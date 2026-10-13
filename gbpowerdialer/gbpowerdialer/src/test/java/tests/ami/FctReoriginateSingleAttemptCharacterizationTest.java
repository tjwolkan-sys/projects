package tests.ami;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
import com.powerdialer.util.Utils;

/**
 * Faz 2 karakterizasyonu: cause 34 zinciri boyunca N bacak TEK attempt olarak kalmalıdır.
 *
 * <p>Bu sınıf Faz 2 davranış değişikliğinden ÖNCE mevcut davranışı kilitler. İki grup vardır:
 *
 * <ul>
 *   <li><b>Korunacak davranış</b>: attempt sayacı, ilk bacağın {@code callDialTime}'ı ve
 *       cursor ilerleyişi. Faz 2 bunlara dokunmaz; testler değişmeden yeşil kalmalıdır.</li>
 *   <li><b>Düzeltilen defekt</b>: {@code originationDate}. Faz 2 öncesi bu sınıf alanın
 *       kaybolduğunu belgeliyordu; düzeltme sonrası beklentiler korunacak davranışa çevrildi
 *       ({@code shouldKeepFirstLegOriginationDateOnReOriginate} ve
 *       {@code shouldMeasureDurationFromFirstOriginationAfterReOriginate}).</li>
 * </ul>
 *
 * <p><b>originationDate defekti (Faz 2'de düzeltildi)</b>: alanın tek yazarı
 * {@code CallOriginator.processOrigination}'dır. Re-originate yolu {@code originateCall}'ı
 * DOĞRUDAN çağırır, {@code processOrigination}'dan geçmez; bu yüzden
 * {@code tryFctRetryWithNextCallerId} içindeki {@code setOriginationDate(null)} satırından sonra
 * alan bir daha DOLDURULMUYORDU. Sonuç: {@code Call.getDurationFromOrigination()} null
 * {@code originationDate} gördüğünde hiç güncellenmemiş alanı (0) döndürüyor; bu da
 * {@code Campaign.incrementTotalCallDuration(0)} ve interaction-notifier payload'ında
 * {@code durationFromOrigination = 0} demekti. Yani süre "son bacaktan" değil, HİÇ ölçülmüyordu —
 * bu yüzden düzeltme bir raporlama değişikliği değil, bugfix'tir.
 *
 * <p><b>Kapsam dışı (bilinçli, ayrı iş)</b>: {@code handleCallCreation.insertCallResultToDB}
 * re-originate bacağında tekrar çalışır (VarSet başına bir kez, aynı satıra upsert). Faz 2 bunu
 * değiştirmez; bu test VarSet akışını sürmediği için sayıyı iddia etmez, davranışın bilinçli
 * bırakıldığı burada kayda geçer.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class FctReoriginateSingleAttemptCharacterizationTest {

  private static final String CALL_ID = "dl-fct-single-attempt";
  private static final String FCT_A = "05550000401";
  private static final String FCT_B = "05550000402";
  private static final String FCT_C = "05550000403";

  /** İlk originate'in üzerinden geçtiği varsayılan süre; korunursa duration > 0 olmalı. */
  private static final long FIRST_LEG_AGE_MS = 5000L;

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
    // Zincirin 3 bacağa ulaşabilmesi için in-call retry'a izin verilir.
    gate.setListRetryMax(2);

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

  /**
   * KORUNACAK: üç bacaklı cause 34 zinciri tek attempt olarak kalır. Attempt artışı yalnız
   * {@code CallOriginator.pollCallsFromConsumerAndOriginate} içindeki
   * {@code CallAttemptPolicy.incrementForOrigination}'dadır; ne {@code originateCall} ne de
   * {@code tryFctRetryWithNextCallerId} attempt'e dokunur.
   */
  @Test
  public void shouldKeepThreeLegChainAsSingleAttempt() {
    Call call = fctCall();
    int attemptsBefore = call.getAttempts();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());
    decorator.getCalls().put(call.getId(), call);
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    verify(decorator, times(2)).originateCall(call);
    assertThat("3 bacak tek attempt olmalı", call.getAttempts(), is(attemptsBefore));
    assertThat(call.getFctListRetryCount(), is(2));
    assertThat("cursor üçüncü entry'de olmalı", call.getCallerId(), is(FCT_C));
  }

  /**
   * KORUNACAK: ilk bacağın {@code callDialTime}'ı zincir boyunca korunur. Bu değer initial CDR'a
   * {@code CallOriginator.processOrigination} içinde bir kez yazılır; re-originate onu ezmez.
   */
  @Test
  public void shouldKeepFirstLegCallDialTimeAcrossChain() {
    Call call = fctCall();
    Date firstDialTime = call.getCdr().getCallDialTime();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());
    decorator.getCalls().put(call.getId(), call);
    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("initial CDR dial time ilk bacağın olmalı", call.getCdr().getCallDialTime(), is(firstDialTime));
  }

  /**
   * MEVCUT DEFEKT (Faz 2'de düzeltilecek): re-originate sonrası {@code originationDate} null kalır
   * ve {@code durationFromOrigination} 0 ölçülür.
   *
   * <p>Faz 2'de {@code setOriginationDate(null)} satırı kaldırıldığında bu testin beklentisi
   * tersine çevrilir: alan ilk bacağın değerini korumalı ve duration > 0 olmalıdır.
   */
  @Test
  public void shouldKeepFirstLegOriginationDateOnReOriginate() {
    Call call = fctCall();
    Double firstOrigination = call.getOriginationDate();
    assertThat("ön koşul: ilk originate tarihi dolu", firstOrigination, is(notNullValue()));
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertThat("Faz 2: originationDate ilk bacaktan korunur", call.getOriginationDate(), is(firstOrigination));
  }

  /**
   * Faz 2 düzeltmesinin asıl kazancı: süre gerçekten ölçülebilir hâle gelir.
   *
   * <p>Eskiden {@code originationDate} null'landığı için {@code getDurationFromOrigination()} hiç
   * güncellenmeyen alanı döndürüyordu (getter yalnız {@code originationDate != null} iken hesaplar).
   * Ara bacakta standart sonuç akışı çalışmadığından getter hiç çağrılmıyor ve alan 0'da donuyordu;
   * bu da {@code Campaign.incrementTotalCallDuration(0)} ve payload'da
   * {@code durationFromOrigination = 0} demekti.
   */
  @Test
  public void shouldMeasureDurationFromFirstOriginationAfterReOriginate() {
    Call call = fctCall();
    decorator.getCalls().put(call.getId(), call);
    call.getCampaign().addCallInProgress(call);

    ReflectionTestUtils.invokeMethod(decorator, "handleDialerHangup", cause34Event());

    assertTrue("Faz 2: süre ilk originate'ten itibaren ölçülür, 0'da donmaz",
        call.getDurationFromOrigination() >= FIRST_LEG_AGE_MS);
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
    campaign.setId(91L);
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
    call.setAttempts(1);
    call.setRouteDetailsCursor(new RouteDetailsCursor(Arrays.asList(entry(FCT_A), entry(FCT_B), entry(FCT_C))));
    call.setCallOriginator(callOriginator);

    // processOrigination'ın yaptığı iki yazım simüle edilir: originationDate + initial CDR dial time.
    Date firstOrigination = new Date(System.currentTimeMillis() - FIRST_LEG_AGE_MS);
    call.setOriginationDate(Utils.convertDateToDouble(firstOrigination));

    CDR cdr = new CDR();
    cdr.setId(5005L);
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
