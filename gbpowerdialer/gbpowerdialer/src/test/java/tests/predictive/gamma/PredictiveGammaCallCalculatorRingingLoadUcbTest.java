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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Binomial UCB (Upper Confidence Bound) davranışını ringingLoad hesabı üzerinde kilitler.
 *
 * <p>Formül:
 * <pre>
 *   ringingLoad = N × AR + k × √(N × AR × (1−AR))
 * </pre>
 * Mevcut "sadece ortalama" davranışına `k × σ` güvenlik tamponu ekler. Düşük AR + büyük
 * ringing havuzu senaryolarında eş zamanlı cevap kümelerinin yarattığı rc78 abandon'u
 * azaltmak için tasarlandı.
 *
 * <p>`PredictiveGammaCallCalculator.DEFAULT_RINGING_LOAD_CONFIDENCE_K` üzerinden sabit
 * k = 1.645 (%95 tek-kuyruk güven seviyesi) uygulanır. Faz 2.2'de kampanyalı tunable.
 */
@RunWith(MockitoJUnitRunner.class)
public class PredictiveGammaCallCalculatorRingingLoadUcbTest {

    private static final double K = 1.645;

    @Mock private PredictiveEventFactory eventFactory;
    @Mock private PredictiveGammaEventDispatcher gammaDispatcher;
    @Mock private HoltsTrendAnalyzer holtsTrendAnalyzer;
    @Mock private Campaign campaign;

    private PredictiveGammaCallCalculator calculator;

    @Before
    public void setUp() {
        calculator = new PredictiveGammaCallCalculator(eventFactory, gammaDispatcher, holtsTrendAnalyzer);
        lenient().when(campaign.getId()).thenReturn(1L);
        lenient().when(campaign.getName()).thenReturn("UCB Test");
        lenient().when(campaign.getGammaCorrectionFactor()).thenReturn(0.0);
        lenient().when(campaign.getGammaAbandonSensitivity()).thenReturn(1.0);
        lenient().when(campaign.getGammaWindowCapacity()).thenReturn(400);
        lenient().when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        lenient().when(campaign.getQueueSize()).thenReturn(0);
        lenient().when(holtsTrendAnalyzer.updateAndGetTrend(eq(1L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new HoltsTrendAnalyzer.HoltResult(0.0, 0.0, 0L));
    }

    @Test
    public void ringingLoad_emptyPool_ucbContributesZero() {
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(5.0, 5.0, 0.0),
                ringingMetrics(0, 0, 0),
                fullyTrustedRetryWindow(0.20));

        PredictiveGammaCallData data = result.getCallData();
        assertNotNull(data);
        assertEquals("Bos havuzda mean = 0", 0.0, data.getRingingLoadMean(), 1e-9);
        assertEquals("Bos havuzda safe = 0", 0.0, data.getRingingLoadSafe(), 1e-9);
        assertEquals("ringingLoad = safe", data.getRingingLoadSafe(), data.getRingingLoad(), 1e-9);
        assertEquals("Sabit k aktarilmali", K, data.getRingingLoadConfidenceK(), 1e-9);
    }

    @Test
    public void ringingLoad_retryOnly_ucbAddsVarianceMargin() {
        // 50 retry ringing, AR = 0.10 (pencere doldu, live AR)
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(8.0, 8.0, 0.0),
                ringingMetrics(0, 50, 0),
                fullyTrustedRetryWindow(0.10));

        PredictiveGammaCallData data = result.getCallData();
        double expectedMean = 50 * 0.10;
        double expectedStd  = Math.sqrt(50 * 0.10 * 0.90);
        double expectedSafe = expectedMean + K * expectedStd;

        assertEquals("Mean: N x AR", expectedMean, data.getRingingLoadMean(), 1e-6);
        assertEquals("Safe = mean + k*sigma", expectedSafe, data.getRingingLoadSafe(), 1e-6);
        assertEquals("ringingLoad = safe", data.getRingingLoadSafe(), data.getRingingLoad(), 1e-9);
        assertTrue("Safe mean'in uzerinde olmali", data.getRingingLoadSafe() > data.getRingingLoadMean());
    }

