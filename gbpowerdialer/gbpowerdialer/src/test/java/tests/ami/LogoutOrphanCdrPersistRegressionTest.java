package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.powerdialer.engine.UserLoginLogoutHandler;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.PhoneNumberColumn;
import com.powerdialer.model.ResultCode;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.model.User;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import com.powerdialer.util.Utils;

/**
 * FIX-320173 (B1) regression'ları: agent logout'unda orphan'lanan answered çağrı, kampanya result
 * tablosuna yazılan orphan sonucuyla pd_cdr'a da persist edilir (in-memory user result code
 * korunarak) ve sonradan gelen gerçek DialerHangup idempotent kalır.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class LogoutOrphanCdrPersistRegressionTest {

  private static final String CALL_ID = "dl-logout-orphan-1";

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
  private ACDEngine acdEngine;

  @Mock
  private RedisUtil redisUtil;

  private ManagerConnectionDecorator decorator;
  private UserLoginLogoutHandler logoutHandler;
  private TelephonyResultCode orphanedCallTerminated;
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

    decorator = spy(new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition));
    // Test ortamında AMI bağlantısı yok; forceful hangup gönderimi no-op yapılır.
    doNothing().when(decorator).hangupCall(any(Call.class));
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());

    Map<String, ManagerConnectionDecorator> openedConnections = new HashMap<>();
    openedConnections.put("127.0.0.1", decorator);
    when(connectionComposition.getOpenedConnections()).thenReturn(openedConnections);
    when(connectionComposition.getItuToTelephonyResultCodeRelations()).thenReturn(telephonyResultCodes());

    logoutHandler = new UserLoginLogoutHandler(loggerDecorator);
    ReflectionTestUtils.setField(logoutHandler, "acdEngine", acdEngine);
    ReflectionTestUtils.setField(logoutHandler, "callUpToDateKeeper", callUpToDateKeeper);
    ReflectionTestUtils.setField(logoutHandler, "dialerFacade", dialerFacade);
    ReflectionTestUtils.setField(logoutHandler, "connectionComposition", connectionComposition);
    ReflectionTestUtils.setField(logoutHandler, "campaignDbOperations", dbOperations);
    ReflectionTestUtils.setField(logoutHandler, "callCleaner", callCleaner);
    ReflectionTestUtils.setField(logoutHandler, "redisUtil", redisUtil);
  }

  @Test
  public void shouldPersistOrphanedFinalCdrPreservingUserResultCodeOnLogout() {
    Call call = answeredCallWithUserResultCode();
    Date placeholderEndTime = call.getCdr().getCallEndTime();

    logoutHandler.handleLogout(call.getUser());

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    CDR savedCdr = cdrCaptor.getValue();

    assertThat(savedCdr.getTelephonyRC(), sameInstance(orphanedCallTerminated));
    // Açık Soru #2 kararı: kampanya result tablosuna yazılan in-memory URC, pd_cdr'da da korunur.
    assertThat(savedCdr.getUserResultCodeId(), is(416L));
    assertThat(savedCdr.getCallEndTime().equals(placeholderEndTime), is(false));
    assertThat(call.getCampaign().getCallsInProgressCount(), is(0));
    assertThat(call.getCampaign().getActiveCalls().get(), is(0));
    assertThat(decorator.getCalls().getIfPresent(call.getId()), is((Call) null));
    assertThat(decorator.getOrphanedCalls().getIfPresent(call.getId()), sameInstance(call));
    verify(dbOperations).insertCallResultToDB(call);
    verify(decorator).hangupCall(call);
  }

  @Test
  public void shouldSkipSubsequentHangupAfterOrphanedFinalCdrPersisted() {
    Call call = answeredCallWithUserResultCode();

    logoutHandler.handleLogout(call.getUser());

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(call.getId());
    event.setHangupCause(String.valueOf(Constants.TELEPHONY_RC_NORMAL_CLEARING));
    event.setDateReceived(new Date());
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    // pd_cdr orphan finalize'ında persist edildiği için ikinci hangup sessizce atlanır (idempotent).
    verify(cdrService, times(1)).saveOrUpdate(any(CDR.class));
    verify(callEventPublisher, never()).publishCallEndEvent(anyString(), any(Date.class));
  }

  @Test
  public void shouldPersistOrphanedFinalCdrWithoutUserResultCode() {
    Call call = answeredCallWithUserResultCode();
    call.setUserResultCode(null);
    call.getCdr().setUserResultCodeId(null);

    logoutHandler.handleLogout(call.getUser());

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    assertThat(cdrCaptor.getValue().getTelephonyRC(), sameInstance(orphanedCallTerminated));
    assertThat(cdrCaptor.getValue().getUserResultCodeId(), nullValue());
  }

  /**
   * Agent blind-transferred the call: it left the agent's bridge but still references the agent and
   * lingers in {@code calls} awaiting its own DialerHangup. Logout must not hang it up or clear it,
   * otherwise the live customer/transfer-target conversation would be cut.
   */
  @Test
  public void shouldNotClearTransferredCallOnLogout() {
    Call call = answeredCallWithUserResultCode();
    call.setTransferred(true);

    logoutHandler.handleLogout(call.getUser());

    verify(decorator, never()).hangupCall(call);
    assertThat("transferred call must stay in calls for its own DialerHangup",
        decorator.getCalls().getIfPresent(call.getId()), sameInstance(call));
    assertThat("transferred call must not be orphaned on logout",
        decorator.getOrphanedCalls().getIfPresent(call.getId()), is((Call) null));
    verify(cdrService, never()).saveOrUpdate(any(CDR.class));
    verify(dbOperations, never()).insertCallResultToDB(call);
  }

  private Call answeredCallWithUserResultCode() {
    Campaign campaign = new Campaign();
    campaign.setId(91L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

    User user = new User();
    user.setId(7L);
    user.setUsername("agent1");

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setUser(user);

    ResultCode userResultCode = new ResultCode();
    userResultCode.setId(416L);
    userResultCode.setFinalStatus(true);
    call.setUserResultCode(userResultCode);

    CDR cdr = new CDR();
    cdr.setId(1001L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(1500L));
    cdr.setCallEndTime(new Date(1600L));
    cdr.setCallAnswerTime(new Date(2000L));
    cdr.setCallHandlingStartTime(new Date(2100L));
    cdr.setUserResultCodeId(416L);
    call.setCdr(cdr);

    CallOriginator originator = mock(CallOriginator.class);
    when(originator.getConnectionDecorator()).thenReturn(decorator);
    call.setCallOriginator(originator);

    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    campaign.setActiveCalls(1);
    decorator.getCalls().put(call.getId(), call);
    return call;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    if (orphanedCallTerminated == null) {
      orphanedCallTerminated = telephonyResultCode(81L, Constants.TELEPHONE_RC_ORPHANED_CALL_TERMINATED);
      normalClearing = telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    }
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONE_RC_ORPHANED_CALL_TERMINATED, orphanedCallTerminated);
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
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
