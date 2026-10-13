package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 * Task 327734: Awaiting user-result-code penceresi agent'ın webphone grubundaki "Wrapup timer"
 * değerinden (saniye) türetilir; preview kampanyada kampanyanın result code timer'ı tanımlıysa
 * webphone gibi o değer önceliklidir. Değer yok veya 0 ise mevcut varsayılan (15 dk) korunur;
 * 15 dk aynı zamanda cache ceiling'i olduğu için üst sınırdır. Süresi dolan çağrı awaiting
 * cache'ten çıkarılıp mevcut finalize hattına verilir; URC gelmiş ve çağrı cache'ten çıkmışsa
 * hiçbir şey yapılmaz.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AwaitingUserResultWrapupTimerWindowFeatureTest {

  private static final String CALL_ID = "dl-awaiting-wrapup-window-1";
  private static final long DEFAULT_TIMEOUT_MS = Constants.AWAITING_USER_RESULT_EXPIRE_TIMEOUT;

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
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
  }

  @Test
  public void shouldUseGroupWrapupTimerAsAwaitingWindow() {
    Call call = callWithGroupWrapupTimer(40);

    assertThat("Pencere grubun wrapup timer'ı (40 sn) olmalı.", resolveTimeout(call), is(40_000L));
  }

  @Test
  public void shouldFallBackToDefaultWhenWrapupTimerIsZero() {
    Call call = callWithGroupWrapupTimer(0);

    assertThat("Webphone'da 0 = wrap-up beklemesi yok; dialer varsayılan 15 dk'yı korumalı.",
        resolveTimeout(call), is(DEFAULT_TIMEOUT_MS));
  }

  @Test
  public void shouldFallBackToDefaultWhenGroupHasNoWrapupTimer() {
    Call call = callWithGroupWrapupTimer(null);

    assertThat(resolveTimeout(call), is(DEFAULT_TIMEOUT_MS));
  }

  @Test
  public void shouldFallBackToDefaultWhenCallHasNoUser() {
    Call call = callWithGroupWrapupTimer(40);
    call.setUser(null);

    assertThat(resolveTimeout(call), is(DEFAULT_TIMEOUT_MS));
  }

  @Test
  public void shouldCapAwaitingWindowAtDefaultCeiling() {
    Call call = callWithGroupWrapupTimer(3600);

    assertThat("Cache ceiling'i 15 dk olduğu için daha uzun wrapup timer 15 dk'ya kırpılmalı.",
        resolveTimeout(call), is(DEFAULT_TIMEOUT_MS));
  }

  @Test
  public void shouldPreferCampaignResultCodeTimerOnPreviewCampaign() {
    Call call = callWithGroupWrapupTimer(40);
    call.getCampaign().setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);
    call.getCampaign().setResultCodeTimer(120);

    assertThat("Preview'da webphone RESULT_CODE_TIMER kanal değişkenini (kampanya) kullanır; dialer da öyle yapmalı.",
        resolveTimeout(call), is(120_000L));
  }

  @Test
  public void shouldUseGroupWrapupTimerOnPreviewCampaignWithoutCampaignTimer() {
    Call call = callWithGroupWrapupTimer(40);
    call.getCampaign().setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);
    call.getCampaign().setResultCodeTimer(null);

    assertThat(resolveTimeout(call), is(40_000L));
  }

  @Test
  public void shouldIgnoreCampaignResultCodeTimerOnProgressiveCampaign() {
    Call call = callWithGroupWrapupTimer(40);
    call.getCampaign().setResultCodeTimer(120);

    assertThat("Progressive'de webphone kampanya timer'ını okumaz; grup değeri geçerli kalmalı.",
        resolveTimeout(call), is(40_000L));
  }

  @Test
  public void shouldFinalizeAndEvictAwaitingCallWhenWindowIsDue() {
    Call call = callWithGroupWrapupTimer(40);
    decorator.getAwaitingUserResultCodeCalls().put(call.getId(), call);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(null);
    when(cdrService.findWebphoneUserResultCodeId(anyString(), anyLong())).thenReturn(null);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCallIfDue", CALL_ID);

    assertThat("Süresi dolan çağrı awaiting cache'ten çıkarılmalı.",
        decorator.getAwaitingUserResultCodeCalls().getIfPresent(CALL_ID), nullValue());
    assertThat("Çağrı mevcut finalize hattından geçmeli (business result finalized).",
        call.isBusinessResultFinalized(), is(true));
    verify(cdrService, atLeastOnce()).saveOrUpdate(any(CDR.class));
  }

  @Test
  public void shouldDoNothingWhenCallAlreadyLeftAwaitingCache() {
    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCallIfDue", CALL_ID);

    verify(cdrService, never()).saveOrUpdate(any(CDR.class));
    verify(cdrService, never()).getByInteractionId(anyString());
  }

  private long resolveTimeout(Call call) {
    Long timeout = ReflectionTestUtils.invokeMethod(decorator, "resolveAwaitingUserResultCodeTimeoutMillis", call);
    assertThat(timeout, notNullValue());
    return timeout;
  }

  private Call callWithGroupWrapupTimer(Integer wrapupTimerSeconds) {
    Campaign campaign = new Campaign();
    campaign.setId(72L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setColResultAttempts("attempts");
    campaign.setRetryScheduleEnabled(false);
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
    call.setUser(userInGroupWithWrapupTimer(wrapupTimerSeconds));
    call.setTelephonyResultCode(normalClearing);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(3005L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(5000L));
    cdr.setCallHandlingStartTime(new Date(2500L));
    cdr.setTelephonyRC(normalClearing);
    call.setCdr(cdr);
    return call;
  }

  private User userInGroupWithWrapupTimer(Integer wrapupTimerSeconds) {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    group.setWrapupTimerSeconds(wrapupTimerSeconds);
    User user = new User();
    user.setId(9932L);
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = new TelephonyResultCode();
    normalClearing.setId(10L);
    normalClearing.setCode(Constants.TELEPHONY_RC_NORMAL_CLEARING);
    normalClearing.setName("TRC-16");
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    return resultCodes;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }
}
