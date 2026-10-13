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
 * Kalem 3: cause 34 sonrası aynı çağrı içinde yeniden originate etme sınırı.
 *
 * <p>Varsayılan `fct.channel.list.retry.max = 0` — gate açıkken in-call retry yapılmaz. Gerekçe:
 * gate zaten dolu/cooldown FCT'ye gitmiyor; buna rağmen cause 34 geliyorsa kıtlık gate'in görmediği
 * seviyededir (trunk/SBC) ve sıradaki FCT'nin de başarısız olma olasılığı yüksek, kanal-zamanı
 * maliyeti ise kesin. Sınır aşılsa da "bu FCT dolu" bilgisi (release + cooldown) korunur.
 *
 * <p>Gate kill switch ile kapatıldığında sınır uygulanmaz: 04.05 FCT routing davranışı birebir
 * geri gelir (geri dönüş yolu tek property).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorFctListRetryCapTest {

  private static final String CALL_ID = "dl-fct-list-retry-cap";
  private static final String FCT_A = "05550000401";
  private static final String FCT_B = "05550000402";

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
    registry = newRegistry(true);
    gate = new FctChannelGate();
    gate.setRegistry(registry);
    gate.setDeferLimit(10);

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
  public void shouldNotRetryInCallWhenLimitIsZeroButStillCooldownFailedFct() {
    gate.setListRetryMax(0);
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, never()).originateCall(any(Call.class));
    verify(decorator).processCallResult(any(Call.class), any(Date.class));
    assertThat("kanal bırakılmalı", registry.occupancy(FCT_A), is(0));
    assertThat("FCT dolu bilgisi korunmalı", registry.isInCooldown(FCT_A), is(true));
    assertThat("sıradaki FCT rezerve edilmemeli", registry.occupancy(FCT_B), is(0));
    assertThat("cursor ilerletilmemeli", call.getRouteDetailsCursor().position(), is(0));
    assertThat(call.getFctListRetryCount(), is(0));
  }

  @Test
  public void shouldAllowExactlyOneInCallRetryWhenLimitIsOne() {
    gate.setListRetryMax(1);
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator).originateCall(call);
    assertThat(call.getFctListRetryCount(), is(1));
    assertThat(call.getCallerId(), is(FCT_B));
    assertThat(registry.occupancy(FCT_B), is(1));
  }

  @Test
  public void shouldStopAfterLimitOnSecondCause34() {
    gate.setListRetryMax(1);
    Call call = fctCall();
    call.incrementFctListRetryCount();
    decorator.getCalls().put(call.getId(), call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, never()).originateCall(any(Call.class));
    verify(decorator).processCallResult(any(Call.class), any(Date.class));
    assertThat("limit aşılsa da cooldown uygulanır", registry.isInCooldown(FCT_A), is(true));
  }

  /** Gate kill switch: 04.05 davranışı birebir geri gelir, sınır uygulanmaz. */
  @Test
  public void shouldIgnoreLimitWhenGateIsDisabled() {
    ReflectionTestUtils.setField(registry, "gateEnabled", false);
    gate.setListRetryMax(0);
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator).originateCall(call);
    assertThat("kill switch açıkken sıradaki entry'ye geçilir", call.getCallerId(), is(FCT_B));
  }

  /**
   * Originate atılamadığında (AMI exception) campaign sayaçları tutarlı kalmalı: retry kolu
   * in-progress kaydını düşürür, `originateCall` başarısız olursa yeniden eklenmez ve çağrı
   * `handleExceptionDuringOrigination` ile temizlenir.
   */
  @Test
  public void shouldKeepCampaignCountersBalancedWhenReOriginateFails() {
    gate.setListRetryMax(1);
    doReturn(false).when(decorator).originateCall(any(Call.class));
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);
    Campaign campaign = call.getCampaign();
    campaign.addCallInProgress(call);
    // NOT: hasCallsInProgress() originatedCalls'a bakar (Campaign:2133), addCallInProgress ise
    // callsInProgress set'ini doldurur. Bu testin izlediği sayaç callsInProgress olduğu için
    // getCallsInProgressCount() kullanılır.
    assertThat(campaign.getCallsInProgressCount(), is(1));

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator).originateCall(call);
    assertThat("başarısız re-originate sonrası in-progress kaydı kalmamalı",
        campaign.getCallsInProgressCount(), is(0));
    assertThat("rezervasyon sızmamalı", registry.occupancy(FCT_B), is(1));
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
    cdr.setId(4004L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(8000L));
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

  private FctChannelRegistry newRegistry(boolean gateEnabled) {
    FctChannelRegistry created = new FctChannelRegistry();
    ReflectionTestUtils.setField(created, "gateEnabled", gateEnabled);
    ReflectionTestUtils.setField(created, "ttlMs", 3900000L);
    ReflectionTestUtils.setField(created, "staleWarnMs", 600000L);
    ReflectionTestUtils.setField(created, "cooldownMs", 60000L);
    ReflectionTestUtils.setField(created, "headroomPercent", 0);
    ReflectionTestUtils.setField(created, "metaRetentionMs", 1800000L);
    ReflectionTestUtils.setField(created, "eventLogSize", 200);
    return created;
  }
}