    @Test
    public void ringingLoad_mixedTypes_sumsPerTypeUcb() {
        // 10 cb (AR=0.80), 30 retry (AR=0.05), 5 fresh (AR=0.30) — karisik havuz
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(10.0, 10.0, 0.0),
                ringingMetrics(10, 30, 5),
                customWindows(0.80, 0.05, 0.30));

        PredictiveGammaCallData data = result.getCallData();

        double cbMean    = 10 * 0.80;
        double retryMean = 30 * 0.05;
        double freshMean = 5  * 0.30;
        double expectedMean = cbMean + retryMean + freshMean;

        double cbStd    = Math.sqrt(10 * 0.80 * 0.20);
        double retryStd = Math.sqrt(30 * 0.05 * 0.95);
        double freshStd = Math.sqrt(5  * 0.30 * 0.70);
        double expectedSafe = expectedMean
                + K * cbStd
                + K * retryStd
                + K * freshStd;

        assertEquals("Mixed mean = sum per type", expectedMean, data.getRingingLoadMean(), 1e-6);
        assertEquals("Mixed safe = per type UCB sum", expectedSafe, data.getRingingLoadSafe(), 1e-6);
    }

    @Test
    public void ringingLoad_lowAr_marginalUcb() {
        // SUPERONLINE benzeri: AR ≈ 0.0075 (400 sample, 3 answered), pool = 30
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(8.0, 8.0, 0.0),
                ringingMetrics(0, 30, 0),
                fullyTrustedRetryWindow(0.007));

        PredictiveGammaCallData data = result.getCallData();
        // AR pencerede en yakın round'a düşer; formülü gerçek AR ile doğrula
        double ar = result.getRetryAnswerRate();
        double expectedMean = 30 * ar;
        double expectedStd  = Math.sqrt(30 * ar * (1 - ar));
        double expectedSafe = expectedMean + K * expectedStd;

        assertEquals(expectedMean, data.getRingingLoadMean(), 1e-6);
        assertEquals(expectedSafe, data.getRingingLoadSafe(), 1e-6);
        // Mutlak fark kucuk ama orantisal koruma var
        assertTrue("Ultra dusuk AR'da bile safe mean'in 3x'i uzerinde",
                data.getRingingLoadSafe() > 3 * data.getRingingLoadMean());
    }

    @Test
    public void ringingLoad_highAr_significantUcb() {
        // Callback agırlıklı: AR = 0.80, pool = 5
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(10.0, 10.0, 0.0),
                ringingMetrics(5, 0, 0),
                customWindows(0.80, 0.10, 0.20));

        PredictiveGammaCallData data = result.getCallData();
        double expectedMean = 5 * 0.80;                                  // 4.0
        double expectedStd  = Math.sqrt(5 * 0.80 * 0.20);                // 0.894
        double expectedSafe = expectedMean + K * expectedStd;            // ~5.47

        assertEquals(expectedMean, data.getRingingLoadMean(), 1e-6);
        assertEquals(expectedSafe, data.getRingingLoadSafe(), 1e-6);
    }

    @Test
    public void ringingLoad_arOneOrZero_noVarianceContribution() {
        // AR=0: hicbir cevap beklenmiyor -> sigma=0, safe = 0
        PredictiveGammaCalculationResult zeroAr = invokeCalculator(
                agentMetrics(5.0, 5.0, 0.0),
                ringingMetrics(0, 20, 0),
                fullyTrustedRetryWindow(0.0));

        PredictiveGammaCallData dataZero = zeroAr.getCallData();
        assertEquals(0.0, dataZero.getRingingLoadMean(), 1e-9);
        assertEquals("AR=0 -> sigma=0 -> safe=0", 0.0, dataZero.getRingingLoadSafe(), 1e-9);

        // AR=1: tum ringing cevaplanacak -> sigma=0, safe = N
        PredictiveGammaCalculationResult oneAr = invokeCalculator(
                agentMetrics(5.0, 5.0, 0.0),
                ringingMetrics(0, 20, 0),
                fullyTrustedRetryWindow(1.0));

        PredictiveGammaCallData dataOne = oneAr.getCallData();
        assertEquals(20.0, dataOne.getRingingLoadMean(), 1e-9);
        assertEquals("AR=1 -> sigma=0 -> safe=mean", 20.0, dataOne.getRingingLoadSafe(), 1e-9);
    }

    @Test
    public void xn_safeRingingLoadIsAppliedInSubtraction() {
        // 7 available agent, sensitivity=1.0, abandon=0 -> penalty=1.0
        // Pool = 30 retry, AR = 0.20 -> mean=6, sigma=sqrt(30*0.2*0.8)=2.19
        // Mean only:   xn = 7 - 6 = 1     -> dial 1
        // UCB k=1.645: xn = 7 - (6 + 3.60) = -2.6 -> 0
        PredictiveGammaCalculationResult result = invokeCalculator(
                agentMetrics(7.0, 7.0, 0.0),
                ringingMetrics(0, 30, 0),
                fullyTrustedRetryWindow(0.20));

        PredictiveGammaCallData data = result.getCallData();
        double safe = data.getRingingLoadSafe();
        double rawXn = data.getRawXn();
        double expectedXn = Math.max(0.0, rawXn - safe);

        assertEquals("xn = max(0, rawXn - safe ringingLoad)", expectedXn, result.getXn(), 1e-6);
        assertEquals("Bu senaryo: safe rawXn'i geciyor -> xn=0", 0.0, result.getXn(), 1e-6);
    }

    // ---------- helper'lar ----------

    private PredictiveGammaCalculationResult invokeCalculator(AgentMetricsDTO agents,
                                                              RingingMetricsDTO ringing,
                                                              SnapshotBundle snapshots) {
        PredictiveGammaStartupProfile startup = new PredictiveGammaStartupProfile(
                0.90, 0.90, 0.90, 100.0, 10.0, 0.0);

        return calculator.calculateXn(
                campaign,
                agents,
                ringing,
                snapshots.cb,
                snapshots.retry,
                snapshots.fresh,
                snapshots.successful,
                snapshots.ring,
                startup,
                100,
                50);
    }

    private AgentMetricsDTO agentMetrics(double available, double total, double inCall) {
        return new AgentMetricsDTO(total, total, inCall, available, 0.0);
    }

    private RingingMetricsDTO ringingMetrics(int cb, int retry, int fresh) {
        RingingMetricsDTO m = new RingingMetricsDTO();
        m.setCbRinging(cb);
        m.setRetryRinging(retry);
        m.setFreshRinging(fresh);
        m.setTotalRinging(cb + retry + fresh);
        return m;
    }

    private SnapshotBundle fullyTrustedRetryWindow(double retryAr) {
        return customWindows(0.90, retryAr, 0.90);
    }

    private SnapshotBundle customWindows(double cbAr, double retryAr, double freshAr) {
        SnapshotBundle bundle = new SnapshotBundle();
        // 400 sample pencere dolu, blend weight = 1.0 -> live AR aktif
        bundle.cb        = window(Constants.CALL_TYPES.CALLBACK, 400, cbAr);
        bundle.retry     = window(Constants.CALL_TYPES.RETRY,    400, retryAr);
        bundle.fresh     = window(Constants.CALL_TYPES.FRESH,    400, freshAr);
        bundle.successful = window(Constants.CALL_TYPES.FRESH, 1, 1.0); // avgTalk > 0 icin
        bundle.ring       = window(Constants.CALL_TYPES.FRESH, 1, 1.0);
        return bundle;
    }

    private PredictiveGammaSnapshot window(Constants.CALL_TYPES type, int total, double answerRate) {
        PredictiveGammaRecentCalls recent = new PredictiveGammaRecentCalls(400);
        int answered = (int) Math.round(total * answerRate);
        for (int i = 0; i < total; i++) {
            PredictiveGammaCallModel call = new PredictiveGammaCallModel();
            call.setCallType(type);
            call.setAnswered(i < answered);
            call.setTalkTime(i < answered ? 100 : 0);
            call.setRingTime(10);
            recent.add(call);
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
