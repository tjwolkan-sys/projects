package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
 * `finalizeAwaitingUserResultCodeCall` çalıştığında, in-memory `call.userResultCode`
 * null olsa bile webphone durable fallback'in DB'ye yazdığı `pd_cdr.user_result_code_id`
 * değerinin korunduğunu doğrular. Awaiting-timeout cleanup'ın null-overwrite davranışına
 * karşı dar regression testleri içerir.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class AwaitingUserResultTimeoutPreserveRegressionTest {

  private static final String CALL_ID = "dl-awaiting-timeout-preserve-1";

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
  public void shouldPreserveDbUserResultCodeOnAwaitingTimeoutFinalize() {
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());
    Call call = callInAwaitingState();
    Long persistedUserResultCodeId = 259L;

    CDR persistedCdr = new CDR();
    persistedCdr.setId(call.getCdr().getId());
    persistedCdr.setInteractionId(call.getId());
    persistedCdr.setCallHandlingStartTime(call.getCdr().getCallHandlingStartTime());
    persistedCdr.setCallEndTime(call.getCdr().getCallEndTime());
    persistedCdr.setTelephonyRC(call.getCdr().getTelephonyRC());
    persistedCdr.setUserResultCodeId(persistedUserResultCodeId);

    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> savedCdrs = cdrCaptor.getAllValues();
    CDR finalSavedCdr = savedCdrs.get(savedCdrs.size() - 1);

    assertThat("Awaiting timeout cleanup, DB CDR'da set edilmiş user_result_code_id alanını ezmemeli.",
        finalSavedCdr.getUserResultCodeId(), is(persistedUserResultCodeId));
    verify(cdrService, atLeastOnce()).getByInteractionId(CALL_ID);
  }

  @Test
  public void shouldKeepCdrUserResultCodeNullWhenDbAndInMemoryAreBothEmpty() {
    when(dialerFacade.getCampaignContexts()).thenReturn(Collections.emptyMap());
    Call call = callInAwaitingState();

    CDR persistedCdr = new CDR();
    persistedCdr.setId(call.getCdr().getId());
    persistedCdr.setInteractionId(call.getId());
    persistedCdr.setCallHandlingStartTime(call.getCdr().getCallHandlingStartTime());
    persistedCdr.setCallEndTime(call.getCdr().getCallEndTime());
    persistedCdr.setTelephonyRC(call.getCdr().getTelephonyRC());
    persistedCdr.setUserResultCodeId(null);

    when(cdrService.getByInteractionId(CALL_ID)).thenReturn(persistedCdr);
    // FIX-GLB90060295: awaiting timeout artık authoritative webphone kaynağını da okuyor. Bu
    // senaryo "hiçbir kaynakta result code yok" durumudur; mock default'una bırakılmaz.
    when(cdrService.findWebphoneUserResultCodeId(any(String.class), any(Long.class))).thenReturn(null);

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> savedCdrs = cdrCaptor.getAllValues();
    CDR finalSavedCdr = savedCdrs.get(savedCdrs.size() - 1);

    assertThat("DB ve in-memory user_result_code yokken awaiting timeout sonucu null kalmalı.",
        finalSavedCdr.getUserResultCodeId(), is((Long) null));
  }

  @Test
  public void shouldNotInvokeProcessCallResultWhenBusinessAlreadyFinalized() {
    Call call = callInAwaitingState();
    call.markBusinessResultFinalized();

    ReflectionTestUtils.invokeMethod(decorator, "finalizeAwaitingUserResultCodeCall", call);

    verify(cdrService, times(0)).saveOrUpdate(any(CDR.class));
    verify(cdrService, times(0)).getByInteractionId(any(String.class));
    verify(dbOperations, times(0)).insertCallResultToDB(any(Call.class));
  }

  private Call callInAwaitingState() {
    Campaign campaign = new Campaign();
    campaign.setId(71L);
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
    call.setAnswerDate(Utils.convertDateToDouble(new Date(2000L)));
    call.setStartDate(Utils.convertDateToDouble(new Date(1000L)));
    call.setAnsweredByUser(true);
    call.setUser(userWithResultCodes());
    call.setTelephonyResultCode(normalClearing);
    call.setCallOriginator(mock(CallOriginator.class));

    CDR cdr = new CDR();
    cdr.setId(3003L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallEndTime(new Date(5000L));
    cdr.setCallHandlingStartTime(new Date(2500L));
    cdr.setTelephonyRC(normalClearing);
    call.setCdr(cdr);

    // Awaiting cache'e elle yerleştir; finalizeAwaitingUserResultCodeCall RemovalListener
    // path'inden çağrıldığında call cache'ten çıkarılmış olur, ancak Call objesi finalize
    // akışına argüman olarak girer.
    decorator.getAwaitingUserResultCodeCalls().put(call.getId(), call);
    return call;
  }

  private User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(9930L);
    user.setGroup(group);
    return user;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    normalClearing = telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
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
