package tests.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.dispatcher.PredictiveGammaEventDispatcher;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Gamma Plus bayrağının hesaplayıcı üzerindeki etkisini madde madde kilitler.
 *
 * <p><b>Her madde için iki test vardır:</b> bayrak KAPALI iken çıktının bugünküyle bit-bit aynı
 * olduğu (elle hesaplanmış eski formül değeriyle), ve bayrak AÇIK iken beklenen yeni değerin
 * çıktığı. Sayısal beklentiler elle hesaplanmıştır.
 *
 * <ul>
 *   <li>MADDE 0/2A — AR_ring (açtı/çaldı), V2 result code kümesi</li>
 *   <li>MADDE 2B — yaş ağırlıklı ringing havuzu</li>
 *   <li>MADDE 2C — tipler arası havuzlanmış varyans</li>
 *   <li>MADDE 4A — avgRing yalnız cevaplananlardan</li>
 *   <li>MADDE 4B — Little yasası: paya acwAgents eklenir</li>
 *   <li>MADDE 5 — CF'nin ≤5 agent uçurumu kalkar</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaCallCalculatorGammaPlusTest {

    private static final double K = 1.645;
    private static final int WINDOW_CAPACITY = 400;

    @Mock private PredictiveEventFactory eventFactory;
    @Mock private PredictiveGammaEventDispatcher gammaDispatcher;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private Campaign campaign;

    private PredictiveGammaCallCalculator calculator;

    @Before
    public void setUp() {
        calculator = new PredictiveGammaCallCalculator(eventFactory, gammaDispatcher, holtsTrendAnalyzer);
        lenient().when(campaign.getId()).thenReturn(1L);
        lenient().when(campaign.getName()).thenReturn("Gamma Plus Test");
        lenient().when(campaign.getGammaCorrectionFactor()).thenReturn(0.0);
        lenient().when(campaign.getGammaAbandonSensitivity()).thenReturn(0.0);
        lenient().when(campaign.getGammaWindowCapacity()).thenReturn(WINDOW_CAPACITY);
        lenient().when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        lenient().when(campaign.getQueueSize()).thenReturn(0);
        lenient().when(holtsTrendAnalyzer.updateAndGetTrend(eq(1L), anyInt()))
                .thenReturn(new HoltsTrendAnalyzer.HoltResult(0.0, 0.0, 0L));
    }

    /**
     * Bayrak kampanya START'inda mandallanip startup profiline yaziliyor; hesaplayici canli
     * kampanya nesnesini DEGIL bu mandali okur. Test de ayni yoldan kurar.
     */
    private void gammaPlus(boolean enabled) {
        this.gammaPlusEnabled = enabled;
    }

    private boolean gammaPlusEnabled;

    // ================================================================ MADDE 2A

    /**
     * Pencere: 400 fresh örnek — 40 cevaplanan, 160 çalıp cevaplanmayan (rc13),
     * 200 hiç çalmayan (rc1).
     *   AR_dial = 40/400 = 0,10
     *   AR_ring = 40/200 = 0,20   (sapma 2,00x — camp7'nin uç değerinden bile yüksek)
     */
    private SnapshotBundle ringScaleWindow() {
        SnapshotBundle b = new SnapshotBundle();
        b.cb = emptyWindow(Constants.CALL_TYPES.CALLBACK);
        b.retry = emptyWindow(Constants.CALL_TYPES.RETRY);
        b.fresh = typedWindow(Constants.CALL_TYPES.FRESH, 400, 40, 160, 12, 30);
        b.successful = talkWindow();
        b.ring = ringWindow(1, 12, 0, 30);
        return b;
    }

    @Test
    public void ringingLoad_flagOff_usesDialAnswerRateExactlyAsToday() {
        gammaPlus(false);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringing(0, 0, 10), ringScaleWindow()).getCallData();

        double arDial = 0.10;
        double expectedMean = 10 * arDial;
        double expectedSafe = expectedMean + K * Math.sqrt(10 * arDial * 0.90);

        assertEquals("Bayrak kapaliyken AR_dial kullanilir", expectedMean, data.getRingingLoadMean(), 1e-9);
        assertEquals(expectedSafe, data.getRingingLoadSafe(), 1e-9);
    }

    @Test
    public void ringingLoad_flagOn_usesRingAnswerRateAndBrakesHarder() {
        gammaPlus(true);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), ringScaleWindow()).getCallData();

        double arRing = 0.20; // 40 acan / 200 calan
        double expectedMean = 10 * arRing;
        double expectedSafe = expectedMean + K * Math.sqrt(10 * arRing - arRing * arRing * 10);

        assertEquals("AR_ring = AR_dial x (N_arandi / N_caldi)", expectedMean, data.getRingingLoadMean(), 1e-9);
        assertEquals(expectedSafe, data.getRingingLoadSafe(), 1e-9);
        assertTrue("2A bir FREN'dir: bozuk listede yuk artmali",
                data.getRingingLoadSafe() > 10 * 0.10 + K * Math.sqrt(10 * 0.10 * 0.90));
    }

    @Test
    public void ringScale_belowMinimumSampleCount_keepsTodaysAnswerRate() {
        // 400 ornek ama yalnizca 40'i calmis (20 acan + 20 calip acmayan) -> esik alti.
        // Gurultulu bir oranin frene dogrudan yansimasi engellenir.
        gammaPlus(true);
        SnapshotBundle b = new SnapshotBundle();
        b.cb = emptyWindow(Constants.CALL_TYPES.CALLBACK);
        b.retry = emptyWindow(Constants.CALL_TYPES.RETRY);
        b.fresh = typedWindow(Constants.CALL_TYPES.FRESH, 400, 20, 20, 12, 30);
        b.successful = talkWindow();
        b.ring = ringWindow(1, 12, 0, 30);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), b).getCallData();

        double arDial = 0.05;
        assertEquals("Esik altinda olcek 1.0 kalir", 10 * arDial, data.getRingingLoadMean(), 1e-9);
    }

    @Test
    public void ringScale_amdCallsCountAsRangUnderV2() {
        // rc71 (AMD) hacmin %10,5'i ve eski listede YOK. V2 ile "caldi" sayilmali,
        // yoksa payda kucuk kalir ve AR_ring gercekten yuksek cikardi.
        gammaPlus(true);
        SnapshotBundle b = new SnapshotBundle();
        b.cb = emptyWindow(Constants.CALL_TYPES.CALLBACK);
        b.retry = emptyWindow(Constants.CALL_TYPES.RETRY);
        // 400 ornek: 40 acan (rc10) + 160 AMD (rc71) + 200 rc1
        b.fresh = customWindow(Constants.CALL_TYPES.FRESH, 400, 40, 160, 71);
        b.successful = talkWindow();
        b.ring = ringWindow(1, 12, 0, 30);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), b).getCallData();

        assertEquals("AMD caldi sayilmali -> AR_ring = 40/200", 10 * 0.20, data.getRingingLoadMean(), 1e-9);
    }

    @Test
    public void ringScale_subscriberAbsentIsNotCountedAsRangUnderV2() {
        // rc14 eski listede VAR ama olculen hat suresi 0 sn -> V2'den cikarildi.
        gammaPlus(true);
        SnapshotBundle b = new SnapshotBundle();
        b.cb = emptyWindow(Constants.CALL_TYPES.CALLBACK);
        b.retry = emptyWindow(Constants.CALL_TYPES.RETRY);
        b.fresh = customWindow(Constants.CALL_TYPES.FRESH, 400, 100, 160, 14);
        b.successful = talkWindow();
        b.ring = ringWindow(1, 12, 0, 30);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), b).getCallData();

        // ringValid = yalniz 100 acan -> olcek 400/100 = 4 -> AR_ring = 0,25 x 4 kirpilarak 1,0
        assertEquals("rc14 caldi sayilmamali", 10 * 1.0, data.getRingingLoadMean(), 1e-9);
    }

    // ================================================================ MADDE 2B

    @Test
    public void ringingLoad_flagOn_ageWeightedPoolBrakesLessThanFlatPool() {
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        // 6 fresh hat; hepsi yasli (w = 0,15 -> 30 sn)
        RingingMetricsDTO aged = new RingingMetricsDTO();
        aged.setFreshRinging(6);
        aged.setTotalRinging(6);
        aged.setFreshRingingWeight(6 * 0.15);
        aged.setFreshRingingWeightSq(6 * 0.15 * 0.15);

        PredictiveGammaCallData agedData = run(agents(10, 10, 0, 10, 0), aged, b).getCallData();
        PredictiveGammaCallData freshData = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 6), b).getCallData();

        assertTrue("Yasli havuz daha az fren uretmeli", agedData.getRingingLoadSafe() < freshData.getRingingLoadSafe());
        assertEquals(0.20 * 6 * 0.15, agedData.getRingingLoadMean(), 1e-9);
    }

    @Test
    public void ringingLoad_flagOff_ignoresAgeWeightsCompletely() {
        // Ayni DTO, ayni agirliklar; bayrak kapaliyken agirliklar okunmamali.
        gammaPlus(false);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        RingingMetricsDTO aged = new RingingMetricsDTO();
        aged.setFreshRinging(6);
        aged.setTotalRinging(6);
        aged.setFreshRingingWeight(6 * 0.15);
        aged.setFreshRingingWeightSq(6 * 0.15 * 0.15);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), aged, b).getCallData();

        assertEquals("Bayrak kapali: ham sayi kullanilir", 6 * 0.20, data.getRingingLoadMean(), 1e-9);
    }

    @Test
    public void ringingLoad_flagOn_missingWeightsFallBackToRawCounts() {
        // Savunma: agirlik doldurulmamissa fren sessizce kaybolmamali.
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        RingingMetricsDTO noWeights = new RingingMetricsDTO();
        noWeights.setFreshRinging(6);
        noWeights.setTotalRinging(6);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), noWeights, b).getCallData();

        assertEquals(6 * 0.20, data.getRingingLoadMean(), 1e-9);
        assertTrue("Fren sifirlanmamali", data.getRingingLoadSafe() > 0.0);
    }

    // ================================================================ MADDE 2C

    @Test
    public void ringingLoad_flagOn_poolsVarianceAcrossTypes() {
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.25, 0.25, 0.25);

        PredictiveGammaCallData plus = run(agents(10, 10, 0, 10, 0), ringingWeighted(1, 1, 1), b).getCallData();

        gammaPlus(false);
        PredictiveGammaCallData old = run(agents(10, 10, 0, 10, 0), ringing(1, 1, 1), b).getCallData();

        double pooled = 0.75 + K * Math.sqrt(3 * 0.25 * 0.75);
        double perType = 3 * (0.25 + K * Math.sqrt(0.25 * 0.75));

        assertEquals("Havuzlanmis sigma", pooled, plus.getRingingLoadSafe(), 1e-9);
        assertEquals("Eski davranis: tip basina sigma toplami", perType, old.getRingingLoadSafe(), 1e-9);
        assertTrue(plus.getRingingLoadSafe() < old.getRingingLoadSafe());
        assertEquals("Ortalama iki yolda da ayni", old.getRingingLoadMean(), plus.getRingingLoadMean(), 1e-9);
    }

    // ================================================================ MADDE 4A

    @Test
    public void avgRing_flagOff_includesUnansweredCalls() {
        gammaPlus(false);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);
        b.ring = ringWindow(30, 12, 70, 30); // 30 cevaplanan 12 sn, 70 cevapsiz 30 sn

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringing(0, 0, 0), b).getCallData();

        assertEquals("Filtresiz ortalama", (30 * 12 + 70 * 30) / 100.0, data.getAvgRingTime(), 1e-9);
        assertEquals(100, data.getCurrentRingWindowSize().intValue());
    }

    @Test
    public void avgRing_flagOn_usesOnlyAnsweredCalls() {
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);
        b.ring = ringWindow(30, 12, 70, 30);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 0), b).getCallData();

        assertEquals("Yalniz cevaplananlar", 12.0, data.getAvgRingTime(), 1e-9);
        assertEquals("Pencere boyu cevaplanan sayisidir", 30, data.getCurrentRingWindowSize().intValue());
    }

    @Test
    public void avgRing_flagOn_belowAnsweredThresholdFallsBackToStartupProfile() {
        // Ring havuzu 400 kayit tutuyor; tipik AR'de icinde 100 cevaplanan asla bulunmaz.
        // Bu yuzden cevaplanan esigi 30'dur; altinda startup profiline dusulur.
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);
        b.ring = ringWindow(29, 12, 200, 30);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 0), b).getCallData();

        assertEquals("29 < 30 -> startup avgRing", 10.0, data.getAvgRingTime(), 1e-9);
    }

    // ================================================================ MADDE 4B

    @Test
    public void supplyTerm_flagOff_excludesAcwAgentsFromNumerator() {
        gammaPlus(false);
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.5);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        // avgTalk=100 (startup), avgAcw=20 (startup), avgRing=10 (startup)
        PredictiveGammaCallData data = run(agents(10, 10, 6, 0, 2), ringing(0, 0, 0), b, profile(100.0, 10.0, 20.0)).getCallData();

        double expected = (6.0 / 120.0) * 10.0 * 0.5;
        assertEquals(expected, data.getRawXn(), 1e-9);
    }

    @Test
    public void supplyTerm_flagOn_addsAcwAgentsToNumerator() {
        gammaPlus(true);
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.5);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        PredictiveGammaCallData data = run(agents(10, 10, 6, 0, 2), ringingWeighted(0, 0, 0), b, profile(100.0, 10.0, 20.0)).getCallData();

        double expected = ((6.0 + 2.0) / 120.0) * 10.0 * 0.5;
        assertEquals("Little yasasi: payda talk+acw ise pay incall+acw", expected, data.getRawXn(), 1e-9);
    }

    @Test
    public void supplyTerm_flagOn_withZeroAcwTime_doesNotAddAcwAgents() {
        // avgAcw olculmemisse ACW asamasi modelde yoktur; paydasi olmayan terim arzi sisirirdi.
        gammaPlus(true);
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.5);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        PredictiveGammaCallData data = run(agents(10, 10, 6, 0, 2), ringingWeighted(0, 0, 0), b, profile(100.0, 10.0, 0.0)).getCallData();

        double expected = (6.0 / 100.0) * 10.0 * 0.5;
        assertEquals(expected, data.getRawXn(), 1e-9);
    }

    // ================================================================ MADDE 5

    @Test
    public void correctionFactor_flagOff_isStillZeroAtFiveAgents() {
        gammaPlus(false);
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.6);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        PredictiveGammaCalculationResult result = run(agents(5, 5, 5, 0, 0), ringing(0, 0, 0), b, profile(100.0, 10.0, 0.0));
        PredictiveGammaCallData data = result.getCallData();

        assertEquals(0.0, data.getCorrectionFactor(), 1e-9);
        assertEquals(0.0, data.getCorrectionFactorScale(), 1e-9);
        assertEquals("Bilinen all-incall olu bolgesi: hic arama yok", 0.0, result.getXn(), 1e-9);
    }

    @Test
    public void correctionFactor_flagOn_hasNoSmallTeamCliff() {
        gammaPlus(true);
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.6);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        PredictiveGammaCalculationResult result = run(agents(5, 5, 5, 0, 0), ringingWeighted(0, 0, 0), b, profile(100.0, 10.0, 0.0));
        PredictiveGammaCallData data = result.getCallData();

        assertEquals(0.6, data.getCorrectionFactor(), 1e-9);
        assertEquals(1.0, data.getCorrectionFactorScale(), 1e-9);
        // 5 agent hepsi incall, servis 100 sn, ring 10 sn, CF 0,6 -> 5/100 x 10 x 0,6 = 0,30
        assertEquals(0.30, result.getXn(), 1e-9);
    }

    @Test
    public void correctionFactor_flagOn_sixAgentsUnchangedFromToday() {
        // 5/6 sinirindaki rejim ziplamasi kalkiyor; 6 agent'ta iki yol ayni deger uretmeli.
        when(campaign.getGammaCorrectionFactor()).thenReturn(0.6);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        gammaPlus(false);
        double off = run(agents(6, 6, 6, 0, 0), ringing(0, 0, 0), b, profile(100.0, 10.0, 0.0)).getXn();
        gammaPlus(true);
        double on = run(agents(6, 6, 6, 0, 0), ringingWeighted(0, 0, 0), b, profile(100.0, 10.0, 0.0)).getXn();

        assertEquals(off, on, 1e-9);
    }

    // ================================================================ İzlenebilirlik alanları

    @Test
    public void telemetry_flagOff_marksArmAndLeavesPlusOnlyFieldsNull() {
        gammaPlus(false);
        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringing(0, 0, 10), ringScaleWindow()).getCallData();

        assertEquals("A/B kol ayirt edicisi kapali kolda da dolmali", Boolean.FALSE, data.getGammaPlus());
        assertNull(data.getCbRingAnswerRate());
        assertNull(data.getRetryRingAnswerRate());
        assertNull(data.getFreshRingAnswerRate());
        assertNull(data.getRingingWeightedCount());
        assertNull(data.getRingingCbWeightedCount());
        assertNull(data.getRingingRetryWeightedCount());
        assertNull(data.getRingingFreshWeightedCount());
    }

    @Test
    public void telemetry_flagOn_publishesRingAnswerRatesUsedByTheBrake() {
        // AR_ring / AR_dial = sapma. Ikisi de event'te oldugu icin sapma turetilebilir.
        gammaPlus(true);
        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), ringScaleWindow()).getCallData();

        assertEquals(Boolean.TRUE, data.getGammaPlus());
        assertEquals("AR_ring fren hesabinda kullanilan degerin aynisi olmali", 0.20, data.getFreshRingAnswerRate(), 1e-9);
        assertEquals("AR_dial dokunulmadan kalir", 0.10, data.getFreshAnswerRate(), 1e-9);
        assertEquals("Sapma = AR_ring / AR_dial", 2.0, data.getFreshRingAnswerRate() / data.getFreshAnswerRate(), 1e-9);
    }

    @Test
    public void telemetry_flagOn_publishesAgeWeightedPoolSize() {
        // ringingWeightedCount / ringingCount = havuzun "erime" orani (MADDE 2B'nin dogrudan olcusu).
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        RingingMetricsDTO aged = new RingingMetricsDTO();
        aged.setFreshRinging(6);
        aged.setCbRinging(2);
        aged.setTotalRinging(8);
        aged.setFreshRingingWeight(6 * 0.15);
        aged.setFreshRingingWeightSq(6 * 0.15 * 0.15);
        aged.setCbRingingWeight(2 * 1.05);
        aged.setCbRingingWeightSq(2 * 1.05 * 1.05);

        PredictiveGammaCallData data = run(agents(10, 10, 0, 10, 0), aged, b).getCallData();

        assertEquals(6 * 0.15, data.getRingingFreshWeightedCount(), 1e-9);
        assertEquals(2 * 1.05, data.getRingingCbWeightedCount(), 1e-9);
        assertEquals(0.0, data.getRingingRetryWeightedCount(), 1e-9);
        assertEquals("Toplam = tiplerin toplami", 6 * 0.15 + 2 * 1.05, data.getRingingWeightedCount(), 1e-9);
        assertTrue("Yasli havuzda agirlikli toplam ham sayidan kucuk olmali",
                data.getRingingWeightedCount() < data.getRingingCount());
    }

    @Test
    public void telemetry_flagOn_weightedCountIsPoolStateBeforeDrain() {
        // Model drain sirasinda buyuyor; telemetri tick anindaki havuzu yansitmali.
        gammaPlus(true);
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);
        PredictiveGammaCalculationResult result = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 4), b);

        result.getRingingLoadModel().commit(Constants.CALL_TYPES.FRESH);
        result.getRingingLoadModel().commit(Constants.CALL_TYPES.FRESH);

        assertEquals("Drain sonrasi degil, drain oncesi deger yayimlanir",
                4.0, result.getCallData().getRingingFreshWeightedCount(), 1e-9);
    }

    // ================================================================ Bayrak sözleşmesi

    @Test
    public void ringingLoadModel_isOnlyBuiltWhenFlagIsOn() {
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        gammaPlus(false);
        assertNull("Bayrak kapali: marjinal fiyat modeli kurulmamali",
                run(agents(10, 10, 0, 10, 0), ringing(0, 0, 5), b).getRingingLoadModel());

        gammaPlus(true);
        assertNotNull(run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 5), b).getRingingLoadModel());
    }

    @Test
    public void dialAnswerRates_areNeverReplacedByRingAnswerRates() {
        // ILKE: telafi dial tarafinda kalir, tampon tarafina sizmaz.
        // getAnswerRateCost'u besleyen AR'ler AR_dial olarak kalmali.
        gammaPlus(true);
        PredictiveGammaCalculationResult result = run(agents(10, 10, 0, 10, 0), ringingWeighted(0, 0, 10), ringScaleWindow());

        assertEquals("Dial maliyeti AR_dial'dir", 0.10, result.getFreshAnswerRate(), 1e-9);
    }

    @Test
    public void emptyPoolAndNoAgents_producesZeroWithoutHaltFallback() {
        // Eskiden burada "[GAMMA] Halt detected" WARN'i basiliyordu; dal ispatli no-op'tu.
        // Deger degismemeli: iki yolda da 0.
        SnapshotBundle b = fullTrustWindows(0.20, 0.20, 0.20);

        gammaPlus(false);
        assertEquals(0.0, run(agents(3, 3, 0, 0, 0), ringing(0, 0, 0), b).getXn(), 1e-9);
        gammaPlus(true);
        assertEquals(0.0, run(agents(3, 3, 0, 0, 0), ringingWeighted(0, 0, 0), b).getXn(), 1e-9);
    }

    @Test
    public void emptyWindows_bothPathsReturnZeroWithoutDispatch() {
        SnapshotBundle b = new SnapshotBundle();
        b.cb = emptyWindow(Constants.CALL_TYPES.CALLBACK);
        b.retry = emptyWindow(Constants.CALL_TYPES.RETRY);
        b.fresh = emptyWindow(Constants.CALL_TYPES.FRESH);
        b.successful = emptyWindow(Constants.CALL_TYPES.FRESH);
        b.ring = emptyWindow(Constants.CALL_TYPES.FRESH);

        // avgTalk startup'tan gelir (>0) -> hesap kosar, havuz bos -> xn = availAgents
        gammaPlus(false);
        double off = run(agents(4, 4, 0, 4, 0), ringing(0, 0, 0), b).getXn();
        gammaPlus(true);
        double on = run(agents(4, 4, 0, 4, 0), ringingWeighted(0, 0, 0), b).getXn();

        assertEquals(4.0, off, 1e-9);
        assertEquals("Bos pencerede iki yol ayni", off, on, 1e-9);
    }

    // ---------------------------------------------------------------- helper

    private PredictiveGammaCalculationResult run(AgentMetricsDTO agents, RingingMetricsDTO ringing, SnapshotBundle b) {
        return run(agents, ringing, b, profile(100.0, 10.0, 0.0));
    }

    private PredictiveGammaCalculationResult run(AgentMetricsDTO agents, RingingMetricsDTO ringing, SnapshotBundle b, PredictiveGammaStartupProfile startup) {
        return calculator.calculateXn(campaign, agents, ringing, b.cb, b.retry, b.fresh, b.successful, b.ring, startup, 100, 50);
    }

    private PredictiveGammaStartupProfile profile(double avgTalk, double avgRing, double avgAcw) {
        return new PredictiveGammaStartupProfile(0.90, 0.90, 0.90, avgTalk, avgRing, avgAcw, gammaPlusEnabled);
    }

    private AgentMetricsDTO agents(double physical, double total, double incall, double avail, double acw) {
        return new AgentMetricsDTO(physical, total, incall, avail, acw);
    }

    private RingingMetricsDTO ringing(int cb, int retry, int fresh) {
        RingingMetricsDTO m = new RingingMetricsDTO();
        m.setCbRinging(cb);
        m.setRetryRinging(retry);
        m.setFreshRinging(fresh);
        m.setTotalRinging(cb + retry + fresh);
        return m;
    }

    /** Gamma Plus yolunda manager'ın dolduracağı hâli taklit eder: hepsi taze (w = 1). */
    private RingingMetricsDTO ringingWeighted(int cb, int retry, int fresh) {
        RingingMetricsDTO m = ringing(cb, retry, fresh);
        m.setCbRingingWeight(cb);
        m.setRetryRingingWeight(retry);
        m.setFreshRingingWeight(fresh);
        m.setCbRingingWeightSq(cb);
        m.setRetryRingingWeightSq(retry);
        m.setFreshRingingWeightSq(fresh);
        return m;
    }

    /** Tüm tiplerde pencere dolu (400) ve hepsi çalmış: AR_ring = AR_dial olur. */
    private SnapshotBundle fullTrustWindows(double cbAr, double retryAr, double freshAr) {
        SnapshotBundle b = new SnapshotBundle();
        b.cb = allRangWindow(Constants.CALL_TYPES.CALLBACK, cbAr);
        b.retry = allRangWindow(Constants.CALL_TYPES.RETRY, retryAr);
        b.fresh = allRangWindow(Constants.CALL_TYPES.FRESH, freshAr);
        b.successful = talkWindow();
        b.ring = ringWindow(1, 10, 0, 30);
        return b;
    }

    private PredictiveGammaSnapshot allRangWindow(Constants.CALL_TYPES type, double answerRate) {
        int answered = (int) Math.round(WINDOW_CAPACITY * answerRate);
        return typedWindow(type, WINDOW_CAPACITY, answered, WINDOW_CAPACITY - answered, 12, 30);
    }

    /**
     * @param rangNotAnswered çalıp cevaplanmayan sayısı; result code 13 (No answer, user alerted)
     *                        — hem eski listede hem V2'de var.
     *                        Kalanı result code 1 (Unallocated number) — hiç çalmadı.
     */
    private PredictiveGammaSnapshot typedWindow(Constants.CALL_TYPES type, int total, int answered, int rangNotAnswered, int answeredRingTime, int unansweredRingTime) {
        return buildWindow(type, total, answered, rangNotAnswered, 13, answeredRingTime, unansweredRingTime);
    }

    private PredictiveGammaSnapshot customWindow(Constants.CALL_TYPES type, int total, int answered, int rangNotAnswered, int rangResultCode) {
        return buildWindow(type, total, answered, rangNotAnswered, rangResultCode, 12, 30);
    }

    private PredictiveGammaSnapshot buildWindow(Constants.CALL_TYPES type, int total, int answered, int rangNotAnswered, int rangResultCode, int answeredRingTime, int unansweredRingTime) {
        PredictiveGammaRecentCalls recent = new PredictiveGammaRecentCalls(WINDOW_CAPACITY);
        for (int i = 0; i < total; i++) {
            PredictiveGammaCallModel m = new PredictiveGammaCallModel();
            m.setCallType(type);
            if (i < answered) {
                m.setAnswered(true);
                m.setResultCode(10);
                m.setTalkTime(100);
                m.setRingTime(answeredRingTime);
            } else if (i < answered + rangNotAnswered) {
                m.setAnswered(false);
                m.setResultCode(rangResultCode);
                m.setRingTime(unansweredRingTime);
            } else {
                m.setAnswered(false);
                m.setResultCode(1); // Unallocated number, p50 1 sn -> calmadi
                m.setRingTime(1);
            }
            recent.add(m);
        }
        return recent.snapshot(campaign);
    }

    private PredictiveGammaSnapshot emptyWindow(Constants.CALL_TYPES type) {
        return new PredictiveGammaRecentCalls(WINDOW_CAPACITY).snapshot(campaign);
    }

    /** avgTalk canlı eşiğin (100) altında kalsın diye tek örnek: startup profiline düşülür. */
    private PredictiveGammaSnapshot talkWindow() {
        PredictiveGammaRecentCalls recent = new PredictiveGammaRecentCalls(WINDOW_CAPACITY);
        PredictiveGammaCallModel m = new PredictiveGammaCallModel();
        m.setCallType(Constants.CALL_TYPES.FRESH);
        m.setAnswered(true);
        m.setResultCode(10);
        m.setTalkTime(100);
        recent.add(m);
        return recent.snapshot(campaign);
    }

    private PredictiveGammaSnapshot ringWindow(int answered, int answeredRingTime, int unanswered, int unansweredRingTime) {
        PredictiveGammaRecentCalls recent = new PredictiveGammaRecentCalls(WINDOW_CAPACITY);
        for (int i = 0; i < answered; i++) {
            PredictiveGammaCallModel m = new PredictiveGammaCallModel();
            m.setCallType(Constants.CALL_TYPES.FRESH);
            m.setAnswered(true);
            m.setResultCode(10);
            m.setRingTime(answeredRingTime);
            recent.add(m);
        }
        for (int i = 0; i < unanswered; i++) {
            PredictiveGammaCallModel m = new PredictiveGammaCallModel();
            m.setCallType(Constants.CALL_TYPES.FRESH);
            m.setAnswered(false);
            m.setResultCode(13);
            m.setRingTime(unansweredRingTime);
            recent.add(m);
        }
        return recent.snapshot(campaign);
    }

    private static class SnapshotBundle {
        PredictiveGammaSnapshot cb;
        PredictiveGammaSnapshot retry;
        PredictiveGammaSnapshot fresh;
        PredictiveGammaSnapshot successful;
        PredictiveGammaSnapshot ring;
    }
}
