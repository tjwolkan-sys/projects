package tests.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel;
import com.powerdialer.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MADDE 2B + 2C + 3A — Gamma Plus borç modelinin matematiği.
 *
 * <p>Beklenen değerlerin tamamı elle hesaplanmıştır; "çalışıyor" testi değildir.
 */
public class GammaRingingLoadModelTest {

    private static final double K = 1.645;
    private static final double EPS = 1e-9;

    /** Yaş ağırlığı olmayan (hepsi taze, w=1) havuz kurar: Σw = Σw² = N. */
    private GammaRingingLoadModel unweighted(double cbAr, double retryAr, double freshAr, int cb, int retry, int fresh) {
        return new GammaRingingLoadModel(cbAr, retryAr, freshAr, cb, retry, fresh, cb, retry, fresh, K);
    }

    // ---------------------------------------------------------------- 2C

    @Test
    public void load_singleType_matchesClassicBinomialUcb() {
        // Tek tip varken havuzlanmış varyans eski formülle birebir aynı sonucu vermeli.
        GammaRingingLoadModel model = unweighted(0.0, 0.10, 0.0, 0, 50, 0);

        double expectedMean = 50 * 0.10;
        double expectedStd = Math.sqrt(50 * 0.10 * 0.90);

        assertEquals(expectedMean, model.mean(), 1e-9);
        assertEquals(expectedMean + K * expectedStd, model.load(), 1e-9);
    }

    @Test
    public void load_mixedTypes_poolsVarianceInsteadOfSummingSigma() {
        // Belgedeki örnek: 3 telefon çalıyor (1 cb, 1 retry, 1 fresh), her biri %25.
        //   Bugün : 3 x (0,25 + 1,645 x 0,433) = 2,89   <- fazla fren
        //   Doğru : 0,75 + 1,645 x 0,75        = 1,98
        GammaRingingLoadModel model = unweighted(0.25, 0.25, 0.25, 1, 1, 1);

        double perTypeStd = Math.sqrt(1 * 0.25 * 0.75);
        double oldWay = 3 * (0.25 + K * perTypeStd);
        double pooled = 0.75 + K * Math.sqrt(3 * 0.25 * 0.75);

        assertEquals(0.75, model.mean(), 1e-9);
        assertEquals(pooled, model.load(), 1e-9);
        assertEquals(2.89, oldWay, 0.01);
        assertEquals(1.98, pooled, 0.01);
        assertTrue("Havuzlanmis varyans eski toplamdan kucuk olmali", pooled < oldWay);
    }

    @Test
    public void load_singleDominantType_poolingGainIsZero() {
        // Tek tip baskınsa 2C kazancı yoktur; bu bilinçli ve belgede yazılı.
        GammaRingingLoadModel model = unweighted(0.0, 0.0, 0.20, 0, 0, 40);
        double classic = 40 * 0.20 + K * Math.sqrt(40 * 0.20 * 0.80);
        assertEquals(classic, model.load(), 1e-9);
    }

    // ---------------------------------------------------------------- 2B

    @Test
    public void load_ageWeighted_isLowerThanFlatPool() {
        // 6 fresh hat çalıyor: 4'ü yaşlı (w=0,265), 2'si taze (w=1,055).
        double sw = 4 * 0.265 + 2 * 1.055;
        double sw2 = 4 * 0.265 * 0.265 + 2 * 1.055 * 1.055;
        GammaRingingLoadModel weighted = new GammaRingingLoadModel(0, 0, 0.25, 0, 0, sw, 0, 0, sw2, K);
        GammaRingingLoadModel flat = unweighted(0, 0, 0.25, 0, 0, 6);

        assertEquals(0.25 * sw, weighted.mean(), 1e-9);
        assertTrue("Yas agirligi ortalamayi dusurmeli", weighted.mean() < flat.mean());
        assertTrue("Yas agirligi toplam freni dusurmeli", weighted.load() < flat.load());
    }

    @Test
    public void variance_usesWeightSquares_notWeightSumTwice() {
        // Var = AR·Σw − AR²·Σw²  (Poisson-binom). Σw² ayrı taşınmazsa varyans yanlış çıkar.
        double ar = 0.30;
        double sw = 3.0;
        double sw2 = 2.4;
        GammaRingingLoadModel model = new GammaRingingLoadModel(0, 0, ar, 0, 0, sw, 0, 0, sw2, K);

        assertEquals(ar * sw - ar * ar * sw2, model.variance(), 1e-12);
    }

    // ---------------------------------------------------------------- kenar durumlar

    @Test
    public void load_emptyPool_isZero() {
        GammaRingingLoadModel model = unweighted(0.5, 0.5, 0.5, 0, 0, 0);
        assertEquals(0.0, model.mean(), EPS);
        assertEquals(0.0, model.variance(), EPS);
        assertEquals(0.0, model.load(), EPS);
    }

    @Test
    public void load_answerRateZero_hasNoLoadAndNoVariance() {
        GammaRingingLoadModel model = unweighted(0, 0, 0.0, 0, 0, 30);
        assertEquals(0.0, model.mean(), EPS);
        assertEquals(0.0, model.load(), EPS);
    }

