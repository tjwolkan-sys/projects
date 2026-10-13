package tests.predictive.gamma;

import com.powerdialer.engine.predictive.gamma.model.GammaRingAgeWeight;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * MADDE 2B — çalan çağrının yaşına göre "hâlâ açılma" ağırlığı w(a) = p(a)/p(0).
 *
 * <p>Tablo 1.274.728 çağrı üzerinde ölçüldü. Bu test tabloyu ve interpolasyon davranışını
 * kilitler; eğri şekli değişirse fren gücü değişir, o yüzden sayısal beklentiler elle yazıldı.
 */
public class GammaRingAgeWeightTest {

    private static final double EPS = 1e-9;

    @Test
    public void weight_atMeasuredPoints_matchesTable() {
        assertEquals(1.00, GammaRingAgeWeight.weight(0), EPS);
        assertEquals(1.01, GammaRingAgeWeight.weight(2), EPS);
        assertEquals(1.05, GammaRingAgeWeight.weight(4), EPS);
        assertEquals(1.06, GammaRingAgeWeight.weight(6), EPS);
        assertEquals(1.05, GammaRingAgeWeight.weight(8), EPS);
        assertEquals(0.96, GammaRingAgeWeight.weight(10), EPS);
        assertEquals(0.86, GammaRingAgeWeight.weight(12), EPS);
        assertEquals(0.69, GammaRingAgeWeight.weight(15), EPS);
        assertEquals(0.51, GammaRingAgeWeight.weight(18), EPS);
        assertEquals(0.37, GammaRingAgeWeight.weight(21), EPS);
        assertEquals(0.23, GammaRingAgeWeight.weight(25), EPS);
        assertEquals(0.15, GammaRingAgeWeight.weight(30), EPS);
        assertEquals(0.10, GammaRingAgeWeight.weight(35), EPS);
        assertEquals(0.07, GammaRingAgeWeight.weight(40), EPS);
    }

    @Test
    public void weight_betweenPoints_interpolatesLinearly() {
        // 0-2 sn aralığının ortası: (1.00 + 1.01) / 2
        assertEquals(1.005, GammaRingAgeWeight.weight(1), 1e-12);
        // 21-25 aralığında 23 sn: 0.37 + 0.5 * (0.23 - 0.37)
        assertEquals(0.30, GammaRingAgeWeight.weight(23), 1e-12);
        // 12-15 aralığında 13 sn: 0.86 + (1/3) * (0.69 - 0.86)
        assertEquals(0.86 + (1.0 / 3.0) * (0.69 - 0.86), GammaRingAgeWeight.weight(13), 1e-12);
    }

    @Test
    public void weight_negativeOrZeroAge_isFullWeight() {
        // Yaş bilinmiyorsa fren zayıflatılmaz.
        assertEquals(1.00, GammaRingAgeWeight.weight(0.0), EPS);
        assertEquals(1.00, GammaRingAgeWeight.weight(-5.0), EPS);
    }

    @Test
    public void weight_beyondTable_staysAtTailValue() {
        assertEquals(0.07, GammaRingAgeWeight.weight(41), EPS);
        assertEquals(0.07, GammaRingAgeWeight.weight(120), EPS);
        assertEquals(0.07, GammaRingAgeWeight.weight(100000), EPS);
    }

    @Test
    public void weight_overCountingRatio_matchesMeasuredSixPointSeven() {
        // Belgedeki fazla-sayma katı: p(0)/p(30) = 6,7x
        double ratio = GammaRingAgeWeight.weight(0) / GammaRingAgeWeight.weight(30);
        assertEquals(6.67, ratio, 0.01);
    }

    @Test
    public void weight_isMonotonicallyDecreasingAfterPeak() {
        // Tepe 6 sn'de; sonrası kesin azalan olmalı, yoksa yaşlı çağrı taze çağrıdan
        // daha çok fren üretir ve maddenin amacı tersine döner.
        double prev = GammaRingAgeWeight.weight(6);
        for (double a = 7; a <= 60; a += 0.5) {
            double w = GammaRingAgeWeight.weight(a);
            assertTrue("w(" + a + ")=" + w + " > w(onceki)=" + prev, w <= prev + EPS);
            prev = w;
        }
    }

    @Test
    public void weight_neverExceedsPeakOrDropsBelowTail() {
        for (double a = -10; a <= 200; a += 0.25) {
            double w = GammaRingAgeWeight.weight(a);
            assertTrue("w=" + w + " ust sinir asildi", w <= 1.06 + EPS);
            assertTrue("w=" + w + " alt sinir asildi", w >= 0.07 - EPS);
        }
    }
}
