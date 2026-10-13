package tests.dialer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.CampaignFilterStatsCalculator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.util.Constants;

/**
 * FIX-330538 — Aynı kaydın cache'e iki kez girip iki kez aranması.
 *
 * <p>Vaka (17.09.2026, KFK_TAHSILAT): kampanya start'ının ilk iki fetch tick'i (5 sn arayla)
 * aynı 6 id'yi döndürdü; ilk batch o anda consumer callQueue'sunda bekliyordu. Producer'ın
 * tek dedupe kaynağı DB'deki {@code is_fetched} rezervasyonu ve tick içi {@code callsUniqueKeys}
 * seti olduğu için ikinci tick aynı satırlar için yeni {@code Call} nesneleri üretti; altı kayıt
 * da iki kez arandı (ikincisi satır kapandıktan sonra). Liste tarafında iki çağrı da
 * {@code ATTEMPTS='1'} yazdığı için duplicate yalnız pd_cdr'de görünüyordu.</p>
 *
 * <p>Beklenen: producer, fetch anında {@code dataQueue} ve consumer {@code callQueue}'sunda
 * canlı bir {@code Call} ile temsil edilen uniqueKey için yeni Call üretmez ve bu satırlar için
 * rezervasyon UPDATE'i göndermez. Guard runtime state'ten türetilir (add/remove'lu ayrı set yok);
 * kayıt iki kuyruğu da terk ettiğinde (reload drain, normal retry) yeniden fetch edilebilir kalır.</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CallProducerDuplicateFetchGuardRegressionTest {

  private static final int ROW_A = 310493;
  private static final int ROW_B = 310195;
  private static final int ROW_C = 310442;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DialerFacade dialerFacade;

  @Mock
  private CampaignDbOperations campaignDbOperations;

  @Mock
  private CampaignFilterStatsCalculator campaignFilterStatsCalculator;

  private Campaign campaign;
  private CampaignContext campaignContext;
  private CallProducer callProducer;

  @Before
  public void setUp() throws Exception {
    campaign = new Campaign();
    campaign.setCacheLimit(100);
    campaign.setConcurrentLines(10);
    campaign.setAllowCallback(false);
    campaign.setCallbackToAppointer(false);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setEndListBehaviour(Constants.END_LIST_BEHAVIOUR.CONTINUE);
    campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
    campaign.setSkillEnabled(false);
    campaign.setDncEnabled(false);
    campaign.setRetryRatio(0);
    campaign.setColUniqueKey("unique_key");
    campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_BEFORE_NEXT_NUMBER);

    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setId(1L);
    phoneNumberColumn.setColNumber("phone_column");
    phoneNumberColumn.setEnabled(true);
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    campaignContext = new CampaignContext(campaign, dialerFacade, campaignFilterStatsCalculator);
    callProducer = new CallProducer(campaignContext, campaignFilterStatsCalculator);
    when(dialerFacade.getCampaignDbOperations()).thenReturn(campaignDbOperations);

    // Üretimde run() currentFetchInterval'ı kurar; fetchCalls() doğrudan çağrıldığı için
    // CallProducerTest ile aynı şekilde run() sonrası state taklit edilir.
    Field field = CallProducer.class.getDeclaredField("currentFetchInterval");
    field.setAccessible(true);
    field.setInt(callProducer, 5000);
  }

  /**
   * Vakanın birebir karşılığı: ilk batch consumer callQueue'sunda beklerken DB aynı satırları
   * yeniden döndürür (rezervasyon kaçmış). İkinci tick yeni Call üretmemeli ve bu satırlar için
   * rezervasyon UPDATE'i göndermemeli.
   */
  @Test
  public void sameRowsReturnedWhileFirstBatchWaitsInCallQueueAreNotRecreated() throws Exception {
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A, ROW_B));

    tick();
    assertThat(callProducer.getDataQueue().size(), is(2));
    moveCacheToCallQueue();

    tick();

    assertThat("callQueue'da canlı kopyası olan satır yeniden Call'a dönüşmemeli",
        callProducer.getDataQueue().size(), is(0));
    assertThat(consumerCallQueue().size(), is(2));
    verify(campaignDbOperations, times(1)).reserveRowsForFetch(any(), any());
  }

  /**
   * Aynı senaryo, batch henüz consumer tarafından çekilmemiş ve cache'te duruyorken.
   */
  @Test
  public void sameRowsReturnedWhileFirstBatchStillInCacheAreNotRecreated() throws Exception {
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A, ROW_B));

    tick();
    tick();

    assertThat("cache'te canlı kopyası olan satır yeniden Call'a dönüşmemeli",
        uniqueKeysIn(callProducer.getDataQueue()).size(), is(2));
    assertThat(callProducer.getDataQueue().size(), is(2));
    verify(campaignDbOperations, times(1)).reserveRowsForFetch(any(), any());
  }

  /**
   * Karışık batch: canlı olan satır atlanır, yeni satır normal akışla üretilir ve yalnız o
   * satır için rezervasyon UPDATE'i gönderilir.
   */
  @Test
  public void mixedBatchOnlyCreatesRowsThatAreNotAlreadyLive() throws Exception {
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A, ROW_B))
        .thenReturn(rows(ROW_A, ROW_C));

    tick();
    moveCacheToCallQueue();
    tick();

    assertThat(uniqueKeysIn(callProducer.getDataQueue()), is(keys(ROW_C)));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Call>> flagged = ArgumentCaptor.forClass(List.class);
    verify(campaignDbOperations, times(2)).reserveRowsForFetch(any(), flagged.capture());
    assertThat("ikinci tick yalnız yeni satırı rezerve etmeli",
        uniqueKeysIn(flagged.getAllValues().get(1)), is(keys(ROW_C)));
  }

  /**
   * Regresyon kilidi (reload / stop / normal retry): kayıt iki kuyruğu da terk ettiğinde
   * guard onu tutmamalı; DB yeniden döndürüyorsa yeniden Call üretilmeli.
   */
  @Test
  public void rowsBecomeFetchableAgainOnceTheyLeaveCacheAndCallQueue() throws Exception {
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A, ROW_B));

    tick();
    moveCacheToCallQueue();
    consumerCallQueue().clear();

    tick();

    assertThat("kuyrukları terk eden satır yeniden fetch edilebilmeli",
        uniqueKeysIn(callProducer.getDataQueue()), is(keys(ROW_A, ROW_B)));
    verify(campaignDbOperations, times(2)).reserveRowsForFetch(any(), any());
  }

  /**
   * FIX-333599 — Vakanın birebir karşılığı (02.10.2026, contact 326361): ilk Call originate
   * edilip kuyrukları terk etmiş, çağrı sürüyor; rezervasyonu silinen satır DB'den yeniden
   * dönüyor. Görüşmedeki satır için yeni Call üretilmemeli ve rezervasyon UPDATE'i gönderilmemeli.
   */
  @Test
  public void rowWhoseCallIsInFlightOnAConnectionIsNotRecreated() throws Exception {
    campaign.setId(6L);
    Set<String> inFlight = new java.util.HashSet<>(keys(ROW_A));
    connectionsWithInFlightKeys(inFlight);
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A, ROW_B));

    tick();

    assertThat("görüşmedeki satır yeniden Call'a dönüşmemeli",
        uniqueKeysIn(callProducer.getDataQueue()), is(keys(ROW_B)));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Call>> flagged = ArgumentCaptor.forClass(List.class);
    verify(campaignDbOperations, times(1)).reserveRowsForFetch(any(), flagged.capture());
    assertThat(uniqueKeysIn(flagged.getValue()), is(keys(ROW_B)));
  }

  /**
   * Regresyon kilidi: çağrı finalize edilip bağlantı cache'lerinden çıktığında (callback/retry
   * vadesi, yeniden aktivasyon) satır guard'a takılmadan yeniden fetch edilebilmeli.
   */
  @Test
  public void rowBecomesFetchableAgainOnceInFlightCallIsFinalized() throws Exception {
    campaign.setId(6L);
    Set<String> inFlight = new java.util.HashSet<>(keys(ROW_A));
    connectionsWithInFlightKeys(inFlight);
    when(campaignDbOperations.fetchDataForProducerFresh(any(), anyInt(), eq(Optional.empty())))
        .thenReturn(rows(ROW_A));

    tick();
    assertThat(callProducer.getDataQueue().size(), is(0));

    inFlight.clear();
    tick();

    assertThat(uniqueKeysIn(callProducer.getDataQueue()), is(keys(ROW_A)));
    verify(campaignDbOperations, times(1)).reserveRowsForFetch(any(), any());
  }

  private void connectionsWithInFlightKeys(Set<String> inFlight) {
    ManagerConnectionDecorator connection = mock(ManagerConnectionDecorator.class);
    doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Set<String> target = (Set<String>) invocation.getArgument(1);
      target.addAll(inFlight);
      return null;
    }).when(connection).collectInFlightUniqueKeys(eq(6L), any());
    when(dialerFacade.getConnectionComposition().getOpenedConnections())
        .thenReturn(Collections.singletonMap("dialer-server-1", connection));
  }

  private void tick() throws Exception {
    Method fetchCalls = CallProducer.class.getDeclaredMethod("fetchCalls");
    fetchCalls.setAccessible(true);
    fetchCalls.invoke(callProducer);
  }

  private void moveCacheToCallQueue() {
    Call call;
    while ((call = callProducer.getDataQueue().poll()) != null) {
      consumerCallQueue().offerLast(call);
    }
  }

  private BlockingDeque<Call> consumerCallQueue() {
    return campaignContext.getCallConsumer().getCallQueue();
  }

  private static Set<String> uniqueKeysIn(Iterable<Call> calls) {
    return java.util.stream.StreamSupport.stream(calls.spliterator(), false)
        .map(Call::getUniqueKey)
        .collect(Collectors.toSet());
  }

  private static Set<String> keys(int... uniqueKeys) {
    return java.util.Arrays.stream(uniqueKeys).mapToObj(String::valueOf).collect(Collectors.toSet());
  }

  private static JSONArray rows(int... uniqueKeys) {
    JSONArray rows = new JSONArray();
    for (int uniqueKey : uniqueKeys) {
      JSONObject row = new JSONObject();
      row.put("unique_key", uniqueKey);
      row.put("phone_column", String.valueOf(uniqueKey));
      rows.put(row);
    }
    return rows;
  }
}
