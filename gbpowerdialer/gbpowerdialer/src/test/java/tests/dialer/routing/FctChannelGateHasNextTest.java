package tests.dialer.routing;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
 * Faz 2: {@code HAS_NEXT} kaynağı olan {@link FctChannelGate#hasNextCallerId(Call)}.
 *
 * <p>Sözleşme tek yönlü tehlikelidir: {@code HAS_NEXT=1} gönderip re-originate ETMEMEK, Asterisk
 * {@code h} extension'ının Disconnect/ODBC/hangup_data adımlarını atlamasına ve o bacağın hangup
 * kaydının hiç oluşmamasına yol açar. Tersi yalnızca eski davranıştır. Bu yüzden bu sınıf iki şeyi
 * kilitler: (1) tekil predicate davranışları, (2) {@code hasNextCallerId} ile gerçek karar yolunun
 * (cause 34 sonrası {@code originateCall} çağrılıyor mu) BİREBİR eşdeğerliği.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FctChannelGateHasNextTest {

  private static final String CALL_ID = "dl-has-next";
  private static final String FCT_A = "05550000401";
  private static final String FCT_B = "05550000402";
  private static final String FIXED_LINE = "02120000001";

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

  private FctChannelRegistry registry;
  private FctChannelGate gate;
  /** Eşdeğerlik testinde gerçek re-originate kararını sayar (vacuous verify yerine). */
  private final AtomicInteger originateCount = new AtomicInteger();

  @Before
  public void setUp() {
    registry = newRegistry();
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
    gate.setListRetryMax(1);
    gate.setFixedLineRetryMax(1);
  }

  // ---------------------------------------------------------------- predicate davranışları

  @Test
  public void shouldReportNextWhenCursorHasUsableCandidate() {
    Call call = fctCall(null);
    assertThat(gate.hasNextCallerId(call), is(true));
    assertThat(gate.hasNextFctCandidate(call), is(true));
    assertThat("sabit hat tanımsızsa fallback yok", gate.hasFixedLineFallback(call), is(false));
  }

  @Test
  public void shouldNotCountCursorWhenRemainingCandidatesAreInCooldown() {
    Call call = fctCall(null);
    registry.markFull(FCT_B);

    assertThat("kalan tek aday cooldown'da", gate.hasNextFctCandidate(call), is(false));
    assertThat(gate.hasNextCallerId(call), is(false));
  }

  @Test
  public void shouldNotCountCursorWhenListRetryLimitReached() {
    gate.setListRetryMax(0);
    Call call = fctCall(null);

    assertThat(gate.hasNextFctCandidate(call), is(false));
    assertThat(gate.hasNextCallerId(call), is(false));
  }

  @Test
  public void shouldCountFixedLineWhenCursorIsNotUsable() {
    gate.setListRetryMax(0);
    Call call = fctCall(FIXED_LINE);

    assertThat("FCT bütçesi bitse de sabit hat ayrı bütçedir", gate.hasFixedLineFallback(call), is(true));
    assertThat(gate.hasNextCallerId(call), is(true));
    assertThat(gate.nextFixedLineCallerId(call), is(FIXED_LINE));
  }

  @Test
  public void shouldNotCountFixedLineWhenItsBudgetIsExhausted() {
    gate.setListRetryMax(0);
    Call call = fctCall(FIXED_LINE);
    call.incrementFixedLineAttemptCount();

    assertThat(gate.hasFixedLineFallback(call), is(false));
    assertThat(gate.nextFixedLineCallerId(call), is((String) null));
    assertThat(gate.hasNextCallerId(call), is(false));
  }

  @Test
  public void shouldReportNoNextWhenGateIsDisabled() {
    ReflectionTestUtils.setField(registry, "gateEnabled", false);
    Call call = fctCall(FIXED_LINE);

    assertThat("kill switch her iki kaynağı da kapatır", gate.hasNextCallerId(call), is(false));
    assertThat(gate.hasFixedLineFallback(call), is(false));
  }

  @Test
  public void shouldReportNoNextOutsideGatePath() {
    Call call = fctCall(FIXED_LINE);
    call.getCampaign().setFctRoutingEnabled(false);

    assertThat(gate.hasNextCallerId(call), is(false));
  }

  // ---------------------------------------------------------------- eşdeğerlik kilidi

  /**
   * {@code hasNextCallerId} ile gerçek karar yolu ayrışamaz. Her senaryoda, cause 34 hangup'ından
   * ÖNCE okunan {@code hasNextCallerId} değeri, hangup sonrası {@code originateCall}'ın çağrılıp
   * çağrılmadığıyla birebir aynı olmalıdır.
   *
   * <p>Gate kapalı senaryosu buraya DAHİL DEĞİLDİR: o durumda {@code HAS_NEXT} değişkeni Asterisk'e
   * hiç gönderilmez (bkz. {@code ManagerConnectionDecoratorHasNextVariableTest}), dolayısıyla
   * eşdeğerlik sözleşmesi uygulanmaz.
   */
  @Test
  public void hasNextCallerIdShouldMatchActualReOriginateDecision() {
    assertEquivalence("aday var, sabit hat yok", 1, null, false, false);
    assertEquivalence("kalan aday cooldown'da", 1, null, true, false);
    assertEquivalence("FCT bütçesi 0, sabit hat yok", 0, null, false, false);
    assertEquivalence("FCT bütçesi 0, sabit hat var", 0, FIXED_LINE, false, false);
    assertEquivalence("kalan aday cooldown, sabit hat var", 1, FIXED_LINE, true, false);
    assertEquivalence("cursor son entry'de, sabit hat var", 1, FIXED_LINE, false, true);
    assertEquivalence("cursor son entry'de, sabit hat yok", 1, null, false, true);
  }

  private void assertEquivalence(String label, int listRetryMax, String fixedLine,
                                 boolean cooldownRemaining, boolean cursorAtLastEntry) {
    registry = newRegistry();
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);
    gate.setListRetryMax(listRetryMax);
    gate.setFixedLineRetryMax(1);

    ManagerConnectionDecorator decorator = newDecorator();
    Call call = fctCall(fixedLine);
    if (cursorAtLastEntry) {
      // Cursor'ı son entry'ye taşı: FCT tarafında aday kalmasın.
      call.getRouteDetailsCursor().next();
      call.setCallerId(FCT_B);
    }
    if (cooldownRemaining) {
      registry.markFull(FCT_B);
    }

    boolean predicted = gate.hasNextCallerId(call);

    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    boolean actuallyReOriginated = originateCount.get() > 0;
    assertThat(label + " -> HAS_NEXT tahmini ile gerçek re-originate kararı eşleşmeli",
        actuallyReOriginated, is(predicted));
  }

  // ---------------------------------------------------------------- fixture

  private ManagerConnectionDecorator newDecorator() {
    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(connectionComposition.getFctChannelGate()).thenReturn(gate);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);

    ManagerConnectionDecorator real =
        new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    real.setCdrService(cdrService);
    real.setServer(server());
    real.setDbOperations(dbOperations);
    real.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
    ManagerConnectionDecorator spied = spy(real);
    originateCount.set(0);
    doAnswer(invocation -> {
      originateCount.incrementAndGet();
      return true;
    }).when(spied).originateCall(any(Call.class));
    doNothing().when(spied).processCallResult(any(Call.class), any(Date.class));
    return spied;
  }

  private DialerHangupEvent cause34Event() {
    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());
    return event;
  }

  private Call fctCall(String fixedLineCallerId) {
    Campaign campaign = new Campaign();
    campaign.setId(92L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setCallerId(fixedLineCallerId);
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
    call.setAttempts(1);
    call.setRouteDetailsCursor(new RouteDetailsCursor(entries()));
    call.setCallOriginator(callOriginator);

    CDR cdr = new CDR();
    cdr.setId(6006L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(9000L));
    call.setCdr(cdr);

    gate.reserveForOrigination(call);
    return call;
  }

  private List<RouteDetailEntry> entries() {
    return Arrays.asList(entry(FCT_A), entry(FCT_B));
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
