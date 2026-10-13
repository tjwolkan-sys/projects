package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
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
 * Awaiting user result code cache transition davranışını kilitler.
 * AMI hangup else-branch çalışırken `calls` ve `awaitingUserResultCodeCalls`
 * arasında call'ın gözlenebilir hiçbir noktada her iki cache'ten de
 * eksik olmadığını doğrular.
 */
@RunWith(MockitoJUnitRunner.class)
public class AwaitingUserResultCacheTransitionRegressionTest {

  private static final String CALL_ID = "dl-awaiting-cache-transition-1";

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
  public void shouldKeepCallInAwaitingCacheBeforeClearCallRemovesItFromCallsCache() {
    Call call = callWaitingForUserResultCode();
    AtomicBoolean awaitingCachePopulatedDuringClearCall = new AtomicBoolean(false);
    AtomicBoolean callsCacheStillPopulatedDuringClearCall = new AtomicBoolean(false);
    CallOriginator originator = mock(CallOriginator.class);
    call.setCallOriginator(originator);

    doAnswer(invocation -> {
      // ManagerConnectionDecorator.clearCall, ilk olarak callOriginator.clearCall
      // çağırır; bu noktada cache invalidasyonları henüz yapılmadı.
      // Race penceresi olmamalı: awaiting cache çoktan dolu olmalı,
      // calls cache de henüz invalidate edilmemiş olmalı.
      awaitingCachePopulatedDuringClearCall.set(
          decorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_ID) == call);
      callsCacheStillPopulatedDuringClearCall.set(
          decorator.getCalls().getIfPresent(CALL_ID) == call);
      return null;
    }).when(originator).clearCall(any(Call.class));

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

    assertThat(
        "Awaiting cache, clearCall callOriginator adımına girdiği anda dolu olmalı; aksi hâlde race window vardır.",
        awaitingCachePopulatedDuringClearCall.get(), is(true));
    assertThat(
        "Calls cache, clearCall callOriginator adımına girdiği anda hâlâ dolu olmalı; clearCall sırası bozulmuş olabilir.",
        callsCacheStillPopulatedDuringClearCall.get(), is(true));

    // Son durum: call yalnız awaitingUserResultCodeCalls cache'inde tutulmalı.
    assertThat(decorator.getCalls().getIfPresent(CALL_ID), is((Call) null));
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_ID), sameInstance(call));
  }

  @Test
  public void shouldRemoveCallFromAwaitingCacheWhenProcessCallResultFinalizesIt() {
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

    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_ID), sameInstance(call));

    ResultCode userResultCode = new ResultCode();
    userResultCode.setId(700L);
    userResultCode.setFinalStatus(true);
    call.setUserResultCode(userResultCode);
    decorator.processCallResult(call, new Date(18000L));

    // processCallResult finalize akışı, awaiting cache'ten call'ı çıkarmalı.
    assertThat(decorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_ID), is((Call) null));
    assertThat(decorator.getCalls().getIfPresent(CALL_ID), is((Call) null));
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
    cdr.setId(2002L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(1L));
    cdr.setCallHandlingStartTime(new Date());
    call.setCdr(cdr);
    return call;
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
