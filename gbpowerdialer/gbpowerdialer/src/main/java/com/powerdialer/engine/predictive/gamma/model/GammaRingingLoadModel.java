// com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel
package com.powerdialer.engine.predictive.gamma.model;

import com.powerdialer.util.Constants;

/**
 * Gamma Plus'ın borç (ringingLoad) tarafı. MADDE 2A + 2B + 2C tek formülde toplanır ve
 * MADDE 3A aynı formülün marjinal türevinden beslenir — tek matematik, tek kaynak.
 *
 * <pre>
 *   p_i          = AR_ring(tip) × w(yaş_i)              her çalan hat için ayrı olasılık
 *   mean         = Σ p_i        = Σ_tip AR × Σw
 *   Var          = Σ p_i(1−p_i) = Σ_tip (AR × Σw − AR² × Σw²)     Poisson-binom
 *   ringingLoad  = mean + k × √Var
 * </pre>
 *
 * <p><b>MADDE 2A</b> — {@code AR_ring} (açtı/çaldı) kullanılır, {@code AR_dial} (açtı/arandı)
 * değil. Çalan telefon "hat kuruldu" sınavını zaten geçmiştir; sistemsel/hat kaynaklı kayıp
 * dial tarafında telafi edilmeye devam eder, tampon tarafına sızmaz.
 *
 * <p><b>MADDE 2B</b> — {@code Σw} ve {@code Σw²} yaş ağırlıklı gelir
 * ({@link GammaRingAgeWeight}); 30 saniyedir çalan hat artık tam AR ile fren üretmez.
 *
 * <p><b>MADDE 2C</b> — varyans tipler arasında TOPLANIR, sapmalar değil
 * ({@code √a+√b+√c > √(a+b+c)}). Eski kod tip başına ayrı {@code mean + kσ} hesaplayıp
 * topluyordu; σ terimi en fazla {@code √3 = 1,73×} şişiyordu.
 *
 * <p><b>MADDE 3A</b> — {@link #marginalCost} bu turda çıkarılacak çağrının freni bir sonraki
 * tick'te ne kadar artıracağını verir: {@code L(R+n+1) − L(R+n)}. Çifte sayım yok, öne alma var.
 *
 * <p><b>Yaşam süresi ve thread güvenliği:</b> tick başına bir kez kurulur ve yalnız o tick'in
 * consumer thread'inde kullanılır (calculateDialBudget → drainWithBudget aynı zamanlanmış
 * görev içinde koşar). Paylaşılmaz, saklanmaz. Mutable olması bilinçlidir; drain sırasında
 * havuz büyümesini takip eder.
 */
public final class GammaRingingLoadModel {

    private static final int CB = 0;
    private static final int RETRY = 1;
    private static final int FRESH = 2;

    private final double[] answerRate = new double[3];
    private final double[] weightSum = new double[3];
    private final double[] weightSqSum = new double[3];
    private final double k;

    /** Kurulduğu andaki (drain öncesi) yük; telemetri ve marjinal hesap için saklanır. */
    private final double baseLoad;

    /** Telemetri: ilk çağrı ortalama fiyat tabanıyla mı geçti (bkz. {@code CallConsumer.isAffordable}). */
    private boolean firstCallFloorApplied;

    public GammaRingingLoadModel(double cbAnswerRate, double retryAnswerRate, double freshAnswerRate,
                                 double cbWeightSum, double retryWeightSum, double freshWeightSum,
                                 double cbWeightSqSum, double retryWeightSqSum, double freshWeightSqSum,
                                 double k) {
        this.answerRate[CB] = clampRate(cbAnswerRate);
        this.answerRate[RETRY] = clampRate(retryAnswerRate);
        this.answerRate[FRESH] = clampRate(freshAnswerRate);
        this.weightSum[CB] = nonNegative(cbWeightSum);
        this.weightSum[RETRY] = nonNegative(retryWeightSum);
        this.weightSum[FRESH] = nonNegative(freshWeightSum);
        this.weightSqSum[CB] = nonNegative(cbWeightSqSum);
        this.weightSqSum[RETRY] = nonNegative(retryWeightSqSum);
        this.weightSqSum[FRESH] = nonNegative(freshWeightSqSum);
        this.k = k;
        this.baseLoad = load();
    }

    /** Havuzun beklenen cevap sayısı (güvenlik payı olmadan). */
    public double mean() {
        double m = 0.0;
        for (int i = 0; i < 3; i++) m += answerRate[i] * weightSum[i];
        return m;
    }

