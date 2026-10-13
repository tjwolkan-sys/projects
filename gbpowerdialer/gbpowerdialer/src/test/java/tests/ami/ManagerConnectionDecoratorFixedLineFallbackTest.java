package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
import com.powerdialer.dto.FctChannelEntryDTO;
import com.powerdialer.dto.FctChannelEventDTO;
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
import com.powerdialer.util.Utils;

/**
 * Faz 2: FCT listesi tükendiğinde çağrı TRC 25 ile kapatılmaz, kampanyanın sabit hat caller
 * ID'siyle ({@code Campaign.callerId}) son bir kez originate edilir.
 *
 * <p>Sabit hatta kapasite kavramı yoktur: registry'de rezervasyon TUTULMAZ, yalnız
 * {@code FALLBACK_FIXED_LINE} feed olayı üretilir. Bütçe {@code fct.fixed.line.retry.max} ile
 * ayrıdır ve {@code fct.channel.list.retry.max = 0} iken bile çalışır.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorFixedLineFallbackTest {

  private static final String CALL_ID = "dl-fixed-line-fallback";
  private static final String FCT_A = "05550000401";
  private static final String FIXED_LINE = "02120000001";
  private static final long FIRST_LEG_AGE_MS = 4000L;

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
    // FCT bütçesi kapalı: sabit hat fallback'inin AYRI bütçeyle çalıştığını kanıtlar.
    gate.setListRetryMax(0);
    gate.setFixedLineRetryMax(1);

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
    decorator = spy(real);

    doReturn(true).when(decorator).originateCall(any(Call.class));
    doNothing().when(decorator).processCallResult(any(Call.class), any(Date.class));
  }

  @Test
  public void shouldFallBackToFixedLineWhenFctListIsExhausted() {
    Call call = fctCall(FIXED_LINE);
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator).originateCall(call);
    verify(decorator, never()).processCallResult(any(Call.class), any(Date.class));
    assertThat("caller ID sabit hat olmalı", call.getCallerId(), is(FIXED_LINE));
    assertThat(call.getFixedLineAttemptCount(), is(1));
  }

  @Test
  public void shouldNotReserveCapacityForFixedLine() {
    Call call = fctCall(FIXED_LINE);
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("sabit hat için rezervasyon tutulmamalı", registry.occupancy(FIXED_LINE), is(0));
    for (FctChannelEntryDTO entry : registry.snapshot().getEntries()) {
      assertThat("sabit hat registry tablosunda görünmemeli", entry.getPhoneNumber(), is(FCT_A));
    }
  }

  @Test
  public void shouldReleaseAndCooldownFailedFctBeforeFallingBack() {
    Call call = fctCall(FIXED_LINE);
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("cause 34 veren FCT bırakılmalı", registry.occupancy(FCT_A), is(0));
    assertThat("cause 34 veren FCT cooldown'a alınmalı", registry.isInCooldown(FCT_A), is(true));
  }

  @Test
  public void shouldEmitFixedLineFallbackEventToMonitorFeed() {
    Call call = fctCall(FIXED_LINE);
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    boolean found = false;
    for (FctChannelEventDTO event : registry.snapshot().getRecentEvents()) {
      if ("FALLBACK_FIXED_LINE".equals(event.getKind())) {
        found = true;
        assertThat(event.getPhoneNumber(), is(FIXED_LINE));
        assertThat(event.getCallId(), is(CALL_ID));
      }
    }
    assertTrue("feed'de FALLBACK_FIXED_LINE olayı olmalı", found);
  }

  /**
   * Sabit hat QSS'te tanımlı değil, trunk'ı da yok. Önceki FCT'den kalan trunk taşınırsa çağrı
   * yanlış trunk'tan çıkar; temizlenince dialplan varsayılan trunk'a düşer.
   */
  @Test
  public void shouldClearFctTrunkOnFixedLineLeg() {
    Call call = fctCall(FIXED_LINE);
    call.setFctTrunk("ININ");
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("sabit hat bacaginda bayat trunk tasinmamali", call.getFctTrunk(), is((String) null));
    assertThat(call.getCallerId(), is(FIXED_LINE));
  }

  @Test
  public void shouldKeepFirstLegOriginationDateOnFixedLineLeg() {
    Call call = fctCall(FIXED_LINE);
    Double firstOrigination = call.getOriginationDate();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("sabit hat bacağı da tek attempt'in parçasıdır",
        call.getOriginationDate(), is(firstOrigination));
  }

  @Test
  public void shouldFallThroughToStandardResultWhenFixedLineBudgetIsExhausted() {
    Call call = fctCall(FIXED_LINE);
    call.incrementFixedLineAttemptCount();
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, never()).originateCall(any(Call.class));
    verify(decorator).processCallResult(any(Call.class), any(Date.class));
  }

  @Test
  public void shouldFallThroughToStandardResultWhenCampaignHasNoFixedLine() {
    Call call = fctCall(null);
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, never()).originateCall(any(Call.class));
    verify(decorator).processCallResult(any(Call.class), any(Date.class));
    assertThat("sabit hat tanımsızsa caller ID değişmez", call.getCallerId(), is(FCT_A));
  }

  /** Kill switch: fallback da devre dışı kalır, 04.05.2026 davranışına dönülür. */
  @Test
  public void shouldNotFallBackWhenGateIsDisabled() {
    ReflectionTestUtils.setField(registry, "gateEnabled", false);
    Call call = fctCall(FIXED_LINE);
    // Cursor'da aday kalmasın ki kill switch olmasa fallback'e düşülecek olsun.
    call.getRouteDetailsCursor().next();
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, never()).originateCall(any(Call.class));
    assertThat("kill switch açıkken sabit hata geçilmez", call.getCallerId(), is(FCT_A));
  }

  // ---------------------------------------------------------------- fixture

  private DialerHangupEvent cause34Event() {
    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(CALL_ID);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());
    return event;
  }

  private Call fctCall(String fixedLineCallerId) {
    Campaign campaign = new Campaign();
    campaign.setId(94L);
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
    // Tek elemanlı liste: ilk cause 34'te FCT tarafı zaten tükenmiş olur.
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A))));
    call.setCallOriginator(callOriginator);

    Date firstOrigination = new Date(System.currentTimeMillis() - FIRST_LEG_AGE_MS);
    call.setOriginationDate(Utils.convertDateToDouble(firstOrigination));

    CDR cdr = new CDR();
    cdr.setId(8008L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(firstOrigination);
    call.setCdr(cdr);

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
