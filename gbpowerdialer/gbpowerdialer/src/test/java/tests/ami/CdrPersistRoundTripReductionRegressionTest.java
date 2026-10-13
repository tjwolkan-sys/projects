package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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
 * FIX-GLB90048674 (Faz 3, Scope A) regression: answer yolundaki redundant
 * {@code cdrService.getByInteractionId(...)} SELECT'i çağrı yaşam döngüsü başına EN FAZLA
 * BİR kez çalışmalı ve CDR id in-memory Call üzerinde bilindiği anda hiç çalışmamalıdır.
 *
 * <p>Invariant nötrlüğü: round-trip azaltımı yalnız bir READ'i ortadan kaldırır; durum
 * değişimi yazımları ({@code insertCallResultToDB} / {@code saveOrUpdate}) aynen korunur
 * (kayıpsızlık ve sıra etkilenmez).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class CdrPersistRoundTripReductionRegressionTest {

  private static final String CALL_ID = "dl-roundtrip-reduction-1";

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

  /**
   * GAP senaryosu: CDR id YOK ve {@code getByInteractionId} null döner (DB'de henüz satır yok),
   * ardından save id ataması YAPMAZ (mock no-op / blind upsert). Bu durumda müşteri cevabından
   * SONRA gelen agent cevabı SELECT'i TEKRARLAMAMALI. Eski kodda {@code cdr.getId()==null}
   * olduğu için SELECT iki kez çalışırdı; tek-atış lookup guard'ı ile EN FAZLA bir kez çalışır.
   */
  @Test
  public void shouldQueryPersistedCdrAtMostOnceWhenIdNeverBecomesKnown() {
    Call call = callWithoutPersistedCdr();
    User user = new User();
    user.setUsername("agent1");
    call.setUser(user);
    decorator.getCalls().put(call.getId(), call);
    // getByInteractionId her zaman null: ilk lookup boş döner, save id atamaz (mock).
    when(cdrService.getByInteractionId(call.getId())).thenReturn(null);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent customerAnswer = new ClientPredictiveAnswerEvent(this);
    customerAnswer.setParentId(call.getId());
    customerAnswer.setClientchannel("SIP/customer-1");
    customerAnswer.setDateReceived(new Date(11000L));

    AgentPredictiveAnswerEvent agentAnswer = new AgentPredictiveAnswerEvent(this);
    agentAnswer.setParentId(call.getId());
    agentAnswer.setUsername("agent1");
    agentAnswer.setDateReceived(new Date(12000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", customerAnswer);
    ReflectionTestUtils.invokeMethod(decorator, "handleAgentAnswer", agentAnswer);

    // Round-trip azaltımı: SELECT en fazla bir kez.
    verify(cdrService, times(1)).getByInteractionId(call.getId());
    // Kayıpsızlık: her iki answer da kendi CDR yazımını yapar (customer + agent).
    verify(cdrService, times(2)).saveOrUpdate(any(CDR.class));
    // Kayıpsızlık: customer-answer campaign result yazımı (insertCallResultToDB) korunur.
    verify(dbOperations, times(1)).insertCallResultToDB(call);
  }

  /**
   * CDR id biliniyorsa ({@code call.getCdr().getId() != null}) SELECT HİÇ çalışmamalı.
   * create(VarSet) -> customer-answer -> agent-answer -> hangup boyunca getByInteractionId
   * never; durum değişimi yazımları yine yapılır.
   */
  @Test
  public void shouldNeverQueryPersistedCdrAcrossLifecycleWhenIdAlreadyKnown() {
    Call call = callWithKnownCdrId();
    User user = new User();
    user.setUsername("agent1");
    call.setUser(user);
    call.setCallOriginator(mock(CallOriginator.class));
    call.getCampaign().addCallInProgress(call);
    call.getCampaign().addToOriginatedCalls(call);
    decorator.getCalls().put(call.getId(), call);
    Map<Long, CampaignContext> campaignContexts = campaignContexts(call.getCampaign());
    when(dialerFacade.getCampaignContexts()).thenReturn(campaignContexts);

    ClientPredictiveAnswerEvent customerAnswer = new ClientPredictiveAnswerEvent(this);
    customerAnswer.setParentId(call.getId());
    customerAnswer.setClientchannel("SIP/customer-1");
    customerAnswer.setDateReceived(new Date(11000L));

    AgentPredictiveAnswerEvent agentAnswer = new AgentPredictiveAnswerEvent(this);
    agentAnswer.setParentId(call.getId());
    agentAnswer.setUsername("agent1");
    agentAnswer.setDateReceived(new Date(12000L));

    DialerHangupEvent hangup = new DialerHangupEvent(this);
    hangup.setOriginId(call.getId());
    hangup.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    hangup.setDateReceived(new Date(21000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleCallAnswer", customerAnswer);
    ReflectionTestUtils.invokeMethod(decorator, "handleAgentAnswer", agentAnswer);
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup);

    // id bilindiği için SELECT hiç çalışmamalı (round-trip tamamen elenir).
    verify(cdrService, never()).getByInteractionId(any(String.class));
    // Kayıpsızlık: answer yazımları (customer + agent) + hangup finalize persist'i = 3 CDR yazımı.
    verify(cdrService, times(3)).saveOrUpdate(any(CDR.class));
    // Kayıpsızlık: en az customer-answer'ın campaign result yazımı yapılmış olmalı
    // (hangup finalize dalı ek bir insertCallResultToDB daha yazabilir; sayıya bağlanmıyoruz).
    verify(dbOperations, atLeastOnce()).insertCallResultToDB(call);
  }

  private Call callWithoutPersistedCdr() {
    Campaign campaign = answerCampaign();
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

  private Call callWithKnownCdrId() {
    Campaign campaign = answerCampaign();
    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    // answerDate'i ÖNCEDEN set ETME: handleCallAnswer (customer) answerDate==null iken işler;
    // böylece customer-answer kendi CDR + campaign result yazımını yapar, ardından gelen hangup
    // (answerDate artık dolu) gecikmeli snapshot yolundan saveOrUpdate eder.
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setUser(userWithResultCodes("agent1"));

    CDR cdr = new CDR();
    cdr.setId(9009L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(1L));
    call.setCdr(cdr);
    return call;
  }

  private Campaign answerCampaign() {
    Campaign campaign = new Campaign();
    campaign.setId(77L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);
    return campaign;
  }

  private User userWithResultCodes(String username) {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setUsername(username);
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
    normalClearing = telephonyResultCode(16L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
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
