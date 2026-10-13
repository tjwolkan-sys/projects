package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.globalbilgi.gbqueueselectionservice.grpc.RouteDetailEntry;
import com.powerdialer.engine.CallCleaner;
import com.powerdialer.engine.CallUpToDateKeeper;
import com.powerdialer.engine.StopConditionChecker;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.model.TelephonyResultCode;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorFctRoutingHangupTest {

  private static final String CALL_ID = "fct-routing-1";

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
  private StopConditionChecker stopConditionChecker;

  @Mock
  private CallCleaner callCleaner;

  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;

  @Mock
  private CallOriginator callOriginator;

  private ManagerConnectionDecorator decorator;
  private TelephonyResultCode trc25;

  @Before
  public void setUp() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);

    ManagerConnectionDecorator real = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    real.setCdrService(cdrService);
    real.setServer(server());
    real.setDbOperations(dbOperations);
    real.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
    decorator = spy(real);

    // Originate yan etkilerini izole et: list-retry kolu çağırınca true dönsün, gerçek AMI/Redis akışı çalışmasın.
    doReturn(true).when(decorator).originateCall(any(Call.class));
    // Cursor exhaust kolunda standart processCallResult çağrılır; gerçek akışın iç ringing/clearCall NPE'lerinden kurtulmak için stub'la.
    doNothing().when(decorator).processCallResult(any(Call.class), any(Date.class));
  }

  @Test
  public void shouldAdvanceCursorAndReoriginateOnCause34WhenCursorHasNext() {
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);
    Campaign campaign = call.getCampaign();
    campaign.addCallInProgress(call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    RouteDetailsCursor cursor = call.getRouteDetailsCursor();
    assertThat("cursor advanced to next entry", cursor.position(), is(1));
    assertThat("callerId switched to next entry", call.getCallerId(), is("0502"));
    assertThat("telephonyResultCode reset for re-originate", call.getTelephonyResultCode(), is((TelephonyResultCode) null));
    assertThat("startDate reset for re-originate", call.getStartDate(), is((Double) null));
    assertThat("originationDate reset for re-originate", call.getOriginationDate(), is((Double) null));
    assertThat("cache cleared for re-originate", decorator.getCalls().getIfPresent(call.getId()), is((Call) null));
    assertThat("call removed from in-progress before re-originate", campaign.hasCallsInProgress(), is(false));

    // List-retry kolu: yeni Originate atıldı, processCallResult ve standart hangup zinciri (publishCallEndEvent, dbOperations.insertCallResultToDB) çağrılmadı.
    verify(decorator).originateCall(call);
    verify(decorator, never()).processCallResult(any(Call.class), any(Date.class));
    verify(callEventPublisher, never()).publishCallEndEvent(any(String.class), any(Date.class));
    verify(dbOperations, never()).insertCallResultToDB(any(Call.class));
  }

  @Test
  public void shouldFallBackToStandardHangupFlowWhenCursorIsExhausted() {
    Call call = fctCall();
    // Cursor'ı son entry'ye advance et.
    call.getRouteDetailsCursor().next();
    decorator.getCalls().put(call.getId(), call);

    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", event);

    // Cursor exhaust: standart processCallResult çağrıldı, list-retry tetiklenmedi (yeni Originate yok).
    verify(decorator).processCallResult(eq(call), any(Date.class));
    verify(decorator, never()).originateCall(any(Call.class));
    verify(callEventPublisher).publishCallEndEvent(eq(call.getId()), any(Date.class));
  }

  private Call fctCall() {
    Campaign campaign = new Campaign();
    campaign.setId(101L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);

    RouteDetailsCursor cursor = new RouteDetailsCursor(Arrays.asList(
        entry("0501", "Manual", 1),
        entry("0502", "Retry", 1)));

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setCallerId(cursor.current().getPhoneNumber());
    call.setRouteDetailsCursor(cursor);
    call.setCallOriginator(callOriginator);

    CDR cdr = new CDR();
    cdr.setId(2002L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(8000L));
    call.setCdr(cdr);
    return call;
  }

  private RouteDetailEntry entry(String phone, String type, int attempts) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phone)
        .setType(type)
        // QSS canonical proto'da attempts string alandır (FEAT-GLB90114534-FCT-KANAL-KAPASITE-GATE).
        .setAttempts(String.valueOf(attempts))
        .build();
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    trc25 = telephonyResultCode(25L, 25);
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    resultCodes.put(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE, trc25);
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
