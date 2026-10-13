package com.powerdialer.engine.dialer;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Filter;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.User;
import com.powerdialer.util.Constants;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CallProducerTest {

  private CallProducer callProducer;
  private Campaign campaign;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacadeMock;

  @Mock
  private CampaignDbOperations campaignDbOperationsMock;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculatorMock;

  @Before
  public void setUp() {
    campaign = new Campaign();
    campaign.setCacheLimit(20);
    campaign.setConcurrentLines(1);
    campaign.setAllowCallback(true);
    campaign.setCallbackToAppointer(false);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setEndListBehaviour(Constants.END_LIST_BEHAVIOUR.CONTINUE);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    campaign.setSkillEnabled(false);
    campaign.setDncEnabled(false);
    campaign.setRetryRatio(100);
    campaign.setCacheLimit(100);
    campaign.setColUniqueKey("unique_key");
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);

    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setId(1L);
    phoneNumberColumn.setColNumber("phone_column");
    phoneNumberColumn.setEnabled(true);
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    CampaignContext campaignContext = new CampaignContext(campaign, dialerFacadeMock, campaignFilterStatsCalculatorMock);
    callProducer = new CallProducer(campaignContext, campaignFilterStatsCalculatorMock);
    when(dialerFacadeMock.getCampaignDbOperations()).thenReturn(campaignDbOperationsMock);

    // Üretimde run() önce çağrılır ve currentFetchInterval'ı DEFAULT_DATA_FETCH_PERIOD'a
    // set eder. Birim test fetchCalls()'u doğrudan çağırdığından bu state kurulmaz; aksi
    // halde adjustFetchInterval, henüz null olan scheduledFuture üzerinde cancel() çağırıp
    // yalnız harness kaynaklı (runtime'da oluşmayan) bir NPE atardı. run() sonrası state'i
    // taklit etmek için currentFetchInterval'ı 5000'e sabitliyoruz.
    try {
      java.lang.reflect.Field field = CallProducer.class.getDeclaredField("currentFetchInterval");
      field.setAccessible(true);
      field.setInt(callProducer, 5000);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void attemptModeFetchesCallbacksWithoutOwnerGate() throws Exception {
    // Req 1 (task 321997): ATTEMPT_COUNT'ta owner aktif-kullanıcı filtresi uygulanmaz; vadesi gelen
    // appointer callback'ler owner offline/işten-çıkmış olsa bile çekilir (usersIds=null geçilir,
    // acdEngine.getCampaignUsers hiç çağrılmaz).
    campaign.setCallbackToAppointer(true);
    campaign.setAppointerResetStrategy(Constants.APPOINTER_RESET_STRATEGY.ATTEMPT_COUNT);
    when(campaignDbOperationsMock.countCallbacksToLoad(eq(campaign), isNull())).thenReturn(5);

    Method m = CallProducer.class.getDeclaredMethod("getCallBacksCount", Campaign.class);
    m.setAccessible(true);
    int count = (int) m.invoke(callProducer, campaign);

    assertThat(count, is(5));
    verify(campaignDbOperationsMock).countCallbacksToLoad(eq(campaign), isNull());
    verify(dialerFacadeMock.getConnectionComposition().getAcdEngine(), never()).getCampaignUsers(any());
  }

  @Test
  public void timerModeAppliesOwnerGate() throws Exception {
    // TIMER/legacy'de owner aktif-kullanıcı filtresi uygulanır: getCampaignUsers çağrılır, usersIds geçilir.
    campaign.setCallbackToAppointer(true);
    campaign.setAppointerReset(false);
    campaign.setAppointerResetStrategy(Constants.APPOINTER_RESET_STRATEGY.TIMER);
    User owner = new User();
    owner.setId(7L);
    ACDEngine acd = dialerFacadeMock.getConnectionComposition().getAcdEngine();
    when(acd.getCampaignUsers(campaign)).thenReturn(java.util.Collections.singletonList(owner));
    when(campaignDbOperationsMock.countCallbacksToLoad(eq(campaign), any())).thenReturn(3);

    Method m = CallProducer.class.getDeclaredMethod("getCallBacksCount", Campaign.class);
    m.setAccessible(true);
    int count = (int) m.invoke(callProducer, campaign);

    assertThat(count, is(3));
    verify(acd).getCampaignUsers(campaign);
  }

  @Test
  public void givenEmptyQueueWithSegmentationFiltersThenFillQueueWithCorrectCallRatios() throws Exception {
    setupSegmentationFilters(60, 40);

    JSONArray callsReturnedByFilter1 = createMockCalls(60, 0);
    when(campaignDbOperationsMock.fetchDataForProducerCallbacks(any(), eq(60), any(), eq(Optional.of(campaign.getFilters().get(0)))))
            .thenReturn(callsReturnedByFilter1);

    callProducer.fetchCalls();

    verify(campaignDbOperationsMock).fetchDataForProducerCallbacks(any(), eq(60), any(), eq(Optional.of(campaign.getFilters().get(0))));
    verify(campaignDbOperationsMock).fetchDataForProducerCallbacks(eq(campaign), eq(40), any(), eq(Optional.of(campaign.getFilters().get(1))));
  }

  @Test
  public void givenEmptyQueueWithSegmentationFiltersAndNotEnoughDataForCallbacksAndRetriesThenCorrectlyDistributeCallsBetweenTypes() throws Exception {
    campaign.setRetryRatio(50);
    setupSegmentationFilters(60, 40);

    JSONArray callsReturnedForCallbacks = createMockCalls(30, 0);
    JSONArray callsReturnedForRetries = createMockCalls(15, 1000);
    JSONArray callsReturnedForFreshCalls = createMockCalls(15, 10000);
    when(campaignDbOperationsMock.fetchDataForProducerCallbacks(any(), eq(60), any(), eq(Optional.of(campaign.getFilters().get(0)))))
            .thenReturn(callsReturnedForCallbacks);
    when(campaignDbOperationsMock.fetchDataForProducerRetries(any(), eq(15), eq(Optional.of(campaign.getFilters().get(0)))))
            .thenReturn(callsReturnedForRetries);
    when(campaignDbOperationsMock.fetchDataForProducerFresh(any(), eq(15), eq(Optional.of(campaign.getFilters().get(0)))))
            .thenReturn(callsReturnedForFreshCalls);

    callProducer.fetchCalls();

    verify(campaignDbOperationsMock).fetchDataForProducerCallbacks(any(), eq(60), any(), eq(Optional.of(campaign.getFilters().get(0))));
    verify(campaignDbOperationsMock).fetchDataForProducerRetries(any(), eq(15), eq(Optional.of(campaign.getFilters().get(0))));
    verify(campaignDbOperationsMock).fetchDataForProducerFresh(any(), eq(15), eq(Optional.of(campaign.getFilters().get(0))));
    verify(campaignDbOperationsMock).fetchDataForProducerCallbacks(eq(campaign), eq(40), any(), eq(Optional.of(campaign.getFilters().get(1))));
  }

  @Test
  public void givenFreshFetchPathWithPreviousAttemptsThenCdrOutdialingTypeIsScheduledRetry() throws Exception {
    campaign.setAllowCallback(false);
    campaign.setRetryDelay(5D);
    campaign.setColResultAttempts("attempts");

    JSONObject rawCall = createMockCall(12028);
    rawCall.put("attempts", 2);
    when(campaignDbOperationsMock.fetchDataForProducerFresh(any(), eq(100), eq(Optional.empty())))
            .thenReturn(new JSONArray().put(rawCall));

    callProducer.fetchCalls();

    Call call = callProducer.getDataQueue().poll();
    assertThat(call.getCallType(), is(Constants.CALL_TYPES.FRESH));
    assertThat(call.getCdr().getCdrOutdialingType(), is(Constants.CDR_OUTDIALING_TYPE.SCHEDULED_RETRY));
  }

  @Test
  public void givenFreshFetchPathWithoutPreviousDialEvidenceThenCdrOutdialingTypeIsNew() throws Exception {
    campaign.setAllowCallback(false);
    campaign.setRetryDelay(5D);
    campaign.setColResultAttempts("attempts");

    when(campaignDbOperationsMock.fetchDataForProducerFresh(any(), eq(100), eq(Optional.empty())))
            .thenReturn(new JSONArray().put(createMockCall(12029)));

    callProducer.fetchCalls();

    Call call = callProducer.getDataQueue().poll();
    assertThat(call.getCallType(), is(Constants.CALL_TYPES.FRESH));
    assertThat(call.getCdr().getCdrOutdialingType(), is(Constants.CDR_OUTDIALING_TYPE.NEW));
  }

  @Test
  public void givenCallbackFetchPathThenCdrOutdialingTypeStaysCallback() throws Exception {
    campaign.setAllowCallback(true);
    campaign.setColResultCallbackDate("callback_date");
    campaign.setColResultAttempts("attempts");

    JSONObject rawCall = createMockCall(12030);
    rawCall.put("callback_date", 45678D);
    rawCall.put("attempts", 2);
    when(campaignDbOperationsMock.fetchDataForProducerCallbacks(any(), eq(100), any(), eq(Optional.empty())))
            .thenReturn(new JSONArray().put(rawCall));

    callProducer.fetchCalls();

    Call call = callProducer.getDataQueue().poll();
    assertThat(call.getCallType(), is(Constants.CALL_TYPES.CALLBACK));
    assertThat(call.getCdr().getCdrOutdialingType(), is(Constants.CDR_OUTDIALING_TYPE.CALLBACK));
  }

  @Test
  public void givenSetFetchedFlagFailsMidCycleThenUniqueKeysDoNotLeakToNextCycle() throws Exception {
    campaign.setAllowCallback(false);

    JSONArray freshRows = new JSONArray()
            .put(createMockCall(5001))
            .put(createMockCall(5002));
    when(campaignDbOperationsMock.fetchDataForProducerFresh(any(), eq(100), eq(Optional.empty())))
            .thenReturn(freshRows);

    // Campaign DB (LSININTURKCELL:1433) drops mid-cycle: the fetched-flag UPDATE
    // fails on the first fetch cycle so rows keep fetched_flag = NULL and are
    // re-selected next cycle; the connection then recovers.
    doThrow(new SQLException("The TCP/IP connection to the host LSININTURKCELL, port 1433 has failed."))
            .doReturn(java.util.Collections.emptySet())
            .when(campaignDbOperationsMock).reserveRowsForFetch(any(), anyList());

    // Cycle 1: UPDATE throws -> nothing reaches the cache, dedup keys get registered.
    callProducer.fetchCalls();
    assertThat(callProducer.getDataQueue().size(), is(0));

    // Cycle 2: the same rows return (flag never persisted) and the DB is healthy.
    // Leaked keys from cycle 1 must NOT eliminate the rows as "already created";
    // the producer must re-create them and refill the cache.
    callProducer.fetchCalls();
    assertThat(callProducer.getDataQueue().size(), is(2));
  }

  @Test
  public void givenSetFetchedFlagFailsMidCycleThenAlreadyFlaggedCallsAreNotLost() throws Exception {
    // Multi-batch tick: callbacks batch'inin fetched-flag UPDATE'i başarılı, fresh batch'inki
    // patlıyor. Buggy davranış: callbacks DB'de fetched_flag=1 işaretlenir ama fresh fail
    // processAndQueueFetchedCalls'a (dataQueue.addAll) ulaşılmasını engellediği için callbacks
    // cache'e HİÇ girmez -> "DB'de fetched ama cache'de yok" = o satırlar bir daha asla
    // SELECT edilmez (WHERE fetched_flag IS NULL) ve aranmaz = kalıcı kayıp.
    campaign.setAllowCallback(true);
    campaign.setRetryRatio(0);

    JSONArray callbackRows = new JSONArray().put(createMockCall(7001)).put(createMockCall(7002));
    JSONArray freshRows = new JSONArray().put(createMockCall(8001)).put(createMockCall(8002));
    when(campaignDbOperationsMock.fetchDataForProducerCallbacks(any(), anyInt(), any(), eq(Optional.empty())))
            .thenReturn(callbackRows);
    when(campaignDbOperationsMock.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
            .thenReturn(freshRows);

    // Başarıyla fetched-flag set edilen (consume taahhüdü verilen) key'leri izle; fresh batch'i
    // (key "8..." ile başlayan) içeren UPDATE çağrısı DB hatası atar (flag yazılmadan).
    Set<String> flaggedKeys = new HashSet<>();
    doAnswer(invocation -> {
      List<Call> flagged = invocation.getArgument(1);
      boolean containsFresh = flagged.stream()
              .anyMatch(c -> c.getUniqueKey() != null && c.getUniqueKey().startsWith("8"));
      if (containsFresh) {
        throw new SQLException("The TCP/IP connection to the host LSININTURKCELL, port 1433 has failed.");
      }
      flagged.forEach(c -> flaggedKeys.add(c.getUniqueKey()));
      return java.util.Collections.emptySet();
    }).when(campaignDbOperationsMock).reserveRowsForFetch(any(), anyList());

    callProducer.fetchCalls();

    // Invariant: fetched_flag set edilen her call cache'e de ulaşmalı. Aksi halde o satırlar
    // DB'de fetched=1 ama cache'de yok -> kalıcı kayıp.
    Set<String> cachedKeys = callProducer.getDataQueue().stream()
            .map(Call::getUniqueKey).collect(Collectors.toSet());
    assertThat("flag-set edilen call'lar cache'e de ulaşmalı (mid-cycle DB hatasında kayıp olmamalı)",
            cachedKeys.containsAll(flaggedKeys), is(true));
  }

  private Call makeCall(Constants.CALL_TYPES type) {
    Call call = new Call();
    call.setCallType(type);
    call.setId(UUID.randomUUID().toString());
    return call;
  }

  private void setupSegmentationFilters(int filter1Ratio, int filter2Ratio) {
    List<Filter> filterList = new ArrayList<>();
    filterList.add(makeSegmentationFilter(1L, filter1Ratio));
    filterList.add(makeSegmentationFilter(2L, filter2Ratio));
    campaign.setFilters(filterList);
  }

  private JSONArray createMockCalls(int count, int offset) {
    JSONArray calls = new JSONArray();
    for (int i = 0; i < count; i++) {
      calls.put(createMockCall(offset + i));
    }
    return calls;
  }

  private JSONObject createMockCall(int uniqueKeyValue) {
    JSONObject uniqueKey = new JSONObject();
    uniqueKey.put("unique_key", uniqueKeyValue);
    uniqueKey.put("phone_column", String.valueOf(uniqueKeyValue));
    return uniqueKey;
  }

  private Filter makeSegmentationFilter(long id, int ratio) {
    Filter filter = new Filter();
    filter.setId(id);
    filter.setEnabled(true);
    filter.setSegmentation(true);
    filter.setRatio(ratio);
    return filter;
  }

}
