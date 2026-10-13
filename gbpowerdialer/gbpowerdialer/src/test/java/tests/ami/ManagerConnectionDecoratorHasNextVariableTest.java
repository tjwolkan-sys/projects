package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
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
import com.powerdialer.engine.db.CampaignDbOperations;
import com.powerdialer.engine.dialer.CampaignRealTimeRingingManager;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.dialer.routing.FctChannelGate;
import com.powerdialer.engine.dialer.routing.FctChannelRegistry;
import com.powerdialer.engine.dialer.routing.RouteDetailsCursor;
import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Server;
import com.powerdialer.redis.CallEventPublisher;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;

/**
 * Faz 2: FCT gate yolundaki her Originate'e {@code HAS_NEXT} değişkeni eklenir.
 *
 * <p>Asterisk {@code [outbound-predictive-fct]} {@code h} extension'ı şu guard'ı kullanır:
 * {@code HAS_NEXT=1} ve DIALSTATUS CONGESTION/CHANUNAVAIL ise yalnız {@code DialerHangup} UserEvent
 * gönderilir; Disconnect, ODBC hangup update, hangup_data.log, EXEC hook ve qos ATLANIR. Değişken
 * boş veya {@code 0} ise eski davranış korunur.
 *
 * <p>Bu yüzden değişkenin YOKLUĞU da sözleşmenin parçasıdır: gate yolu dışında, gate bileşeni
 * yokken ve kill switch kapalıyken değişken HİÇ eklenmemelidir (04.05.2026 davranışı).
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ManagerConnectionDecoratorHasNextVariableTest {

  private static final String HAS_NEXT = "HAS_NEXT";
  private static final String CALL_ID = "dl-has-next-var";
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
    ReflectionTestUtils.invokeMethod(decorator, "setConnections", eventConnection, commandConnection);
  }

  @Test
  public void shouldSendHasNextOneWhenAnotherFctCandidateExists() {
    Call call = fctCall(null);

    OriginateAction action = originate(call);

    assertThat(action.getVariables().get(HAS_NEXT), is("1"));
  }

  @Test
  public void shouldSendHasNextZeroOnLastCandidateWithoutFixedLine() {
    Call call = fctCall(null);
    // Cursor'ı son entry'ye taşı: başka FCT adayı kalmaz.
    call.getRouteDetailsCursor().next();

    OriginateAction action = originate(call);

    assertThat(action.getVariables().get(HAS_NEXT), is("0"));
  }

  @Test
  public void shouldSendHasNextOneWhenFctExhaustedButFixedLineIsConfigured() {
    Call call = fctCall(FIXED_LINE);
    call.getRouteDetailsCursor().next();

    OriginateAction action = originate(call);

    assertThat("sabit hat fallback'i de bir sonraki bacaktır", action.getVariables().get(HAS_NEXT), is("1"));
  }

  @Test
  public void shouldNotSendHasNextOutsideGatePath() {
    Call call = fctCall(FIXED_LINE);
    call.getCampaign().setFctRoutingEnabled(false);

    OriginateAction action = originate(call);

    assertThat(variables(action), is(nullValue()));
  }

  @Test
  public void shouldNotSendHasNextWhenGateComponentIsAbsent() {
    when(connectionComposition.getFctChannelGate()).thenReturn(null);
    Call call = fctCall(FIXED_LINE);

    OriginateAction action = originate(call);

    assertThat(variables(action), is(nullValue()));
  }

  /** Kill switch: HAS_NEXT üretimi ve sabit hat fallback'i birlikte devre dışı kalır. */
  @Test
  public void shouldNotSendHasNextWhenGateIsDisabled() {
    ReflectionTestUtils.setField(registry, "gateEnabled", false);
    Call call = fctCall(FIXED_LINE);

    OriginateAction action = originate(call);

    assertThat("kill switch açıkken Asterisk eski davranışa döner", variables(action), is(nullValue()));
  }

  /**
   * Preview kampanyalar {@code isGatePath} gereği gate kapsamı dışıdır, bu yüzden değişken
   * eklenmez ve Redis'e publish edilen preview payload'ına da girmez.
   *
   * <p>Burada tam originate yolu SÜRÜLMEZ: preview dalı webphone user'ı ve schedule service
   * wiring'i ister; bunlar bu sözleşmeyle ilgisizdir ve testi kırılganlaştırır. Sözleşme noktası
   * doğrudan çağrılır.
   */
  @Test
  public void shouldNotSendHasNextForPreviewCampaign() {
    Call call = fctCall(FIXED_LINE);
    call.getCampaign().setType(Constants.CAMPAIGN_TYPE.PREVIEW_FREE);
    OriginateAction action = new OriginateAction();

    ReflectionTestUtils.invokeMethod(decorator, "applyHasNextVariable", action, call);

    assertThat("preview gate kapsamı dışıdır", variables(action), is(nullValue()));
  }

  /**
   * HAS_NEXT, action KURULDUKTAN SONRA eklenir; mevcut originate değişkenlerini ezmemelidir.
   *
   * <p>Diğer testler mocked factory'den dönen boş bir {@link OriginateAction} kullanır; bu test
   * bilerek GERÇEK builder'ı ({@code AbstractOriginateAction} alt sınıfı) kurar. Böylece
   * "HAS_NEXT eklemesi ORIGINID/PARENTID/DIALER_CALLERID gibi değişkenleri düşürüyor mu" sorusu
   * doğrudan yanıtlanır — canlıda log satırından şüphelenilen senaryo budur.
   */
  @Test
  public void hasNextShouldBeAddedWithoutDroppingExistingOriginateVariables() {
    Call call = fctCall(FIXED_LINE);
    OriginateAction action = new ProgressiveReleasedAction(call);
    Map<String, String> before = new LinkedHashMap<>(action.getVariables());
    assertThat("ön koşul: builder değişkenleri kurmuş olmalı", before.isEmpty(), is(false));

    ReflectionTestUtils.invokeMethod(decorator, "applyHasNextVariable", action, call);

    Map<String, String> after = action.getVariables();
    for (Map.Entry<String, String> entry : before.entrySet()) {
      assertThat("HAS_NEXT eklenmesi mevcut değişkeni düşürmemeli: " + entry.getKey(),
          after.get(entry.getKey()), is(entry.getValue()));
    }
    assertThat(after.get("ORIGINID"), is(CALL_ID));
    assertThat(after.get("PARENTID"), is(CALL_ID));
    assertThat(after.get("DIALER_CALLERID"), is(FCT_A));
    assertThat(after.get(HAS_NEXT), is("1"));
    assertThat("yalnız bir değişken eklenmeli", after.size(), is(before.size() + 1));
  }

  // ---------------------------------------------------------------- yardımcılar

  private OriginateAction originate(Call call) {
    OriginateAction action = new OriginateAction();
    when(managerActionFactory.generateCall(call)).thenReturn(action);
    decorator.originateCall(call);
    return action;
  }

  /** Değişken hiç eklenmemişse map null ya da HAS_NEXT içermeyen bir map olabilir. */
  private String variables(OriginateAction action) {
    return action.getVariables() == null ? null : action.getVariables().get(HAS_NEXT);
  }

  private Call fctCall(String fixedLineCallerId) {
    Campaign campaign = new Campaign();
    campaign.setId(93L);
    campaign.setType(Constants.CAMPAIGN_TYPE.PROGRESSIVE_RELEASED);
    campaign.setCallerIdBehaviour(Constants.CALLER_ID_BEHAVIOUR.POOL);
    campaign.setFctRoutingEnabled(true);
    campaign.setCallerId(fixedLineCallerId);
    campaign.setAttachedDataLongTermSave(Boolean.FALSE);
    // Gerçek AbstractOriginateAction builder'ının okuduğu alanlar (yalnız
    // hasNextShouldBeAddedWithoutDroppingExistingOriginateVariables için gerekli).
    campaign.setName("FCT-HAS-NEXT-TEST");
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
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A), entry(FCT_B))));

    CDR cdr = new CDR();
    cdr.setId(7007L);
    cdr.setInteractionId(CALL_ID);
    cdr.setCallDialTime(new Date(10000L));
    call.setCdr(cdr);
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
