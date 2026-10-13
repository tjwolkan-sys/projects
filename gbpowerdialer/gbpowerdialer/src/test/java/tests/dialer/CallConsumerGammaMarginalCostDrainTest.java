package tests.dialer;

import com.powerdialer.engine.dialer.CallConsumer;
import com.powerdialer.engine.dialer.CallProducer;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.predictive.beta.processor.PredictiveBetaCallManager;
import com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel;
import com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.util.LoggerDecorator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MADDE 3A — çıkan partide güvenlik payı.
 *
 * <p>Bugün aynı risk bulunduğu yere göre farklı fiyatlanıyor: çıkarılırken {@code cost = AR}
 * (varyans payı yok), çalarken {@code ringingLoad} paylı. Havuz boşalınca pay sıfırlanıyor;
 * uç hâli kampanya açılışı — 40 agent'a 40 cevap beklentisiyle ~80 numara.
 *
 * <p>Çözüm yeni bir emniyet katmanı değil, var olanın tutarlı uygulanmasıdır:
 * {@code cost(n) = UCB(R+n+1) − UCB(R+n)}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CallConsumerGammaMarginalCostDrainTest {

  private static final long CID = 1L;
  private static final double K = 1.645;

  @Mock private CampaignContext campaignContext;
  @Mock private DialerFacade dialerFacade;
  @Mock private CallProducer callProducer;
  @Mock private Campaign campaign;
  @Mock private LoggerDecorator loggerDecorator;
  @Mock private LoggerDecorator logger;
  @Mock private PredictiveBetaCallManager predictiveBetaCallManager;
  @Mock private PredictiveGammaCallManager predictiveGammaCallManager;

  private BlockingQueue<Call> dataQueue;
  private CallConsumer callConsumer;

  /** Bayrak kapali yolun FRESH maliyeti (AR_dial). Bazi testler bunu senaryoya gore degistirir. */
  private double freshDialCost = 0.5;

  @Before
  public void setUp() {
    dataQueue = new LinkedBlockingDeque<>();

    when(campaignContext.getCampaign()).thenReturn(campaign);
    when(campaignContext.getCallProducer()).thenReturn(callProducer);
    when(campaignContext.getDialerFacade()).thenReturn(dialerFacade);
    when(callProducer.getDataQueue()).thenReturn(dataQueue);
    when(dialerFacade.getLoggerDecorator()).thenReturn(loggerDecorator);
    when(loggerDecorator.getLogger(CallConsumer.class)).thenReturn(logger);
    lenient().when(dialerFacade.getPredictiveBetaCallManager()).thenReturn(predictiveBetaCallManager);
    when(dialerFacade.getPredictiveGammaCallManager()).thenReturn(predictiveGammaCallManager);
    lenient().when(campaign.getId()).thenReturn(CID);
    when(campaign.getConcurrentLines()).thenReturn(50);

    lenient().when(predictiveGammaCallManager.getAnswerRateCost(any(Call.class), eq(CID))).thenAnswer(inv -> {
      Call call = inv.getArgument(0);
      if (Constants.CALL_TYPES.RETRY.equals(call.getCallType())) return 0.2;
      if (Constants.CALL_TYPES.CALLBACK.equals(call.getCallType())) return 0.8;
      return freshDialCost;
    });

    callConsumer = new CallConsumer(campaignContext);
  }

  // ---------------------------------------------------------------- bayrak kapalı

  @Test
  public void flagOff_nullModel_usesTodaysFlatAnswerRateCost() throws Exception {
    offer(Constants.CALL_TYPES.RETRY, Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.CALLBACK);

    int drained = drain(1.2d, 10, 50, null);

    assertEquals("0,2 + 0,5 + 0,5 = 1,2", 3, drained);
    assertEquals(1, dataQueue.size());
  }

  @Test
  public void flagOff_doesNotConsultMarginalModel() throws Exception {
    offer(Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.FRESH);

    drain(1.0d, 10, 50, null);

    verify(predictiveGammaCallManager, org.mockito.Mockito.atLeastOnce()).getAnswerRateCost(any(Call.class), eq(CID));
  }

  // ---------------------------------------------------------------- bayrak açık

  @Test
  public void flagOn_marginalCost_isUsedInsteadOfFlatAnswerRate() throws Exception {
    // Havuz bos, fresh AR_ring = 0,20.
    // UCB(1)=0,20+1,645*sqrt(0,16)=0,858 ; UCB(2)=0,40+1,645*sqrt(0,32)=1,3305
    // Butce 1,50 -> ilk cagri 0,858, ikinci 0,4725, kalan 0,169 < ucuncu (0,3866) -> 2 cagri
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.20);
    offer(Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.FRESH, Constants.CALL_TYPES.FRESH);

    int drained = drain(1.50d, 10, 50, model);

    assertEquals(2, drained);
    verify(predictiveGammaCallManager, never()).getAnswerRateCost(any(Call.class), eq(CID));
  }

  @Test
  public void flagOn_batchIsSmallerThanFlatPricingWouldAllow() {
    // Ayni butce, ayni AR: marjinal UCB fiyati her zaman duz AR'den pahalidir,
    // yani cikan parti kucuktur. 3A'nin tum amaci bu.
    int flat = drainQuiet(4.0d, null, 20);
    int marginal = drainQuiet(4.0d, emptyPoolModel(0.0, 0.0, 0.50), 20);

    assertTrue("Marjinal fiyat daha az cagri cikarmali (" + marginal + " vs " + flat + ")", marginal < flat);
  }

  @Test
  public void flagOn_campaignOpeningWithFortyAgents_dialsSubstantiallyFewerNumbers() {
    // Kampanya acilisi: 40 bos agent, havuz bos, startup AR sabiti 0,50.
    // Duz fiyatlama 40/0,5 = 80 numara basar, sapma icin hic yer birakmaz.
    // Marjinal UCB ile UCB(B) <= 40 kosulunu saglayan B ~ 66 olur.
    int flat = drainQuiet(40.0d, null, 200);
    int marginal = drainQuiet(40.0d, emptyPoolModel(0.0, 0.0, 0.50), 200);

    assertEquals(80, flat);
    assertTrue("Acilis partisi kuculmeli, ama durmamali", marginal > 50 && marginal < 75);
  }

  // ---------------------------------------------------------------- 3A kalibrasyonu

  @Test
  public void flagOn_singleFreeAgentWithEmptyPool_stillDialsOneCall() {
    // KALIBRASYON: UCB'nin normal yaklasimi N=1'de gecersiz. p=0,50'de ilk cagrinin
    // marjinal maliyeti 1,3225 > 1,0 cikar. Kural olmasa tek bos agent'li kampanya
    // hicbir tick'te arama yapamaz — kalici kilitlenme.
    freshDialCost = 0.50;
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.50);
    assertTrue("On kosul: ilk marjinal maliyet 1'i asiyor",
            model.marginalCost(Constants.CALL_TYPES.FRESH) > 1.0);

    int drained = drainQuiet(1.0d, model, 5);

    assertEquals("Tam olarak 1 cagri; kilitlenme yok, tasma yok", 1, drained);
  }

  @Test
  public void flagOn_budgetBelowMeanCost_dialsNothing() {
    // Taban ortalama fiyattir; onun da altindaysa arama cikmamali.
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.50);

    assertEquals(0, drainQuiet(0.30d, model, 5));
  }

  @Test
  public void flagOn_minimumOneCallRule_appliesOnlyToTheFirstCall() {
    // Ikinci cagri artik tam marjinal fiyati oder; kural yalniz ilk cagri icin gecerli.
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.50);
    // UCB(1) = 1,3225. Butce 1,35 -> ilk cagri gecer, bakiye 0,0275.
    // Ikinci cagrinin marjinal fiyati 0,8407; ortalama tabani 0,50 -> ikisi de karsilanmaz.
    assertEquals(1, drainQuiet(1.35d, model, 5));
  }

  @Test
  public void flagOn_smallTeamAllInCall_getsOneCallInsteadOfNone() {
    // MADDE 5 senaryosu: 5 agent hepsi incall, servis 200 sn, ring 15 sn -> beklenen
    // bosalma 0,375. Makul olan 1 cagri. Marjinal UCB tek basina 0 cagri verirdi
    // (UCB(1) = 0,15 + 1,645*sqrt(0,1275) = 0,737); taban bunu kurtarir.
    freshDialCost = 0.15;
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.15);
    assertTrue("On kosul: marjinal fiyat butcenin uzerinde", model.marginalCost(Constants.CALL_TYPES.FRESH) > 0.375);

    assertEquals(1, drainQuiet(0.375d, model, 5));
  }

  @Test
  public void flagOn_neverDialsFewerThanFlagOffWhenOnlyOneCallFits() {
    // KRITIK INVARYANT: taban AR_dial'dir, AR_ring degil.
    // AR_ring >= AR_dial oldugu icin taban AR_ring secilseydi, butcesi
    // [AR_dial, AR_ring) araliginda kalan kampanyalar bayrak KAPALIYKEN arama yapip
    // bayrak ACIKKEN yapamaz olurdu — gaz maddelerinin amacina tamamen ters.
    // Ustelik starvation makinesi nextCallCost'u AR_dial ile olctugu icin krediyi de
    // sifirlar ve kampanya kalici olarak sifir aramada takilirdi.
    freshDialCost = 0.50;                                   // AR_dial
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.625); // AR_ring = 1,25x sapma
    double budget = 0.55;                                   // AR_dial < butce < AR_ring

    assertTrue("On kosul: butce AR_ring'in altinda", 0.55 < model.marginalMeanCost(Constants.CALL_TYPES.FRESH));

    int flagOff = drainQuiet(budget, null, 5);
    int flagOn = drainQuiet(budget, model, 5);

    assertEquals("Bayrak kapali 1 cagri cikarir", 1, flagOff);
    assertEquals("Bayrak acik da en az ayni sayida cikarmali", 1, flagOn);
  }

  @Test
  public void flagOn_belowFlagOffCostStillDialsNothing() {
    // Taban gevsetilmis olmamali: AR_dial'in de altindaki butce arama uretmez.
    freshDialCost = 0.50;
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.625);

    assertEquals(0, drainQuiet(0.45d, null, 5));
    assertEquals(0, drainQuiet(0.45d, model, 5));
  }

  // ---------------------------------------------------------------- havuz durumu

  @Test
  public void flagOn_alreadyBusyPool_pricesTheNextCallCheaper() {
    // UCB icbukey: havuz doldukca marjinal fiyat duser. Ayni butceyle dolu havuzdan
    // daha cok cagri cikar — vana kademeli acilir, patla/dur salinimi yumusar.
    int emptyPool = drainQuiet(3.0d, emptyPoolModel(0.0, 0.0, 0.20), 40);
    int busyPool = drainQuiet(3.0d, poolModel(0.0, 0.0, 0.20, 0, 0, 25), 40);

    assertTrue("Dolu havuzda marjinal fiyat ucuzlamali (" + busyPool + " vs " + emptyPool + ")",
            busyPool > emptyPool);
  }

  @Test
  public void flagOn_costIsChargedPerCallType() throws Exception {
    // AR_ring: cb 0,80 / retry 0,05. Callback cok daha pahali olmali.
    GammaRingingLoadModel model = emptyPoolModel(0.80, 0.05, 0.20);
    assertTrue(model.marginalCost(Constants.CALL_TYPES.CALLBACK) > model.marginalCost(Constants.CALL_TYPES.RETRY));

    offerN(Constants.CALL_TYPES.RETRY, 40);
    int retryDrained = drain(2.0d, 40, 50, model);

    assertTrue("Ucuz tip cok cagri cikarabilmeli", retryDrained >= 5);
  }

  @Test
  public void flagOn_drainCommitsEachCallIntoTheModel() {
    // Drain sirasinda model havuzun buyudugunu gormeli. UCB icbukey oldugu icin
    // gozlenebilir isaret marjinal fiyatin DUSMESIDIR: pay N ile degil sqrt(N) ile
    // buyur, dolayisiyla cagri basina pay azalir. commit() cagrilmasaydi fiyat
    // sabit kalirdi.
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.20);
    double firstCost = model.marginalCost(Constants.CALL_TYPES.FRESH);

    int drained = drainQuiet(5.0d, model, 10);

    assertTrue("Drain gerceklesmeli", drained > 0);
    assertTrue("Havuz buyudugu icin marjinal fiyat dusmeli",
            model.marginalCost(Constants.CALL_TYPES.FRESH) < firstCost);
  }

  @Test
  public void flagOn_totalChargedEqualsNextTickBrakeIncrease() {
    // 3A'nin sozu: parti icin toplanan pay = UCB(R+B) - UCB(R), yani bir sonraki
    // tick'in freninin gorecegi artisin aynisi. Cifte sayim yok, one alma var.
    GammaRingingLoadModel model = poolModel(0.0, 0.0, 0.20, 0, 0, 5);
    double loadBefore = model.load();
    double budget = 3.0;

    int drained = drainQuiet(budget, model, 30);

    assertTrue(drained > 0);
    double brakeIncrease = model.load() - loadBefore;
    assertTrue("Toplanan pay butceyi asmamali", brakeIncrease <= budget + 1e-9);
    assertTrue("Kalan butce bir sonraki cagriyi karsilayamamali",
            budget - brakeIncrease < model.marginalCost(Constants.CALL_TYPES.FRESH) + 1e-9);
  }

  @Test
  public void flagOn_zeroBudget_dialsNothing() {
    assertEquals(0, drainQuiet(0.0d, emptyPoolModel(0.0, 0.0, 0.20), 10));
  }

  @Test
  public void flagOn_maxCallCountStillCapsTheBatch() {
    // Butce bol olsa da tick basina ust sinir korunmali.
    assertEquals(3, drainQuiet(100.0d, emptyPoolModel(0.0, 0.0, 0.20), 20, 3));
  }

  // ---------------------------------------------------------------- izlenebilirlik (MADDE 3A)

  @Test
  public void telemetry_chargedMarginalCostEqualsBrakeIncrease() {
    // drainMarginalCost, bir sonraki tick'in freninin gorecegi artisin aynisi olmali.
    // Bu sayede "parti neden kucuk cikti" sorusu event'ten cevaplanabilir.
    GammaRingingLoadModel model = poolModel(0.0, 0.0, 0.20, 0, 0, 5);
    double loadBefore = model.load();

    int drained = drainQuiet(3.0d, model, 30);

    assertTrue(drained > 0);
    assertEquals(model.load() - loadBefore, model.getChargedMarginalCost(), 1e-9);
    assertTrue("Bos drain disinda pay pozitif olmali", model.getChargedMarginalCost() > 0.0);
  }

  @Test
  public void telemetry_chargedMarginalCostIsZeroWhenNothingDrained() {
    freshDialCost = 0.50;
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.50);

    assertEquals(0, drainQuiet(0.30d, model, 5));
    assertEquals(0.0, model.getChargedMarginalCost(), 1e-9);
  }

  @Test
  public void telemetry_floorFlagIsSetOnlyWhenTheFirstCallRuleFires() {
    // Bayrak, kampanyanin butce sinirinda calistiginin dogrudan gostergesi:
    // agent bos ama arama neredeyse yok durumunu event'ten ayirt ettirir.
    freshDialCost = 0.50;
    GammaRingingLoadModel triggered = emptyPoolModel(0.0, 0.0, 0.50);
    drainQuiet(1.0d, triggered, 5);            // UCB(1)=1,3225 > 1,0 -> taban devrede
    assertTrue("Taban kurali devreye girdi olarak isaretlenmeli", triggered.isFirstCallFloorApplied());

    GammaRingingLoadModel notTriggered = emptyPoolModel(0.0, 0.0, 0.20);
    drainQuiet(5.0d, notTriggered, 10);        // butce bol -> tam marjinal fiyat odenir
    assertFalse("Bol butcede taban kurali isaretlenmemeli", notTriggered.isFirstCallFloorApplied());
  }

  @Test
  public void telemetry_floorFlagStaysFalseWhenNothingIsDialed() {
    freshDialCost = 0.50;
    GammaRingingLoadModel model = emptyPoolModel(0.0, 0.0, 0.50);

    assertEquals(0, drainQuiet(0.20d, model, 5));
    assertFalse(model.isFirstCallFloorApplied());
  }

  // ---------------------------------------------------------------- helper

  private GammaRingingLoadModel emptyPoolModel(double cbAr, double retryAr, double freshAr) {
    return poolModel(cbAr, retryAr, freshAr, 0, 0, 0);
  }

  private GammaRingingLoadModel poolModel(double cbAr, double retryAr, double freshAr, int cb, int retry, int fresh) {
    return new GammaRingingLoadModel(cbAr, retryAr, freshAr, cb, retry, fresh, cb, retry, fresh, K);
  }

  private void offer(Constants.CALL_TYPES... types) {
    for (Constants.CALL_TYPES t : types) dataQueue.offer(call(t));
  }

  private void offerN(Constants.CALL_TYPES type, int count) {
    for (int i = 0; i < count; i++) dataQueue.offer(call(type));
  }

  private int drain(double budget, int maxCallCount, int concurrentLines, GammaRingingLoadModel model) throws Exception {
    return Whitebox.invokeMethod(callConsumer, "drainWithBudget", budget, maxCallCount, concurrentLines, model);
  }

  /** Fresh çağrılarla dolu bir kuyruk kurup drain eder; sık kullanılan kısayol. */
  private int drainQuiet(double budget, GammaRingingLoadModel model, int available) {
    return drainQuiet(budget, model, available, available);
  }

  private int drainQuiet(double budget, GammaRingingLoadModel model, int available, int maxCallCount) {
    dataQueue.clear();
    callConsumer.getCallQueue().clear();
    offerN(Constants.CALL_TYPES.FRESH, available);
    try {
      return drain(budget, maxCallCount, 500, model);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Call call(Constants.CALL_TYPES callType) {
    Call call = new Call();
    call.setCallType(callType);
    return call;
  }
}
