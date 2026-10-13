package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
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
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * FIX-320173 savunma katmanı regression'ları: pd_telephony_rc'de eşleşmesi olmayan veya parse
 * edilemeyen hangup cause'ları finalize'ı null TRC ile zehirlemek yerine CALL_EXPIRED_CLEARING
 * (901) fallback koduna düşer.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class UnmappedHangupCauseFallbackRegressionTest {

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
  private TelephonyResultCode expiredClearingFallback;

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
  public void shouldFallbackToExpiredClearingWhenHangupCauseIsNotMapped() {
    Call call = unansweredCall("dl-unmapped-cause-1");
    decorator.getCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(call.getId());
    event.setHangupCause("997");
    event.setDateReceived(new Date(20000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    assertThat(cdrCaptor.getValue().getTelephonyRC().getCode(),
        is(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode()));
    assertThat(cdrCaptor.getValue().getCallEndTime(), is(event.getDateReceived()));
    assertThat(call.isBusinessResultFinalized(), is(true));
  }

  @Test
  public void shouldFallbackToExpiredClearingWhenHangupCauseIsNotNumeric() {
    Call call = unansweredCall("dl-unparseable-cause-1");
    decorator.getCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(call.getId());
    event.setHangupCause("not-a-number");
    event.setDateReceived(new Date(21000L));

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    ArgumentCaptor<CDR> cdrCaptor = ArgumentCaptor.forClass(CDR.class);
    verify(cdrService).saveOrUpdate(cdrCaptor.capture());
    assertThat(cdrCaptor.getValue().getTelephonyRC().getCode(),
        is(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode()));
    assertThat(call.isBusinessResultFinalized(), is(true));
  }

  private Call unansweredCall(String callId) {
    Campaign campaign = new Campaign();
    campaign.setId(85L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);
    PhoneNumberColumn phoneNumberColumn = new PhoneNumberColumn();
    phoneNumberColumn.setColNumber("phone1");
    campaign.getPhoneNumberColumns().add(phoneNumberColumn);

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
    cdr.setCallEndTime(new Date(9100L));
    call.setCdr(cdr);

    campaign.addCallInProgress(call);
    campaign.addToOriginatedCalls(call);
    return call;
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    expiredClearingFallback = telephonyResultCode(73L, Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode());
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.TELEPHONY_RC_NORMAL_CLEARING,
        telephonyResultCode(10L, Constants.TELEPHONY_RC_NORMAL_CLEARING));
    resultCodes.put(Constants.ERROR_RESULT_CODES.CALL_EXPIRED_CLEARING.getCode(), expiredClearingFallback);
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
