package com.powerdialer.engine.predictive.gamma.model;

import lombok.Getter;

@Getter
public class PredictiveGammaStartupProfile {
    private final double cbAnswerRate;
    private final double retryAnswerRate;
    private final double freshAnswerRate;
    private final double avgTalkTime;
    private final double avgRingTime;
    private final double avgAcwTime;

    /**
     * Kampanya START anında okunup MANDALLANMIŞ Gamma Plus bayrağı.
     *
     * <p>Neden profil üzerinde taşınıyor: bayrak {@code pd_campaign} kolonu olduğu için
     * {@code CampaignPropertyCopier} onu çalışan kampanyaya kopyalayabiliyor, yani ekrandan
     * çalışma sırasında değiştirilmesi teknik olarak mümkün. Ama {@code avgRing}'in tanımı
     * (filtresiz / yalnız cevaplananlar) START'ta bir kez sorgulanıyor. Bayrak tick sırasında
     * okunsaydı, canlı yol cevaplanan-bazlı ortalamaya geçerken warm-up profili filtresiz
     * kalır; üstüne aynı anda CF küçük takımlarda açılır ve ACW arza eklenirdi — mümkün olan
     * en fazla arz şişmesi. Mandal bu geçişi imkânsız kılar: bayrak yalnız stop/start ile
     * devreye girer, ki kural da budur.
     */
    private final boolean gammaPlus;

    public PredictiveGammaStartupProfile(double cbAnswerRate, double retryAnswerRate, double freshAnswerRate, double avgTalkTime, double avgRingTime, double avgAcwTime) {
        this(cbAnswerRate, retryAnswerRate, freshAnswerRate, avgTalkTime, avgRingTime, avgAcwTime, false);
    }

    public PredictiveGammaStartupProfile(double cbAnswerRate, double retryAnswerRate, double freshAnswerRate, double avgTalkTime, double avgRingTime, double avgAcwTime, boolean gammaPlus) {
        this.cbAnswerRate = cbAnswerRate;
        this.retryAnswerRate = retryAnswerRate;
        this.freshAnswerRate = freshAnswerRate;
        this.avgTalkTime = avgTalkTime;
        this.avgRingTime = avgRingTime;
        this.avgAcwTime = avgAcwTime;
        this.gammaPlus = gammaPlus;
    }

    public boolean isGammaPlus() {
        return gammaPlus;
    }
}