    /** Tipler arası havuzlanmış Poisson-binom varyansı. */
    public double variance() {
        double v = 0.0;
        for (int i = 0; i < 3; i++) {
            double ar = answerRate[i];
            // p_i(1−p_i) = AR·w_i − AR²·w_i²  →  toplamı Σw ve Σw² üzerinden kapalı formda.
            double typeVar = ar * weightSum[i] - ar * ar * weightSqSum[i];
            if (typeVar > 0.0) v += typeVar;
        }
        return v;
    }

    /** {@code mean + k·σ}. Aktif formülde kullanılan borç değeri. */
    public double load() {
        double m = mean();
        double v = variance();
        if (v <= 0.0) return m;
        return m + k * Math.sqrt(v);
    }

    /** Model kurulduğu andaki (drain öncesi) yük. */
    public double getBaseLoad() {
        return baseLoad;
    }

    /**
     * MADDE 3A telemetrisi — drain sırasında bu turda çıkarılan partiye toplam ne kadar
     * güvenlik payı yazıldığı: {@code UCB(R+B) − UCB(R)}. Drain'den sonra okunmalıdır.
     */
    public double getChargedMarginalCost() {
        return Math.max(0.0, load() - baseLoad);
    }

    public double getCbWeightSum() {
        return weightSum[CB];
    }

    public double getRetryWeightSum() {
        return weightSum[RETRY];
    }

    public double getFreshWeightSum() {
        return weightSum[FRESH];
    }

    public double getTotalWeightSum() {
        return weightSum[CB] + weightSum[RETRY] + weightSum[FRESH];
    }

    /**
     * MADDE 3A kalibrasyonu bu drain'de devreye girdi mi: ilk çağrı tam marjinal UCB fiyatını
     * ödeyemedi, ortalama fiyat tabanıyla geçti. Kampanyanın bütçe sınırında çalıştığının
     * (agent boş ama neredeyse arama yok) doğrudan göstergesi.
     */
    public void markFirstCallFloorApplied() {
        this.firstCallFloorApplied = true;
    }

    public boolean isFirstCallFloorApplied() {
        return firstCallFloorApplied;
    }

    /**
     * MADDE 3A — bu turda çıkarılacak bir sonraki çağrının marjinal fiyatı.
     * Yeni çağrının yaşı 0 olduğu için ağırlığı {@code w(0) = 1}.
     *
     * @return {@code L(havuz + 1 adet tip) − L(havuz)}; daima &gt; 0
     */
    public double marginalCost(Constants.CALL_TYPES type) {
        int i = index(type);
        double before = load();
        weightSum[i] += 1.0;
        weightSqSum[i] += 1.0;
        double after = load();
        weightSum[i] -= 1.0;
        weightSqSum[i] -= 1.0;
        return Math.max(0.0, after - before);
    }

    /**
     * Aynı çağrının varyans payı olmayan (yalnız ortalama) fiyatı = {@code AR_ring(tip)}.
     *
     * <p>MADDE 3A kalibrasyonu için gerekli: UCB'nin normal yaklaşımı N=1'de geçersizdir.
     * {@code p = 0,50} iken ilk çağrının marjinal maliyeti {@code 1,32 > 1,0} çıkar; tek boş
     * agent'lı ve havuzu boş bir kampanya bu yüzden hiç arama yapamaz hale gelirdi
     * (kilitlenme). Havuzda tek bekleyen çağrı en fazla 1 agent'ı meşgul edebileceği için
     * ilk çağrıda ortalama fiyat tabanı kullanılır.
     */
    public double marginalMeanCost(Constants.CALL_TYPES type) {
        return answerRate[index(type)];
    }

    /** Marjinal fiyatı ödenen çağrıyı havuza işler; sonraki marjinal fiyat buna göre artar. */
    public void commit(Constants.CALL_TYPES type) {
        int i = index(type);
        weightSum[i] += 1.0;
        weightSqSum[i] += 1.0;
    }

    private static int index(Constants.CALL_TYPES type) {
        if (type == null) return FRESH;
        switch (type) {
            case CALLBACK: return CB;
            case RETRY: return RETRY;
            default: return FRESH;
        }
    }

    private static double clampRate(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) return 0.0;
        return Math.min(1.0, rate);
    }

    private static double nonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}
