package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * Task 327734: Awaiting penceresi hiçbir kaynaktan user result code gelmeden dolduğunda
 * başarılı (normal clearing) çağrının "Belirsiz Kayıt" (code 85) telephony result code'una
 * çevrildiğini, normal retry sürecine girdiğini ve agent sahipliğinin korunduğunu doğrular.
 * Webphone kaynağından result code bulunan veya code 85 tanımsız olan durumlarda mevcut
 * davranışın (trc 10) korunması regression olarak kilitlenir.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AwaitingUserResultTimeoutAmbiguousTrcRegressionTest {

  private static final String CALL_ID = "dl-awaiting-timeout-ambiguous-1";
  private static final Long NORMAL_CLEARING_TRC_ID = 10L;
  private static final Long AMBIGUOUS_TRC_ID = 85L;

  @Mock
  private LoggerDecorator loggerDecorator;

  @Mock
  private ManagerConnectionComposition connectionComposition;

  @Mock
  private CDRService cdrService;

  @Mock
  private DialerFacade dialerFacade;

  @Mock
  private CallEventPublisher callEventPublisher;

  @Mock
  private CampaignDbOperations dbOperations;

  @Mock
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;

  @Mock
  private StopConditionChecker stopConditionChecker;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  private ManagerConnectionDecorator decorator;
  private TelephonyResultCode normalClearing;
  private TelephonyResultCode ambiguousRecord;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes(true));
  }

  @Test
  public void shouldFinalizeWithAmbiguousTrcAndKeepAgentOwnershipWhenNoUserResultCodeAnywhere() {
    Call call = callInAwaitingState(false);
    stubNoResultCodeInAnySource(call);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    CDR finalSavedCdr = lastSavedCdr();
    assertThat("URC hiçbir kaynakta yokken telephony result code Belirsiz Kayıt (85) olmalı.",
        finalSavedCdr.getTelephonyRC().getId(), is(AMBIGUOUS_TRC_ID));
    assertThat("Belirsiz Kayıt'a çevrilen çağrıda agent görüşmesi gerçekleştiği için connected user korunmalı.",
        finalSavedCdr.getConnectedUser(), is(call.getUser()));
    assertThat("Belirsiz Kayıt'a çevrilen çağrıda assigned user korunmalı.",
        finalSavedCdr.getAssignedUser(), is(call.getUser()));
    assertThat("User result code boş kalmalı.", finalSavedCdr.getUserResultCodeId(), is(nullValue()));
  }

  @Test
  public void shouldScheduleRetryInsteadOfCompletingWhenNoUserResultCode() {
    Call call = callInAwaitingState(true);
    stubNoResultCodeInAnySource(call);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("Belirsiz Kayıt normal retry sürecine girmeli; retry tarihi kurulmalı.",
        call.getRetryDate(), is(notNullValue()));
    assertThat("Retry kurulan çağrı tamamlanmış sayılmamalı.", call.getCompleteDate(), is(nullValue()));
    assertThat("Retry numarası aranan numara olmalı.", call.getRetryNumber(), is(call.getCalledNumber()));
    assertThat("Telephony result code Belirsiz Kayıt (85) olmalı.",
        call.getTelephonyResultCode().getId(), is(AMBIGUOUS_TRC_ID));
  }

  @Test
  public void shouldKeepSuccessfulTrcWhenWebphoneResultCodeIsHydrated() {
    Call call = callInAwaitingState(false);
    Long webphoneResultCodeId = 259L;

    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdrWithoutUserResultCode(call));
    when(cdrService.findWebphoneUserResultCodeId(any(String.class), any(Long.class))).thenReturn(webphoneResultCodeId);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    CDR finalSavedCdr = lastSavedCdr();
    assertThat("Webphone kaynağında result code varken telephony result code başarılı (10) kalmalı.",
        finalSavedCdr.getTelephonyRC().getId(), is(NORMAL_CLEARING_TRC_ID));
    assertThat("Webphone kaynağındaki user result code CDR'a bağlanmalı.",
        finalSavedCdr.getUserResultCodeId(), is(webphoneResultCodeId));
  }

  @Test
  public void shouldKeepSuccessfulTrcWhenAmbiguousCodeIsNotDefined() {
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes(false));
    Call call = callInAwaitingState(false);
    stubNoResultCodeInAnySource(call);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    CDR finalSavedCdr = lastSavedCdr();
    assertThat("Code 85 pd_telephony_rc'de tanımlı değilse mevcut davranış korunmalı (trc 10).",
        finalSavedCdr.getTelephonyRC().getId(), is(NORMAL_CLEARING_TRC_ID));
  }

  private void stubNoResultCodeInAnySource(Call call) {
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdrWithoutUserResultCode(call));
    when(cdrService.findWebphoneUserResultCodeId(any(String.class), any(Long.class))).thenReturn(null);
  }

  private CDR persistedCdrWithoutUserResultCode(Call call) {
    CDR persistedCdr = new CDR();
    persistedCdr.setId(call.getCdr().getId());
    persistedCdr.setInteractionId(call.getId());
    persistedCdr.setCallHandlingStartTime(call.getCdr().getCallHandlingStartTime());
    persistedCdr.setCallEndTime(call.getCdr().getCallEndTime());
    persistedCdr.setTelephonyRC(call.getCdr().getTelephonyRC());
    persistedCdr.setUserResultCodeId(null);
    return persistedCdr;
  }

  private CDR lastSavedCdr() {
    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    org.mockito.Mockito.verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> savedCdrs = cdrCaptor.getAllValues();
    return savedCdrs.get(savedCdrs.size() - 1);
  }

  private Call callInAwaitingState(boolean retryEnabled) {
    Campaign campaign = new Campaign();
    campaign.setId(72L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultAttempts("attempts");
    if (retryEnabled) {
      campaign.setRetryScheduleEnabled(true);
      campaign.setRetryStrategy(Constants.RETRY_STRATEGY.REACH_RETRY_LIMIT_FOR_SINGLE_NUMBER);
      campaign.setRetryOveralAttempt(3);
      campaign.setCountAllCallTypes(true);
      campaign.setRetryDelay(2D);
    } else {
      campaign.setRetryScheduleEnabled(false);
    }
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    phoneNumberColumn.setEnabled(true);
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnsweredByUser(true);
    call.setUser(userWithResultCodes());
    call.setTelephonyResultCode(normalClearing);
    call.setCallOriginator(mock(CallOriginator.class));
    if (retryEnabled) {
      call.incrementAttempts();
    }

    CDR cdr = new CDR();
    cdr.setId(3004L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(5000L));
    cdr.setCallHandlingStartTime(new Date(2500L));
    cdr.setTelephonyRC(normalClearing);
    call.setCdr(cdr);

    decorator.getAwaitingUserResultCodeCalls().put(call.getId(), call);
    return call;
  }

  private User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(9931L);
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes(boolean includeAmbiguous) {
    normalClearing = telephonyResultCode(NORMAL_CLEARING_TRC_ID, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    if (includeAmbiguous) {
      ambiguousRecord = telephonyResultCode(AMBIGUOUS_TRC_ID, Constants.TELEPHONY_RC_AMBIGUOUS_RECORD);
      resultCodes.put(Constants.TELEPHONY_RC_AMBIGUOUS_RECORD, ambiguousRecord);
    }
    return resultCodes;
  }

  private TelephonyResultCode telephonyResultCode(Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    return telephonyResultCode;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
