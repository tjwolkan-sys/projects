package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.boost.DynamicBoostManager;
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
 * FEAT-325470 Faz 1 karakterizasyonu: hangup yolunda AMD (code 900 / id 71) sonucunun bugun
 * aldigi uc ozel muamele kilitlenir. Faz 3'te bu kontroller id tabanli tek sabitten code tabanli
 * AMD ailesi kumesine ({@code 900, 113, 114, 115}) tasinacak; 900 icin ciktinin birebir ayni
 * kalmasi bu testle korunur.
 *
 * <ul>
 *   <li>Preview kampanyada agent atanmis ve grup result code'lu iken AMD awaiting-URC cache'ine
 *       dusmez, dogrudan finalize olur (user busy ise bekler).</li>
 *   <li>Musteri bacagi cevaplanmis ama agent'a hic baglanmamis cagrida AMD, TRC 84 ("customer
 *       hangup before agent") override'indan muaftir (user busy override edilir).</li>
 *   <li>Predictive ALPHA'da cevaplanmis AMD cagrisi NO_CONNECTION sayilir ve boost manager'a
 *       basarisiz cagri olarak bildirilir.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AmdFamilyHangupCharacterizationTest {

  private static final int AMD_CODE = 900;
  private static final int USER_BUSY_CODE = 17;

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

  @Mock
  private DynamicBoostManager dynamicBoostManager;

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
    decorator.setDynamicBoostManager(dynamicBoostManager);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
  }

  // ---------------------------------------------------------------------
  // (a) Preview + agent + grup result code'lu: awaiting-URC muafiyeti
  // ---------------------------------------------------------------------

  @Test
  public void amdHangupInPreviewCampaignFinalizesWithoutWaitingForUserResultCode() {
    Call call = previewCallWaitingForUserResultCode("dl-amd-preview-900");
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, AMD_CODE));

    verify(dbOperations).insertCallResultToDB(call);
    assertThat(call.isBusinessResultFinalized(), is(true));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()), is(nullValue()));
    assertThat(call.getTelephonyResultCode().getCode(), is(AMD_CODE));
  }

  @Test
  public void userBusyHangupInPreviewCampaignWaitsForUserResultCode() {
    Call call = previewCallWaitingForUserResultCode("dl-busy-preview-17");
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, USER_BUSY_CODE));

    verify(dbOperations, never()).insertCallResultToDB(call);
    assertThat(call.isBusinessResultFinalized(), is(false));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()), is(sameInstance(call)));
  }

  // ---------------------------------------------------------------------
  // (b) Cevaplanmis ama agent'a baglanmamis: TRC 84 override muafiyeti
  // ---------------------------------------------------------------------

  @Test
  public void amdHangupIsNotOverriddenToCustomerHangupBeforeAgentConnection() {
    Call call = answeredCallWithoutAgentHandling("dl-amd-answered-900", Constants.PREDICTIVE_STRATEGY.GAMMA);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, AMD_CODE));

    assertThat(lastPersistedCdr().getTelephonyRC().getCode(), is(AMD_CODE));
    assertThat(call.getTelephonyResultCode().getCode(), is(AMD_CODE));
  }

  @Test
  public void userBusyHangupOnAnsweredCallWithoutAgentIsOverriddenToCustomerHangupBeforeAgent() {
    Call call = answeredCallWithoutAgentHandling("dl-busy-answered-17", Constants.PREDICTIVE_STRATEGY.GAMMA);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, USER_BUSY_CODE));

    assertThat(lastPersistedCdr().getTelephonyRC().getCode(), is(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
  }

  // ---------------------------------------------------------------------
  // (c) Predictive ALPHA: cevaplanmis AMD = NO_CONNECTION
  // ---------------------------------------------------------------------

  @Test
  public void answeredAmdHangupIsReportedAsFailedCallToAlphaBoostManager() {
    Call call = answeredCallWithoutAgentHandling("dl-amd-alpha-900", Constants.PREDICTIVE_STRATEGY.ALPHA);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, AMD_CODE));

    verify(dynamicBoostManager).onCallFailed(call);
  }

  @Test
  public void answeredUserBusyHangupIsNotReportedAsFailedCallToAlphaBoostManager() {
    Call call = answeredCallWithoutAgentHandling("dl-busy-alpha-17", Constants.PREDICTIVE_STRATEGY.ALPHA);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", hangup(call, USER_BUSY_CODE));

    verify(dynamicBoostManager, never()).onCallFailed(call);
  }

  // ---------------------------------------------------------------------
  // Fixture
  // ---------------------------------------------------------------------

  private CDR lastPersistedCdr() {
    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> persisted = cdrCaptor.getAllValues();
    return persisted.get(persisted.size() - 1);
  }

  private static DialerHangupEvent hangup(Call call, int cause) {
    DialerHangupEvent event = new DialerHangupEvent(AmdFamilyHangupCharacterizationTest.class);
    event.setOriginId(call.getId());
    event.setHangupCause(String.valueOf(cause));
    event.setDateReceived(new Date(20000L));
    return event;
  }

  private Call previewCallWaitingForUserResultCode(String callId) {
    Campaign campaign = campaign(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);
    Call call = call(callId, campaign);
    call.setUser(userWithResultCodes());
    return call;
  }

  private Call answeredCallWithoutAgentHandling(String callId, Constants.PREDICTIVE_STRATEGY predictiveStrategy) {
    Campaign campaign = campaign(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(predictiveStrategy);
    Call call = call(callId, campaign);
    call.setAnswerDate(Utils.convertDateToDouble(new Date(15000L)));
    call.getCdr().setCallAnswerTime(new Date(15000L));
    call.getCdr().setCallHandlingStartTime(null);
    return call;
  }

  private static Campaign campaign(Constants.CAMPAIGN_TYPE type) {
    Campaign campaign = new Campaign();
    campaign.setId(85L);
    campaign.setType(type);
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

  private static Call call(String callId, Campaign campaign) {
    Call call = new Call();
    call.setId(callId);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(3003L);
    cdr.setInteractionId(callId);
    cdr.setCallDialTime(new Date(9000L));
    call.setCdr(cdr);

    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    return call;
  }

  private static User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(7L);
    user.setGroup(group);
    return user;
  }

  private static Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING));
    resultCodes.put(USER_BUSY_CODE, telephonyResultCode(11L, USER_BUSY_CODE));
    resultCodes.put(AMD_CODE, telephonyResultCode(71L, AMD_CODE));
    resultCodes.put(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(),
        telephonyResultCode(73L, Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode()));
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT,
        telephonyResultCode((long) Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT));
    return resultCodes;
  }

  private static TelephonyResultCode telephonyResultCode(Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    return telephonyResultCode;
  }

  private static Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }

}
