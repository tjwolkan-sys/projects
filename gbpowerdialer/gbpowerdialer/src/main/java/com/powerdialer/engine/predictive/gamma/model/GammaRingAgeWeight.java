// com.powerdialer.engine.predictive.gamma.model.GammaRingAgeWeight
package com.powerdialer.engine.predictive.gamma.model;

/**
 * Calan bir cagrinin "hala acilma" ihtimalinin yasa gore agirligi: {@code w(a) = p(a) / p(0)}.
 *
 * <p><b>Neden:</b> Gamma bugun 2 saniyedir calan cagriyi da 28 saniyedir calan cagriyi da ayni
 * {@code answerRate} ile carpiyor. Telefon acma davranisi hafizasiz degil; 6-8 saniyede tepe
 * yapip sonra cokuyor. 30 saniyedir calan cagriya tam AR yazmak "hayali talep" uretir, agent
 * gelmeyecek bir cevap icin rezerve edilir ve bos bekler.
 *
 * <p><b>Sekil global, seviye kampanyaya ozel.</b> {@code p(a) = seviye x w(a)}; seviye mevcut
 * tip bazli AR'den gelir. 1.274.728 cagri uzerinde olculdu: 0-12 sn araliginda 21 kampanya
 * arasi CV &lt;= %10, hicbir kampanyada ters sekil / ikinci tepe / duz egri yok. Bu yuzden
 * kampanya bazli egriye gerek duyulmadi ve tablo sabit tutuldu.
 *
 * <p><b>Performans:</b> runtime'da egri hesabi, regresyon veya matematik kutuphanesi yok;
 * sabit lookup + dogrusal interpolasyon. Cagri basina O(log n) degil, O(1) beklenen maliyet
 * (14 elemanli dizide ileri tarama).
 *
 * <p>Yalniz Gamma Plus yolunda kullanilir.
 */
public final class GammaRingAgeWeight {

    /** Olcum noktalari (saniye), artan sirada. */
    private static final int[] AGE_SEC = {0, 2, 4, 6, 8, 10, 12, 15, 18, 21, 25, 30, 35, 40};

    /** Karsilik gelen w(a) degerleri. */
    private static final double[] WEIGHT = {
            1.00, 1.01, 1.05, 1.06, 1.05, 0.96, 0.86, 0.69, 0.51, 0.37, 0.23, 0.15, 0.10, 0.07
    };

    /** 40 sn ustunde son olculen deger sabit kalir (kuyruk zaten ihmal edilebilir). */
    private static final double TAIL_WEIGHT = WEIGHT[WEIGHT.length - 1];

    private GammaRingAgeWeight() {
    }

    /**
     * Yasa gore agirlik dondurur.
     *
     * @param ageSeconds cagrinin originate anindan bu yana gecen sure (saniye).
     *                   Negatif veya bilinmeyen yas 0 kabul edilir (agirlik 1.0) — bilgi
     *                   yoklugunda fren zayiflatilmaz, guvenli taraf secilir.
     * @return [0.07, 1.06] araliginda agirlik
     */
    public static double weight(double ageSeconds) {
        if (!(ageSeconds > 0.0)) return WEIGHT[0];
        if (ageSeconds >= AGE_SEC[AGE_SEC.length - 1]) return TAIL_WEIGHT;

        for (int i = 1; i < AGE_SEC.length; i++) {
            if (ageSeconds <= AGE_SEC[i]) {
                int lo = i - 1;
                double span = AGE_SEC[i] - AGE_SEC[lo];
                double ratio = (ageSeconds - AGE_SEC[lo]) / span;
                return WEIGHT[lo] + ratio * (WEIGHT[i] - WEIGHT[lo]);
            }
        }
        return TAIL_WEIGHT;
    }
}
