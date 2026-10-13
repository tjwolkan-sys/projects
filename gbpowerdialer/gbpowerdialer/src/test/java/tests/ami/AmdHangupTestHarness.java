package tests.ami;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
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
 * FEAT-325470 hangup testleri icin ortak fixture: gercek {@link ManagerConnectionDecorator},
 * mock'lanmis boundary'ler ve AMD ailesini iceren telephony result code haritasi. Her
 * senaryo icin yeni bir harness kurulur; boylece mock etkilesimleri kodlar arasinda karismaz.
 */
final class AmdHangupTestHarness {

  static final int USER_BUSY_CODE = 17;

  final ManagerConnectionDecorator decorator;
  final CampaignDbOperations dbOperations = mock(CampaignDbOperations.class);
  final CDRService cdrService = mock(CDRService.class);
  final DynamicBoostManager dynamicBoostManager = mock(DynamicBoostManager.class);

  AmdHangupTestHarness() {
    LoggerDecorator loggerDecorator = mock(LoggerDecorator.class);
    ManagerConnectionComposition connectionComposition = mock(ManagerConnectionComposition.class);
    DialerFacade dialerFacade = mock(DialerFacade.class);
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(mock(StopConditionChecker.class));
    when(connectionComposition.getCallCleaner()).thenReturn(mock(CallCleaner.class));
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(mock(CallUpToDateKeeper.class));
    when(dialerFacade.getCallEventPublisher()).thenReturn(mock(CallEventPublisher.class));
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(mock(CampaignRealTimeRingingManager.class));

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setDynamicBoostManager(dynamicBoostManager);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodesWithAmdFamily());
  }

  /** Call'i calls cache'ine koyar ve verilen cause ile DialerHangup event'ini isler. */
  void hangup(Call call, int cause) {
    decorator.getCalls().put(call.getId(), call);
    DialerHangupEvent event = new DialerHangupEvent(AmdHangupTestHarness.class);
    event.setOriginId(call.getId());
    event.setHangupCause(String.valueOf(cause));
    event.setDateReceived(new Date(20000L));
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);
  }

  boolean isAwaitingUserResultCode(Call call) {
    return decorator.getAwaitingUserResultCodeCalls().getIfPresent(call.getId()) == call;
  }

  CDR lastPersistedCdr() {
    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService, atLeastOnce()).saveOrUpdate(cdrCaptor.capture());
    List<CDR> persisted = cdrCaptor.getAllValues();
    return persisted.get(persisted.size() - 1);
  }

  /** Preview kampanya, agent atanmis, grubunda result code var, URC yok, musteri bacagi cevapsiz. */
  static Call previewCallWaitingForUserResultCode(String callId) {
    Call call = call(callId, campaign(Constants.CAMPAIGN_TYPE.PREVIEW_FREE));
    call.setUser(userWithResultCodes());
    return call;
  }

  /** Predictive kampanya, agent yok, musteri bacagi cevaplanmis, agent handling hic baslamamis. */
  static Call answeredCallWithoutAgentHandling(String callId, Constants.PREDICTIVE_STRATEGY predictiveStrategy) {
    Campaign campaign = campaign(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setPredictiveStrategy(predictiveStrategy);
    Call call = call(callId, campaign);
    call.setAnswerDate(Utils.convertDateToDouble(new Date(15000L)));
    call.getCdr().setCallAnswerTime(new Date(15000L));
    call.getCdr().setCallHandlingStartTime(null);
    return call;
  }

  static Campaign campaign(Constants.CAMPAIGN_TYPE type) {
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

  static Call call(String callId, Campaign campaign) {
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

  static User userWithResultCodes() {
    Group group = new Group();
    group.setResultCodes(Collections.singletonList(new ResultCode()));
    User user = new User();
    user.setId(7L);
    user.setGroup(group);
    return user;
  }

  /** 16, 17, 901, 911 ve AMD ailesi (900/113/114/115); id'ler uretim tablosuyla ayni. */
  static Map<Integer, TelephonyResultCode> telephonyResultCodesWithAmdFamily() {
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    put(resultCodes, 10L, Constants.TELEPHONY_RC_NORMAL_CLEARING);
    put(resultCodes, 11L, USER_BUSY_CODE);
    put(resultCodes, 71L, Constants.TELEPHONY_RC_AMD);
    put(resultCodes, 73L, Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode());
    put(resultCodes, (long) Constants.TELEPHONY_RC_ANSWERED_BUT_NO_MT_CONNECTION, Constants.TELEPHONY_RC_CUSTOMER_HANGUP_BEFORE_AGENT);
    put(resultCodes, 86L, Constants.TELEPHONY_RC_AMD_BAD_NUMBER);
    put(resultCodes, 87L, Constants.TELEPHONY_RC_AMD_NOT_REACHED);
    put(resultCodes, 88L, Constants.TELEPHONY_RC_AMD_UNKNOWN_TONE);
    return resultCodes;
  }

  private static void put(Map<Integer, TelephonyResultCode> resultCodes, Long id, int code) {
    TelephonyResultCode telephonyResultCode = new TelephonyResultCode();
    telephonyResultCode.setId(id);
    telephonyResultCode.setCode(code);
    telephonyResultCode.setName("TRC-" + code);
    resultCodes.put(code, telephonyResultCode);
  }

  private static Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }

}
