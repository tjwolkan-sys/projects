// com.powerdialer.engine.predictive.gamma.model.GammaAnswerSample
package com.powerdialer.engine.predictive.gamma.model;

/**
 * MADDE 1 — cevap anında AR penceresine yazılan kaydın, çağrı nesnesi üzerinde taşınan tutamağı.
 *
 * <p>Modelin yanında YAZILDIĞI BUFFER örneğini de taşır. Bu şart: {@code onCampaignStop} tip
 * buffer'larını komple değiştiriyor. Yalnız model tutulsaydı, stop/start arasında cevaplanmış
 * bir çağrı hangup'ta "zaten sayıldı" sanılıp yeni pencereye hiç eklenmez, yani HİÇ sayılmazdı.
 * Kaybedilen küme %100 cevaplanan çağrılardan oluştuğu için ölçülen AR aşağı saparadı — güvensiz
 * yön (daha ucuz dial maliyeti + daha zayıf fren).
 *
 * <p>Buffer örneği karşılaştırması iki durumu ayırt eder:
 * <ul>
 *   <li><b>Aynı buffer</b> → kayıt bu pencerede; ya hâlâ içinde (damgalanabilir) ya da ring
 *       buffer rotasyonuyla düşmüş (tekrar eklenmemeli, {@code totalCallCount} onu zaten saydı).</li>
 *   <li><b>Farklı buffer</b> → kampanya yeniden başlamış; kayıt ölü pencereye ait, çağrı yeni
 *       pencereye normal yoldan eklenmeli.</li>
 * </ul>
 *
 * <p>JVM-only; çağrı nesnesiyle birlikte GC olur.
 */
public final class GammaAnswerSample {

    private final PredictiveGammaCallModel model;
    private final PredictiveGammaRecentCalls owner;

    public GammaAnswerSample(PredictiveGammaCallModel model, PredictiveGammaRecentCalls owner) {
        this.model = model;
        this.owner = owner;
    }

    public PredictiveGammaCallModel getModel() {
        return model;
    }

    /** Kaydın yazıldığı tip buffer'ı. Kimlik (==) karşılaştırması için tutulur. */
    public boolean isOwnedBy(PredictiveGammaRecentCalls candidate) {
        return owner != null && owner == candidate;
    }
}
