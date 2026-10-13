package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.action.OriginateAction;
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
import com.powerdialer.engine.asterisk.actions.ManagerActionFactory;
import com.powerdialer.engine.asterisk.actions.ProgressiveReleasedAction;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionComposition;
import com.powerdialer.engine.asterisk.ami.ManagerConnectionDecorator;
import com.powerdialer.engine.asterisk.events.DialerHangupEvent;
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CallOriginator;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
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
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Seçili FCT numarasının trunk'ı Asterisk'e {@code Variable: DIALER_TRUNK} olarak iletilir.
 * Değer QSS'ten gelir ({@code RouteDetailEntry.trunk}), PD yorumlamaz.
 *
 * <p><b>HAS_NEXT'ten farkı</b>: trunk yalnız {@code isGatePath} koşuluna bağlıdır, kill switch'e
 * DEĞİL. {@code fct.channel.gate.enabled=false} kapasite saymayı durdurur ama caller ID yine QSS
 * entry'sinden seçilir; trunk'ı o anda göndermemek kill switch'in bütün FCT çağrılarını varsayılan
 * trunk'a kaydırması demek olurdu. Kill switch kapasite gate'ini geri alır, çağrının doğru
 * trunk'tan çıkmasını değil.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorTrunkVariableTest {

  private static final String DIALER_TRUNK = "DIALER_TRUNK";
  private static final String CALL_ID = "dl-trunk-var";
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
  private CampaignRealTimeRingingManager campaignRealTimeRingingManager;
  @Mock
  private StopConditionChecker stopConditionChecker;
  @Mock
  private CallCleaner callCleaner;
  @Mock
  private CallUpToDateKeeper callUpToDateKeeper;
  @Mock
  private ManagerActionFactory managerActionFactory;
  @Mock
  private RedisUtil redisUtil;
  @Mock
  private ManagerConnection eventConnection;
  @Mock
  private ManagerConnection commandConnection;
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
    gate.setListRetryMax(1);
    gate.setFixedLineRetryMax(1);

    when(loggerDecorator.getLogger(ManagerConnectionDecorator.class)).thenReturn(loggerDecorator);
    when(connectionComposition.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(connectionComposition.getDialerFacade()).thenReturn(dialerFacade);
    when(connectionComposition.getManagerActionFactory()).thenReturn(managerActionFactory);
    when(connectionComposition.getRedisUtil()).thenReturn(redisUtil);
    when(connectionComposition.getStopConditionChecker()).thenReturn(stopConditionChecker);
    when(connectionComposition.getCallCleaner()).thenReturn(callCleaner);
    when(connectionComposition.getCallUpToDateKeeper()).thenReturn(callUpToDateKeeper);
    when(connectionComposition.getFctChannelGate()).thenReturn(gate);
    when(dialerFacade.getCallEventPublisher()).thenReturn(callEventPublisher);
    when(dialerFacade.getCampaignRealTimeRingingManager()).thenReturn(campaignRealTimeRingingManager);

    decorator = new ManagerConnectionDecorator("127.0.0.1", 5038, "user", "password", connectionComposition);
    decorator.setCdrService(cdrService);
    decorator.setServer(server());
    decorator.setDbOperations(dbOperations);
    decorator.setItuToTelephonyResultCodeRelations(telephonyResultCodes());
    ReflectionTestUtils.invokeMethod(decorator, "setConnections", eventConnection, commandConnection);
  }

  @Test
  public void shouldSendTrunkSelectedForTheCallerId() {
    Call call = fctCall();
    call.setFctTrunk("ININ");

    OriginateAction action = originate(call);

    assertThat(action.getVariables().get(DIALER_TRUNK), is("ININ"));
  }

  /** Trunk tanımsızsa değişken HİÇ eklenmez; dialplan varsayılan trunk'a düşer. */
  @Test
  public void shouldNotSendTrunkWhenUndefined() {
    Call call = fctCall();
    call.setFctTrunk(null);

    OriginateAction action = originate(call);

    assertThat(variables(action), is(nullValue()));
  }

  @Test
  public void shouldNotSendTrunkOutsideGatePath() {
    Call call = fctCall();
    call.setFctTrunk("ININ");
    call.getCampaign().setFctRoutingEnabled(false);

    OriginateAction action = originate(call);

    assertThat(variables(action), is(nullValue()));
  }

  @Test
  public void shouldNotSendTrunkWhenGateComponentIsAbsent() {
    when(connectionComposition.getFctChannelGate()).thenReturn(null);
    Call call = fctCall();
    call.setFctTrunk("ININ");

    OriginateAction action = originate(call);

    assertThat(variables(action), is(nullValue()));
  }

  /**
   * Tasarım kararı: kill switch kapasite gate'ini geri alır, trunk seçimini DEĞİL. Aksi hâlde
   * {@code fct.channel.gate.enabled=false} yapmak bütün FCT çağrılarını varsayılan trunk'a
   * kaydırırdı.
   */
  @Test
  public void shouldStillSendTrunkWhenCapacityGateIsDisabled() {
    ReflectionTestUtils.setField(registry, "gateEnabled", false);
    Call call = fctCall();
    call.setFctTrunk("ININ");

    OriginateAction action = originate(call);

    assertThat("kill switch trunk'i susturmamali", action.getVariables().get(DIALER_TRUNK), is("ININ"));
  }

  /** Trunk eklenmesi builder'ın kurduğu mevcut değişkenleri düşürmemeli. */
  @Test
  public void shouldAddTrunkWithoutDroppingExistingOriginateVariables() {
    Call call = fctCall();
    call.setFctTrunk("ININ");
    OriginateAction action = new ProgressiveReleasedAction(call);
    Map<String, String> before = new LinkedHashMap<>(action.getVariables());

    ReflectionTestUtils.invokeMethod(decorator, "applyTrunkVariable", action, call);

    Map<String, String> after = action.getVariables();
    for (Map.Entry<String, String> entry : before.entrySet()) {
      assertThat("mevcut degisken dusmemeli: " + entry.getKey(), after.get(entry.getKey()), is(entry.getValue()));
    }
    assertThat(after.get("DIALER_CALLERID"), is(FCT_A));
    assertThat(after.get(DIALER_TRUNK), is("ININ"));
    assertThat("yalniz bir degisken eklenmeli", after.size(), is(before.size() + 1));
  }

  /**
   * cause 34 sonrası AYNI çağrı yeniden originate edilirken {@code DIALER_TRUNK} de yeniden
   * üretilir ve sıradaki FCT'nin trunk'ını taşır.
   *
   * <p>Bu test originate yolunu GERÇEKTEN sürer (originateCall stub'lanmaz) ve her bacakta kurulan
   * {@code OriginateAction}'ı yakalar. Diğer testler ya tek bacağı ya da yalnız {@code Call}
   * alanını doğruluyordu; re-originate bacağının action'ına değişkenin düştüğü burada kilitlenir.
   */
  @Test
  public void shouldResendUpdatedTrunkOnEachReOriginate() {
    final List<OriginateAction> sent = new ArrayList<>();
    when(managerActionFactory.generateCall(any(Call.class))).thenAnswer(invocation -> {
      OriginateAction action = new OriginateAction();
      sent.add(action);
      return action;
    });

    Call call = reOriginatableCall();
    gate.reserveForOrigination(call);
    assertThat("on kosul: ilk bacak ININ", call.getFctTrunk(), is("ININ"));

    decorator.originateCall(call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event(call.getId()));

    assertThat("iki bacak da originate edilmeli", sent.size(), is(2));
    assertThat(sent.get(0).getVariables().get(DIALER_TRUNK), is("ININ"));
    assertThat("re-originate bacagi siradaki FCT'nin trunk'ini tasimali",
        sent.get(1).getVariables().get(DIALER_TRUNK), is("SOL"));
    // NOT: DIALER_CALLERID'i burada iddia etmiyoruz; generateCall mock'landigi icin builder
    // calismiyor ve o degisken kurulmuyor. Gercek builder kapsami
    // shouldAddTrunkWithoutDroppingExistingOriginateVariables testinde.
    assertThat("caller ID de siradaki FCT'ye gecmis olmali", call.getCallerId(), is(FCT_B));
  }

  // ---------------------------------------------------------------- yardımcılar

  private DialerHangupEvent cause34Event(String callId) {
    DialerHangupEvent event = new DialerHangupEvent(this);
    event.setOriginId(callId);
    event.setHangupCause(String.valueOf(Constants.HANGUP_CAUSE_NO_CIRCUIT_AVAILABLE));
    event.setDateReceived(new Date());
    return event;
  }

  /** cause 34 zincirini sürebilmek için iki entry'li ve sonuç kolonları dolu çağrı. */
  private Call reOriginatableCall() {
    Call call = fctCall();
    call.setId("dl-trunk-reoriginate");
    call.setResultCodeSequence(0);
    call.setCallOriginator(callOriginator);
    call.getCampaign().setColResultRcSequence("rc_sequence");
    call.getCampaign().setColResultUserRc("user_rc");
    call.getCampaign().setColResultTelephonyRc("telephony_rc");
    call.getCampaign().setColResultRetryHistory("retry_history");
    call.getCampaign().setRetryScheduleEnabled(false);
    call.setRouteDetailsCursor(new RouteDetailsCursor(
        Arrays.asList(entry(FCT_A, "ININ"), entry(FCT_B, "SOL"))));
    call.setCallerId(null);
    call.setFctTrunk(null);
    return call;
  }

  private OriginateAction originate(Call call) {
    OriginateAction action = new OriginateAction();
    when(managerActionFactory.generateCall(call)).thenReturn(action);
    decorator.originateCall(call);
    return action;
  }

  private String variables(OriginateAction action) {
    return action.getVariables() == null ? null : action.getVariables().get(DIALER_TRUNK);
  }

  private Call fctCall() {
    Campaign campaign = new Campaign();
    campaign.setId(97L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setAttachedDataLongTermSave(Boolean.FALSE);
    campaign.setName("FCT-TRUNK-TEST");
    campaign.setContext("out-dialer");
    campaign.setRingTimeout(60);
    campaign.setResultCodeTimer(0);

    Call call = new Call();
    call.setId(CALL_ID);
    call.setCampaign(campaign);
    call.setCalledNumber("5551000");
    call.setRawData(new JSONObject().put("phone1", "5551000"));
    call.setAttempts(1);
    call.setCallerId(FCT_A);
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A, "ININ"))));

    CDR cdr = new CDR();
    cdr.setId(9009L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(11000L));
    call.setCdr(cdr);
    return call;
  }

  private RouteDetailEntry entry(String phoneNumber, String trunk) {
    return RouteDetailEntry.newBuilder()
        .setPhoneNumber(phoneNumber)
        .setType("MOBILE")
        .setAttempts("1")
        .setCapacity(1)
        .setTrunk(trunk)
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
