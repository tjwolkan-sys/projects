package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
 * Awaiting-timeout finalize'ında agent result code'unun authoritative kaynaktan
 * (`ast_registrar_cdr` + `wbp_call_result_code`) kurtarılmasını doğrular.
 *
 * <p>Kapsanan üretim vakası (06.08.2026): agent result code'u girdi, webphone kaydetti,
 * fakat Redis mesajı dialer'a hiç ulaşmadı. `pd_cdr` durable fallback tarafından da
 * yazılmadığı için 15 dk sonunda awaiting-timeout finalize'ı `user_result_code_id` NULL
 * yazıyordu ve telafi yolu kalmıyordu.</p>
 *
 * <p>Guard sırası da burada kilitlenir: `pd_cdr` → evaluator gate → result code sahibi
 * çözümü → authoritative lookup. Gereksiz DB erişimi eklenmediği "lookup hiç çağrılmaz"
 * senaryolarıyla kanıtlanır.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AwaitingUserResultAuthoritativeRecoveryRegressionTest {

  private static final String CALL_ID = "dl-awaiting-authoritative-recovery-1";
  private static final Long AGENT_ID = 9930L;
  private static final Long WEBPHONE_RESULT_CODE_ID = 171L;

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
  private TelephonyResultCode answeredButNoMtConnection;

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
  public void shouldRecoverUserResultCodeFromWebphoneTablesWhenPersistedCdrHasNone() {
    Call call = callInAwaitingState(normalClearing);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));
    when(cdrService.findWebphoneUserResultCodeId(CALL_ID, AGENT_ID)).thenReturn(WEBPHONE_RESULT_CODE_ID);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("Redis mesajı kaybolduğunda authoritative webphone result code pd_cdr'a yazılmalı.",
        lastSavedCdr().getUserResultCodeId(), is(WEBPHONE_RESULT_CODE_ID));
    assertThat("Kurtarılan result code in-memory call'a da bağlanmalı (kampanya result tablosu aynı değeri yazar).",
        call.getUserResultCode() != null ? call.getUserResultCode().getId() : null, is(WEBPHONE_RESULT_CODE_ID));
    verify(dbOperations, atLeastOnce()).insertCallResultToDB(call);
  }

  @Test
  public void shouldNotQueryWebphoneTablesWhenPersistedCdrAlreadyHasUserResultCode() {
    Call call = callInAwaitingState(normalClearing);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, 259L));

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("pd_cdr'da değer varsa mevcut davranış korunur.",
        lastSavedCdr().getUserResultCodeId(), is(259L));
    verify(cdrService, never()).findWebphoneUserResultCodeId(anyString(), anyLong());
  }

  @Test
  public void shouldKeepUserResultCodeNullWhenWebphoneLookupHasNoResultCode() {
    Call call = callInAwaitingState(normalClearing);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));
    when(cdrService.findWebphoneUserResultCodeId(CALL_ID, AGENT_ID)).thenReturn(null);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("Agent gerçekten result code girmediyse davranış değişmez; URC null kalır.",
        lastSavedCdr().getUserResultCodeId(), is((Long) null));
  }

  @Test
  public void shouldIgnoreNonPositiveWebphoneResultCodeId() {
    Call call = callInAwaitingState(normalClearing);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));
    when(cdrService.findWebphoneUserResultCodeId(CALL_ID, AGENT_ID)).thenReturn(0L);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("Webphone tablosundan gelen geçersiz id pd_cdr'a yazılmamalı.",
        lastSavedCdr().getUserResultCodeId(), is((Long) null));
    assertThat("Geçersiz id in-memory call'a da bağlanmamalı.",
        call.getUserResultCode(), is((ResultCode) null));
  }

  @Test
  public void shouldSkipWebphoneLookupWhenEvaluatorSuppressesUserResultCode() {
    Call call = callInAwaitingState(answeredButNoMtConnection);
    call.getCdr().setCallHandlingStartTime(null);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    verify(cdrService, never()).findWebphoneUserResultCodeId(anyString(), anyLong());
    assertThat("TRC 84 gibi başarısız çağrılarda URC bastırma davranışı kasıtlıdır, korunur.",
        lastSavedCdr().getUserResultCodeId(), is((Long) null));
  }

  @Test
  public void shouldSkipWebphoneLookupWhenResultCodeOwnerCannotBeResolved() {
    Call call = callInAwaitingState(normalClearing);
    call.setUser(null);
    call.getCdr().setAssignedUser(null);
    call.getCdr().setConnectedUser(null);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    verify(cdrService, never()).findWebphoneUserResultCodeId(anyString(), anyLong());
  }

  @Test
  public void shouldResolveResultCodeOwnerFromCdrAssignedUserWhenCallUserIsCleared() {
    Call call = callInAwaitingState(normalClearing);
    call.setUser(null);
    call.getCdr().setAssignedUser(agent());
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));
    when(cdrService.findWebphoneUserResultCodeId(CALL_ID, AGENT_ID)).thenReturn(WEBPHONE_RESULT_CODE_ID);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    verify(cdrService).findWebphoneUserResultCodeId(eq(CALL_ID), eq(AGENT_ID));
  }

  @Test
  public void shouldFinalizeCallWhenWebphoneLookupFails() {
    Call call = callInAwaitingState(normalClearing);
    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr(call, null));
    when(cdrService.findWebphoneUserResultCodeId(CALL_ID, AGENT_ID))
        .thenThrow(new RuntimeException("webphone lookup timeout"));

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    assertThat("Authoritative lookup hatası finalize persist'ini kesmemeli.",
        lastSavedCdr().getUserResultCodeId(), is((Long) null));
  }

  private CDR lastSavedCdr() {
    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> savedCdrs = cdrCaptor.getAllValues();
    return savedCdrs.get(savedCdrs.size() - 1);
  }

  private CDR persistedCdr(Call call, Long userResultCodeId) {
    CDR persistedCdr = new CDR();
    persistedCdr.setId(call.getCdr().getId());
    persistedCdr.setInteractionId(call.getId());
    persistedCdr.setCallHandlingStartTime(call.getCdr().getCallHandlingStartTime());
    persistedCdr.setCallEndTime(call.getCdr().getCallEndTime());
    persistedCdr.setTelephonyRC(call.getCdr().getTelephonyRC());
    persistedCdr.setUserResultCodeId(userResultCodeId);
    return persistedCdr;
  }

  private Call callInAwaitingState(TelephonyResultCode telephonyResultCode) {
    Campaign campaign = new Campaign();
    campaign.setId(64L);
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
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setAnsweredByUser(true);
    call.setUser(agent());
    call.setTelephonyResultCode(telephonyResultCode);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(6006L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(1500L));
    cdr.setCallAnswerTime(new Date(2000L));
    cdr.setCallHandlingStartTime(new Date(2500L));
    cdr.setCallEndTime(new Date(5000L));
    cdr.setTelephonyRC(telephonyResultCode);
    call.setCdr(cdr);

    decorator.getAwaitingUserResultCodeCalls().put(call.getId(), call);
    return call;
  }

  private User agent() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(AGENT_ID);
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    answeredButNoMtConnection = telephonyResultCode(
        (long) Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING, normalClearing);
    resultCodes.put(Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT, answeredButNoMtConnection);
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
