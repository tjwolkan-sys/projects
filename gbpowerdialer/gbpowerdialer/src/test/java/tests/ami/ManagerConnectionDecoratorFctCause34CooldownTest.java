package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
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

/**
 * cause 34 (No circuit available) hangup'ında kapasite gate'i ile birlikte beklenen davranış:
 * hangup'ı veren FCT'nin rezervasyonu bırakılır, numara cooldown'a alınır ve sıradaki UYGUN FCT
 * rezerve edilerek aynı Call için yeni Originate atılır.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorFctCause34CooldownTest {

  private static final String CALL_ID = "dl-fct-cause34";
  private static final String FCT_A = "05550000301";
  private static final String FCT_B = "05550000302";

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
  private FctChannelRegistry registry;
  private FctChannelGate gate;

  @Before
  public void setUp() {
    registry = newRegistry();
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
    // In-call retry varsayılanı 0 (kapalı); bu sınıf retry AÇIK davranışını test ettiği için
    // sınır açıkça 1'e alınır. Varsayılan davranış ManagerConnectionDecoratorFctListRetryCapTest'te.
    gate.setListRetryMax(1);

    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(connectionComposition.getFctChannelGate()).thenReturn(gate);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);

    ManagerConnectionDecorator real = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    real.setCdrService(cdrService);
    real.setServer(server());
    real.setDbOperations(dbOperations);
    real.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
    decorator = spy(real);

    doReturn(true).when(decorator).originateCall(any(Call.class));
    doNothing().when(decorator).processCallResult(any(Call.class), any(Date.class));
  }

  @Test
  public void shouldReleaseAndCooldownFailedFctThenReserveNextOnCause34() {
    Call call = fctCall();
    assertThat("ilk FCT rezerve edilmiş durumda", registry.occupancy(FCT_A), is(1));
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("cause 34 veren FCT'nin rezervasyonu bırakılır", registry.occupancy(FCT_A), is(0));
    assertThat("cause 34 veren FCT cooldown'a alınır", registry.isInCooldown(FCT_A), is(true));
    assertThat("sıradaki FCT rezerve edilir", registry.occupancy(FCT_B), is(1));
    assertThat("caller ID sıradaki FCT olur", call.getCallerId(), is(FCT_B));
    assertThat("cursor ilerletilir", call.getRouteDetailsCursor().position(), is(1));

    verify(decorator).originateCall(call);
    verify(decorator, never()).processCallResult(any(Call.class), any(Date.class));
  }

  @Test
  public void shouldFallBackToStandardResultFlowWhenNextFctIsAlsoUnavailable() {
    Call call = fctCall();
    // Sıradaki FCT'yi de başka bir çağrı doldursun: liste tükenir, standart sonuç akışına düşülür.
    registry.tryAcquire(FCT_B, "MOBILE", "1", 1, "baska-call", 9L);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("hangup'ı veren FCT yine cooldown'a alınır", registry.isInCooldown(FCT_A), is(true));
    assertThat("hangup'ı veren FCT'nin rezervasyonu bırakılır", registry.occupancy(FCT_A), is(0));
    verify(decorator).processCallResult(any(Call.class), any(Date.class));
    verify(decorator, never()).originateCall(any(Call.class));
  }

  private DialerHangupEvent cause34Event() {
    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());
    return event;
  }

  private Call fctCall() {
    Campaign campaign = new Campaign();
    campaign.setId(88L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setColResultRcSequence("rc_sequence");
    campaign.setColResultUserRc("user_rc");
    campaign.setColResultTelephonyRc("telephony_rc");
    campaign.setColResultRetryHistory("retry_history");
    campaign.setRetryScheduleEnabled(false);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setResultCodeSequence(0);
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A), entry(FCT_B))));
    call.setCallOriginator(callOriginator);

    CDR cdr = new CDR();
    cdr.setId(3003L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(8000L));
    call.setCdr(cdr);

    // Originate akışının yaptığı rezervasyonu kur: caller ID ilk FCT.
    gate.reserveForOrigination(call);
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType("MOBILE")
        .setAttempts("1")
        .setCapacity(1)
        .build();
  }

  private Map<Integer, TelephonyResultCode> telephonyResultCodes() {
    Map<Integer, TelephonyResultCode> resultCodes = new HashMap<>();
    TelephonyResultCode trc25 = new TelephonyResultCode();
    trc25.setId(25L);
    trc25.setCode(25);
    trc25.setName("TRC-25");
    resultCodes.put(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE, trc25);
    return resultCodes;
  }

  private Server server() {
    Server server = new Server();
    server.setIp("127.0.0.1");
    return server;
  }

  private FctChannelRegistry newRegistry() {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", true);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
