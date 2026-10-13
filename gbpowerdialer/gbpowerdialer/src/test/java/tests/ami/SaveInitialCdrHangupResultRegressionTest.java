package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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
import com.powerdialer.engine.asterisk.events.AgentPredictiveAnswerEvent;
import com.powerdialer.engine.asterisk.events.ClientPredictiveAnswerEvent;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignContext;
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

@RunWith(MockitoJUnitRunner.class)
public class SaveInitialCdrHangupResultRegressionTest {

  private static final String CALL_ID = "dl-save-initial-1";

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

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
  }

  @Test
  public void shouldPersistAmiHangupSnapshotWhenCallWaitsForUserResultCode() {
    Call call = callWaitingForUserResultCode();
    Date initialEndTime = call.getCdr().getCallEndTime();
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    call.getCampaign().setActiveCalls(1);
    call.getCampaign().incrementInCallsSize(call);
    decorator.getCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    event.setDateReceived(new Date());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getId(), is(1001L));
    assertThat(savedCdr.getTelephonyRC(), sameInstance(normalClearing));
    assertThat(savedCdr.getCallEndTime(), is(event.getDateReceived()));
    assertThat(savedCdr.getCallEndTime().equals(initialEndTime), is(false));
    assertThat(call.getCampaign().getCallsInProgressCount(), is(0));
    assertThat(call.getCampaign().hasCallsInProgress(), is(false));
    assertThat(call.getCampaign().getActiveCalls().get(), is(0));
    assertThat(decorator.getCalls().getIfPresent(call.getId()), is((Call) null));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()), sameInstance(call));
    verify(originator).clearCall(call);
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  @Test
  public void shouldPersistHangupSnapshotForRecoverableConnectionLossCall() {
    Call call = callWaitingForUserResultCode();
    Date initialEndTime = call.getCdr().getCallEndTime();
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    call.getCampaign().setActiveCalls(1);
    call.getCampaign().incrementInCallsSize(call);
    decorator.getRecoverableConnectionLossCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    event.setDateReceived(new Date());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getId(), is(1001L));
    assertThat(savedCdr.getTelephonyRC(), sameInstance(normalClearing));
    assertThat(savedCdr.getCallEndTime(), is(event.getDateReceived()));
    assertThat(savedCdr.getCallEndTime().equals(initialEndTime), is(false));
    assertThat(call.getCampaign().getCallsInProgressCount(), is(0));
    assertThat(call.getCampaign().hasCallsInProgress(), is(false));
    assertThat(call.getCampaign().getActiveCalls().get(), is(0));
    assertThat(decorator.getCalls().getIfPresent(call.getId()), is((Call) null));
    assertThat(decorator.getRecoverableConnectionLossCalls().getIfPresent(call.getId()), is((Call) null));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()), sameInstance(call));
    verify(originator).clearCall(call);
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  @Test
  public void shouldFinalizeAwaitingUserResultCallWithoutSecondTelephonyDecrement() {
    Call call = callWaitingForUserResultCode();
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);
    call.getCampaign().setId(99L);
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    call.getCampaign().setActiveCalls(1);
    call.getCampaign().incrementInCallsSize(call);
    decorator.getCalls().put(call.getId(), call);
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    event.setDateReceived(new Date(17000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    ResultCode userResultCode = new ResultCode();
    userResultCode.setId(700L);
    userResultCode.setFinalStatus(true);
    call.setUserResultCode(userResultCode);
    decorator.processCallResult(call, new Date(18000L));
    decorator.processCallResult(call, new Date(19000L));

    assertThat(call.getCampaign().getCallsInProgressCount(), is(0));
    assertThat(call.getCampaign().hasCallsInProgress(), is(false));
    assertThat(call.getCampaign().getActiveCalls().get(), is(0));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()), is((Call) null));
    verify(originator, times(2)).clearCall(call);
    verify(dbOperations, times(1)).insertCallResultToDB(call);
  }

  @Test
  public void shouldCreateAnswerCdrSnapshotWhenInitialCdrWasNotPersisted() {
    Call call = callWithoutPersistedCdr();
    Date answerDate = new Date(10000L);
    decorator.getCalls().put(call.getId(), call);
    when(cdrService.getByInteractionId(call.getId())).thenReturn(null);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent event = new ClientPredictiveAnswerEvent(this);
    event.setParentId(call.getId());
    event.setClientchannel("SIP/customer-1");
    event.setDateReceived(answerDate);

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getInteractionId(), is(call.getId()));
    assertThat(savedCdr.getCallAnswerTime(), is(answerDate));
    assertThat(savedCdr.getCallEndTime(), is(call.getCdr().getCallDialTime()));
    assertThat(savedCdr.getServer().getIp(), is("127.0.0.1"));
    assertThat(savedCdr.getContactNumberColumn(), is("phone1"));
    assertThat(savedCdr.getContactNumberOrder(), is(0));
  }

  @Test
  public void shouldUpdateExistingCdrByInteractionIdWhenAnswerCdrHasNoId() {
    Call call = callWithoutPersistedCdr();
    Date initialEndTime = new Date(5000L);
    CDR existingCdr = new CDR();
    existingCdr.setId(3003L);
    existingCdr.setInteractionId(call.getId());
    existingCdr.setCallEndTime(initialEndTime);
    decorator.getCalls().put(call.getId(), call);
    when(cdrService.getByInteractionId(call.getId())).thenReturn(existingCdr);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent event = new ClientPredictiveAnswerEvent(this);
    event.setParentId(call.getId());
    event.setClientchannel("SIP/customer-1");
    event.setDateReceived(new Date(11000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr, sameInstance(existingCdr));
    assertThat(call.getCdr(), sameInstance(existingCdr));
    assertThat(savedCdr.getCallEndTime(), is(initialEndTime));
    assertThat(savedCdr.getCallAnswerTime(), is(event.getDateReceived()));
  }

  @Test
  public void shouldCreateAgentAnswerCdrSnapshotWhenInitialCdrWasNotPersisted() {
    Call call = callWithoutPersistedCdr();
    User user = new User();
    user.setUsername("agent1");
    call.setUser(user);
    Date answerDate = new Date(12000L);
    decorator.getCalls().put(call.getId(), call);
    when(cdrService.getByInteractionId(call.getId())).thenReturn(null);

    AgentPredictiveAnswerEvent event = new AgentPredictiveAnswerEvent(this);
    event.setParentId(call.getId());
    event.setUsername("agent1");
    event.setDateReceived(answerDate);

    ReflectionTestUtils.invokeMethod(decorator, "handleAgentAnswer", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getInteractionId(), is(call.getId()));
    assertThat(savedCdr.getCallHandlingStartTime(), is(answerDate));
    assertThat(savedCdr.getCallEndTime(), is(call.getCdr().getCallDialTime()));
    assertThat(savedCdr.getContactNumberColumn(), is("phone1"));
  }

  @Test
  public void shouldKeepInitialEndTimeWhenPersistedCdrReceivesCustomerAnswer() {
    Call call = callWithoutPersistedCdr();
    Date initialEndTime = new Date(7000L);
    call.getCdr().setId(4004L);
    call.getCdr().setCallEndTime(initialEndTime);
    decorator.getCalls().put(call.getId(), call);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent event = new ClientPredictiveAnswerEvent(this);
    event.setParentId(call.getId());
    event.setClientchannel("SIP/customer-1");
    event.setDateReceived(new Date(13000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    verify(cdrService, never()).getByInteractionId(call.getId());
    assertThat(cdrCaptor.getValue(), sameInstance(call.getCdr()));
    assertThat(cdrCaptor.getValue().getCallEndTime(), is(initialEndTime));
    assertThat(cdrCaptor.getValue().getCallAnswerTime(), is(event.getDateReceived()));
  }

  @Test
  public void shouldRollbackFinalizeAndRetryWhenFinalCdrSaveFails() {
    Call call = completedCall();
    Campaign campaign = call.getCampaign();
    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    decorator.getCalls().put(call.getId(), call);
    doThrow(new RuntimeException("cdr failure")).doNothing().when(cdrService).saveOrUpdate(any(CDR.class));

    decorator.processCallResult(call, new Date(15000L));

    // FIX-320173 persist-first: persist başarısızsa finalize geri alınır, yan etkiler koşmaz,
    // çağrı cache'te kalır ve sonraki deneme (2. hangup bacağı / expiry) finalize'ı tamamlar.
    assertThat(call.isBusinessResultFinalized(), is(false));
    assertThat(campaign.getCallsInProgressCount(), is(1));
    assertThat(decorator.getCalls().getIfPresent(call.getId()), sameInstance(call));
    verify(dbOperations, never()).insertCallResultToDB(call);

    decorator.processCallResult(call, new Date(16000L));

    assertThat(call.isBusinessResultFinalized(), is(true));
    assertThat(campaign.getCallsInProgressCount(), is(0));
    assertThat(campaign.hasCallsInProgress(), is(false));
    verify(cdrService, times(2)).saveOrUpdate(any(CDR.class));
    verify(dbOperations).insertCallResultToDB(call);
  }

  @Test
  public void shouldFinalizeRecoverableConnectionLossCallWithAmiLossWhenTimeoutExpires() {
    Call call = recoverableConnectionLossCall();
    Campaign campaign = call.getCampaign();
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);
    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    campaign.setActiveCalls(1);
    campaign.incrementInCallsSize(call);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeRecoverableConnectionLossCall", call);

    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
    assertThat(campaign.getCallsInProgressCount(), is(0));
    assertThat(campaign.hasCallsInProgress(), is(false));
    assertThat(campaign.getActiveCalls().get(), is(0));
    verify(callCleaner).completeCall(eq(call), any(Date.class));
    verify(dbOperations).insertCallResultToDB(call);
    verify(originator).saveCDR(eq(call), any(Date.class));
  }

  @Test
  public void shouldNotCountAttemptsForOriginateActionErrorResult() {
    Call call = nonAttemptResultCall(telephonyResultCode(79L, Constants.TELEPHONY_RC_ORIGINATE_ACTION_ERROR), 0);
    call.snapshotAttemptsBeforeOrigination();
    call.incrementAttempts();
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    decorator.processCallResult(call, new Date(15000L));

    assertThat(call.getAttempts(), is(0));
    verify(dbOperations).insertCallResultToDB(call);
  }

  @Test
  public void shouldCountAttemptsForAmiConnectionLossRecoverableTimeout() {
    Call call = recoverableConnectionLossCall();
    call.getCampaign().setColResultAttempts("attempts");
    call.setRawData(call.getRawData().put("attempts", 2));
    call.snapshotAttemptsBeforeOrigination();
    call.incrementAttempts();
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeRecoverableConnectionLossCall", call);

    assertThat(call.getTelephonyResultCode().getCode(), is(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
    assertThat(call.getAttempts(), is(3));
    verify(dbOperations).insertCallResultToDB(call);
    verify(originator).saveCDR(eq(call), any(Date.class));
  }

  private Call callWaitingForUserResultCode() {
    Campaign campaign = new Campaign();
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setAnswerDate(Utils.convertDateToDouble(new Date()));
    call.setUser(userWithResultCodes());

    CDR cdr = new CDR();
    cdr.setId(1001L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(1L));
    cdr.setCallHandlingStartTime(new Date());
    call.setCdr(cdr);
    return call;
  }

  private Call callWithoutPersistedCdr() {
    Campaign campaign = new Campaign();
    campaign.setId(77L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);

    CDR cdr = new CDR();
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(9000L));
    call.setCdr(cdr);
    return call;
  }

  private Call completedCall() {
    Campaign campaign = new Campaign();
    campaign.setId(78L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId("dl-final-cleanup-1");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setTelephonyResultCode(normalClearing);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(5005L);
    cdr.setInteractionId(call.getId());
    cdr.setCallDialTime(new Date(14000L));
    cdr.setCallEndTime(new Date(14000L));
    call.setCdr(cdr);
    return call;
  }

  private Call nonAttemptResultCall(TelephonyResultCode telephonyResultCode, int attempts) {
    Campaign campaign = new Campaign();
    campaign.setId(80L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultAttempts("attempts");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    Call call = new Call();
    call.setId("dl-non-attempt-result-1");
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject()
        .put("phone1", "5551000")
        .put("attempts", attempts)
        .put("retry_history", ""));
    call.setResultCodeSequence(0);
    call.setTelephonyResultCode(telephonyResultCode);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(6006L);
    cdr.setInteractionId(call.getId());
    cdr.setCallDialTime(new Date(14000L));
    cdr.setCallEndTime(new Date(14000L));
    call.setCdr(cdr);
    return call;
  }

  private Call recoverableConnectionLossCall() {
    Call call = completedCall();
    call.setId("dl-recoverable-timeout-1");
    call.setTelephonyResultCode(null);
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    User user = new User();
    user.setId(44L);
    call.setUser(user);
    call.setAnsweredByUser(true);
    call.getCdr().setInteractionId(call.getId());
    call.getCdr().setCallHandlingStartTime(new Date(2500L));
    return call;
  }

  private Map<Long, CampaignContext> campaignContexts(Campaign campaign) {
    CampaignContext campaignContext = mock(CampaignContext.class);
    when(campaignContext.getCampaign()).thenReturn(campaign);
    Map<Long, CampaignContext> campaignContexts = new HashMap<>();
    campaignContexts.put(campaign.getId(), campaignContext);
    return campaignContexts;
  }

  private User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = telephonyResultCode(16L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT,
        telephonyResultCode(911L, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
    resultCodes.put(Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode(),
        telephonyResultCode(75L, Constants.ERROR_RESULT_CODES.AMI_CONNECTION_LOSS.getCode()));
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