    @Test
    public void load_answerRateOne_hasNoVariance() {
        GammaRingingLoadModel model = unweighted(0, 0, 1.0, 0, 0, 20);
        assertEquals(20.0, model.mean(), EPS);
        assertEquals(0.0, model.variance(), EPS);
        assertEquals(20.0, model.load(), EPS);
    }

    @Test
    public void load_answerRateAboveOne_isClampedAndVarianceStaysNonNegative() {
        GammaRingingLoadModel model = unweighted(0, 0, 1.7, 0, 0, 10);
        assertEquals(10.0, model.mean(), EPS);
        assertTrue("Varyans negatife dusmemeli", model.variance() >= 0.0);
        assertEquals(10.0, model.load(), EPS);
    }

    @Test
    public void load_nonFiniteInputs_areNeutralised() {
        GammaRingingLoadModel model = new GammaRingingLoadModel(
                Double.NaN, Double.NEGATIVE_INFINITY, 0.2,
                Double.NaN, -3.0, 10.0,
                Double.NaN, -3.0, 10.0, K);
        assertTrue(Double.isFinite(model.load()));
        assertEquals(10 * 0.2 + K * Math.sqrt(10 * 0.2 * 0.8), model.load(), 1e-9);
    }

    // ---------------------------------------------------------------- 3A

    @Test
    public void marginalCost_firstCallOnEmptyPool_equalsUcbOfOne() {
        GammaRingingLoadModel model = unweighted(0, 0, 0.50, 0, 0, 0);
        double expected = 0.50 + K * Math.sqrt(0.50 * 0.50);

        assertEquals(expected, model.marginalCost(Constants.CALL_TYPES.FRESH), 1e-9);
        // Belgedeki uyarı: p=0,5'te ilk çağrının marjinal maliyeti 1,32 > 1,0.
        assertEquals(1.3225, model.marginalCost(Constants.CALL_TYPES.FRESH), 1e-4);
    }

    @Test
    public void marginalCost_isDecreasingAsPoolGrows() {
        GammaRingingLoadModel model = unweighted(0, 0, 0.50, 0, 0, 0);
        double first = model.marginalCost(Constants.CALL_TYPES.FRESH);
        model.commit(Constants.CALL_TYPES.FRESH);
        double second = model.marginalCost(Constants.CALL_TYPES.FRESH);
        model.commit(Constants.CALL_TYPES.FRESH);
        double third = model.marginalCost(Constants.CALL_TYPES.FRESH);

        assertTrue(first > second);
        assertTrue(second > third);
    }

    @Test
    public void marginalCosts_telescopeToBatchLoadDelta() {
        // 3A'nın çekirdek iddiası: parti için toplanan pay = UCB(R+B) - UCB(R).
        // Yani bir sonraki tick'in freninin göreceğinin aynısı; çifte sayım yok.
        GammaRingingLoadModel model = unweighted(0, 0, 0.20, 0, 0, 7);
        double before = model.load();

        double collected = 0.0;
        for (int i = 0; i < 5; i++) {
            collected += model.marginalCost(Constants.CALL_TYPES.FRESH);
            model.commit(Constants.CALL_TYPES.FRESH);
        }

        assertEquals(model.load() - before, collected, 1e-9);
    }

    @Test
    public void marginalCost_doesNotMutateModel() {
        GammaRingingLoadModel model = unweighted(0.4, 0.3, 0.2, 2, 3, 4);
        double before = model.load();
        model.marginalCost(Constants.CALL_TYPES.CALLBACK);
        model.marginalCost(Constants.CALL_TYPES.RETRY);
        model.marginalCost(Constants.CALL_TYPES.FRESH);
        assertEquals("marginalCost salt okunur olmali", before, model.load(), 1e-12);
    }

    @Test
    public void marginalCost_isTypeSpecific() {
        GammaRingingLoadModel model = unweighted(0.80, 0.05, 0.20, 1, 1, 1);
        double cb = model.marginalCost(Constants.CALL_TYPES.CALLBACK);
        double retry = model.marginalCost(Constants.CALL_TYPES.RETRY);
        assertTrue("Yuksek AR'li callback daha pahali olmali", cb > retry);
    }

    @Test
    public void marginalCost_nullType_fallsBackToFresh() {
        GammaRingingLoadModel model = unweighted(0.80, 0.05, 0.20, 0, 0, 0);
        assertEquals(model.marginalCost(Constants.CALL_TYPES.FRESH), model.marginalCost(null), 1e-12);
    }

    @Test
    public void marginalMeanCost_isTheRingAnswerRate() {
        GammaRingingLoadModel model = unweighted(0.80, 0.05, 0.20, 3, 3, 3);
        assertEquals(0.80, model.marginalMeanCost(Constants.CALL_TYPES.CALLBACK), EPS);
        assertEquals(0.05, model.marginalMeanCost(Constants.CALL_TYPES.RETRY), EPS);
        assertEquals(0.20, model.marginalMeanCost(Constants.CALL_TYPES.FRESH), EPS);
    }

    @Test
    public void baseLoad_isCapturedAtConstructionAndSurvivesCommits() {
        GammaRingingLoadModel model = unweighted(0, 0, 0.20, 0, 0, 7);
        double base = model.getBaseLoad();
        model.commit(Constants.CALL_TYPES.FRESH);
        assertEquals(base, model.getBaseLoad(), 1e-12);
        assertTrue(model.load() > base);
    }
}
