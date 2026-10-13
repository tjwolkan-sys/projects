package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.asteriskjava.manager.event.FullyBootedEvent;
import org.asteriskjava.manager.event.ManagerEvent;
import org.asteriskjava.manager.event.VarSetEvent;
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

/**
 * {@link ManagerConnectionDecorator#onManagerEvent(ManagerEvent)} PUBLIC dispatch
 * karakterizasyon testleri.
 *
 * <p>Mevcut {@code tests/ami} testleri private handler'ları (handleCallCreation,
 * handleCallAnswer, handleAgentAnswer, handleDialerHangup) doğrudan
 * {@link ReflectionTestUtils#invokeMethod} ile çağırır; public dispatch eşlemesinin
 * kendisi (event tipi -> handler) henüz kilitli değildir. Bu test, event worker
 * offload refaktöründen ÖNCE bugünkü SENKRON dispatch davranışını kilitler:
 * her event tipi doğru handler'a route edilmeli ve handler'ın karakteristik yan
 * etkisini üretmelidir. Eşlenmeyen event tipi hiçbir yan etki üretmemelidir.
 *
 * <p>Assertion'lar bilinçli olarak dispatch -> yan etki eşlemesine odaklıdır;
 * her handler'ın iç detayı zaten ilgili handler testlerinde kapsanır.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmiEventDispatchCharacterizationRegressionTest {

  private static final String CALL_ID = "dl-dispatch-char-1";

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
  public void shouldRouteVarSetEventToCallCreation() {
    Call call = baseCall();
    decorator.getCalls().put(call.getId(), call);

    VarSetEvent event = new VarSetEvent(this);
    event.setVariable("ORIGINID");
    event.setValue(CALL_ID);
    event.setChannel("Local/5551000@out-dialer-attempt-0009b39a;1");
    event.setUniqueId("GAVXBSNTRGRP01-1777966771.480652");
    event.setDateReceived(new java.util.Date());

    decorator.onManagerEvent(event);

    assertThat("VarSetEvent handleCallCreation'a route edilmeli (startDate set olur)",
        call.getStartDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(call);
  }

  @Test
  public void shouldRouteClientPredictiveAnswerEventToCallAnswer() {
    Call call = ringingCallAwaitingAnswer();
    decorator.getCalls().put(call.getId(), call);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent event = new ClientPredictiveAnswerEvent(this);
    event.setParentId(CALL_ID);
    event.setClientchannel("SIP/customer-1");
    event.setDateReceived(new java.util.Date(11000L));

    decorator.onManagerEvent(event);

    assertThat("ClientPredictiveAnswerEvent handleCallAnswer'a route edilmeli (answerDate set olur)",
        call.getAnswerDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(call);
    verify(cdrService).saveOrUpdate(any(CDR.class));
  }

  /**
   * FIX-333607: campaign stop edilip context kaldırıldıktan sonra gelen müşteri cevabı NPE ile
   * yarıda kalıyordu ({@code handleCallAnswer:758}, 02.10.2026 10:05'te 121 + gün içinde 7).
   * Context yokken context sayaçları atlanır; CDR answer zamanı, sonuç yazımı ve customer answer
   * event'i işlenmeye devam eder.
   */
  @Test
  public void shouldHandleClientAnswerWhenCampaignContextIsGone() {
    Call call = ringingCallAwaitingAnswer();
    decorator.getCalls().put(call.getId(), call);
    when(dialerFacade.getCampaignContexts()).thenReturn(new HashMap<>());

    ClientPredictiveAnswerEvent event = new ClientPredictiveAnswerEvent(this);
    event.setParentId(CALL_ID);
    event.setClientchannel("SIP/customer-1");
    event.setDateReceived(new java.util.Date(11000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", event);

    assertThat(call.getAnswerDate(), notNullValue());
    verify(dbOperations).insertCallResultToDB(call);
    verify(cdrService).saveOrUpdate(any(CDR.class));
    verify(callEventPublisher).publishCallCustomerAnswerEvent(eq(CALL_ID), any());
  }

  @Test
  public void shouldRouteAgentPredictiveAnswerEventToAgentAnswer() {
    Call call = ringingCallAwaitingAnswer();
    User agent = new User();
    agent.setUsername("agent1");
    call.setUser(agent);
    decorator.getCalls().put(call.getId(), call);
    int inCallsBefore = call.getCampaign().getInCalls().get();

    AgentPredictiveAnswerEvent event = new AgentPredictiveAnswerEvent(this);
    event.setParentId(CALL_ID);
    event.setUsername("agent1");
    event.setDateReceived(new java.util.Date(12000L));

    decorator.onManagerEvent(event);

    assertThat("AgentPredictiveAnswerEvent handleAgentAnswer'a route edilmeli (inCalls 1 artar)",
        call.getCampaign().getInCalls().get(), is(inCallsBefore + 1));
    verify(cdrService).saveOrUpdate(any(CDR.class));
  }

  @Test
  public void shouldRouteDialerHangupEventToDialerHangup() {
    Call call = callWaitingForUserResultCode();
    call.setCallOriginator(mock(CallOriginator.class));
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    call.getCampaign().setActiveCalls(1);
    call.getCampaign().incrementInCallsSize(call);
    decorator.getCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    event.setDateReceived(new java.util.Date());

    decorator.onManagerEvent(event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    assertThat("DialerHangupEvent handleDialerHangup'a route edilmeli (hangup snapshot persist edilir)",
        cdrCaptor.getValue().getTelephonyRC(), notNullValue());
    verify(callEventPublisher).publishCallEndEvent(org.mockito.ArgumentMatchers.eq(call.getId()),
        any(java.util.Date.class));
  }

  @Test
  public void shouldNotTriggerAnyHandlerSideEffectForUnhandledEvent() {
    Call call = ringingCallAwaitingAnswer();
    decorator.getCalls().put(call.getId(), call);

    ManagerEvent unhandled = new FullyBootedEvent(this);

    decorator.onManagerEvent(unhandled);

    verify(dbOperations, never()).insertCallResultToDB(any(Call.class));
    verify(cdrService, never()).saveOrUpdate(any(CDR.class));
  }

  /** Originate edilmiş ama VarSet (RINGING) gelmemiş çağrı: startDate null. */
  private Call baseCall() {
    Campaign campaign = baseCampaign();
    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setResultCodeSequence(0);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));

    CDR cdr = new CDR();
    cdr.setInteractionId(CALL_ID);
    call.setCdr(cdr);
    return call;
  }

  /** RINGING durumunda, henüz cevaplanmamış çağrı: startDate set, answerDate null. */
  private Call ringingCallAwaitingAnswer() {
    Call call = baseCall();
    call.setStartDate(Utils.convertDateToDouble(new java.util.Date(1000L)));
    // CDR id set: prepareCdrForAnswerSave persisted-cdr yolunu izler, getByInteractionId
    // çağrılmaz; handleCallAnswer/handleAgentAnswer yine cdrService.saveOrUpdate'i tetikler.
    call.getCdr().setId(2002L);
    return call;
  }

  /**
   * Hangup'ta user result code beklenen çağrı: handleDialerHangup,
   * persistHangupSnapshotForDelayedResult üzerinden cdrService.saveOrUpdate'i çağırır.
   * (Bkz. SaveInitialCdrHangupResultRegressionTest fixture idiom'u.)
   */
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
    call.setAnswerDate(Utils.convertDateToDouble(new java.util.Date()));
    call.setUser(userWithResultCodes());

    CDR cdr = new CDR();
    cdr.setId(1001L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new java.util.Date(1L));
    cdr.setCallHandlingStartTime(new java.util.Date());
    call.setCdr(cdr);
    return call;
  }

  private Campaign baseCampaign() {
    Campaign campaign = new Campaign();
    campaign.setId(45L);
    campaign.setColResultRcSequence("rc_sequence");
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);
    return campaign;
  }

  private User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(java.util.Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setGroup(group);
    return user;
  }

  private Map<Long, CampaignContext> campaignContexts(Campaign campaign) {
    CampaignContext campaignContext = mock(CampaignContext.class);
    when(campaignContext.getCampaign()).thenReturn(campaign);
    Map<Long, CampaignContext> campaignContexts = new HashMap<>();
    campaignContexts.put(campaign.getId(), campaignContext);
    return campaignContexts;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING,
        telephonyResultCode(16L, Constants.TELEPHONY_RC_NORMAL_CLEARING));
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT,
        telephonyResultCode(911L, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
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
