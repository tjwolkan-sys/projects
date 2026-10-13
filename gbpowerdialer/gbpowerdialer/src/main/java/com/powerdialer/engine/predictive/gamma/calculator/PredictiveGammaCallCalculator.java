// com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator
package com.powerdialer.engine.predictive.gamma.calculator;

import com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.engine.predictive.dispatcher.PredictiveGammaEventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.powerdialer.engine.predictive.gamma.processor.HoltsTrendAnalyzer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.function.ToLongFunction;

@Component
public class PredictiveGammaCallCalculator {

    private static final Logger log = LoggerFactory.getLogger(PredictiveGammaCallCalculator.class);

    /**
     * Binomial UCB (Upper Confidence Bound) tampon katsayısı.
     * ringingLoad = N × AR + k × √(N × AR × (1−AR))
     *
     * <p>k = 1.645 istatistiksel %95 tek-kuyruk güven seviyesi. Eş zamanlı cevap
     * kümelerinin yaratabileceği rc78 abandon'unu azaltmak için ringingLoad'a
     * variance tabanlı güvenlik payı ekler. Faz 2.2'de Campaign tablosundan
     * tunable hale getirilecek.
     */
    static final double DEFAULT_RINGING_LOAD_CONFIDENCE_K = 1.645;

    /**
     * MADDE 2A — {@code AR_ring / AR_dial} ölçeğinin güvenilir sayılması için gereken minimum
     * pencere örneği. Altında ölçek 1.0 kabul edilir, yani bugünkü fren aynen uygulanır.
     *
     * <p>Oran bir orantı tahmini; 50 örnek p≈0,85 civarında ±%7 hata verir. Daha az örnekte
     * gürültü doğrudan frene yansırdı.
     */
    static final int MIN_RING_SCALE_SAMPLES = 50;

    /**
     * MADDE 4A — cevaplananlarla sınırlanmış {@code avgRing} için canlı ortalamaya geçiş eşiği.
     *
     * <p>Filtresiz eşik (100) burada KULLANILAMAZ: ring havuzu 400 kayıt tutuyor ve tipik
     * cevap oranında bu havuzda ancak ~70 cevaplanan çağrı bulunur. 100 eşiği hiçbir zaman
     * aşılamaz, {@code avgRing} kalıcı olarak startup sabitine çakılırdı.
     */
    static final int STARTUP_SNAPSHOT_THRESHOLD_RING_ANSWERED = 30;

    /**
     * T1 — Vardiya sonu freninin {@code serviceAgents} uzerindeki ALT SINIRI.
     *
     * <p>Holt negatif trendi ikinci terime uygulanirken arzin tamamen sifirlanmasini engeller:
     * cikis hizi anlik olarak abartili olculse bile {@code serviceAgents} bu oranin altina inmez.
     * Sigorta amaclidir; normal calismada devreye girmez.
     */
    static final double SHIFT_END_SERVICE_AGENT_FLOOR = 0.5;

    /**
     * T2 — {@code avgTalk} icin canli ortalamaya TAM guven esigi.
     *
     * <p>{@code startupSnapshotThreshold} (100) ile bu deger arasinda kademeli gecis yapilir.
     * Deger keyfi degildir: basarili cagri penceresinin kendisidir ({@code SUCCESSFUL_CALLS_WINDOW_SIZE}
     * ile ayni). Kural: "pencere dolmadan ona tam guvenme".
     *
     * <p>Gerekce: talk ortalamasi BITEN cagrilardan alinir. Gun basinda yalnizca kisa cagrilar
     * bitmis olur, uzunlar devam eder ve gorunmez -> ortalama sistematik olarak DUSUK cikar
     * (olculen: dk 0-5'te 48,7 sn, gercek ~150 sn). Tek adimda canliya gecmek (eski davranis)
     * iyi degerden (dunun ortalamasi, hata -%12) kotu degere (canli pencere, hata -%35..-%55)
     * atlamak demekti.
     */
    static final int FULL_TRUST_THRESHOLD_TALK = 400;

    private final PredictiveEventFactory eventFactory;
    private final PredictiveGammaEventDispatcher gammaDispatcher;
    private final HoltsTrendAnalyzer holtsTrendAnalyzer;

    public PredictiveGammaCallCalculator(PredictiveEventFactory eventFactory, PredictiveGammaEventDispatcher gammaDispatcher, HoltsTrendAnalyzer holtsTrendAnalyzer) {
        this.eventFactory = eventFactory;
        this.gammaDispatcher = gammaDispatcher;
        this.holtsTrendAnalyzer = holtsTrendAnalyzer;
    }

    /**
     * Kampanya Gamma ayarlarını doğrular, snapshot verilerinden hesaplama bağlamını kurar,
     * Xn değerini hesaplar, telemetri verisini hazırlar ve sonucu döner.
     *
     * Xn = (availableAgents + (inCallAgents / (avgTalkTime + avgAcwTime)) * avgRingTime * correctionFactor) * abandonPenalty - ringingLoad
     * ringingLoad = cb.answerRate * ringing.cb + retry.answerRate * ringing.retry + fresh.answerRate * ringing.fresh
     */
    public PredictiveGammaCalculationResult calculateXn( Campaign campaign, AgentMetricsDTO agentMetrics, RingingMetricsDTO ringingMetrics, PredictiveGammaSnapshot cbSamples, PredictiveGammaSnapshot retrySamples,
                                                         PredictiveGammaSnapshot freshSamples, PredictiveGammaSnapshot successfulSamples, PredictiveGammaSnapshot ringSamples,
                                                         PredictiveGammaStartupProfile startupProfile, int startupSnapshotThreshold, int startupSnapshotThresholdCallback) {

        if (!isConfigured(campaign)) {
            return PredictiveGammaCalculationResult.zero();
        }

        CalculationContext ctx = buildContext(campaign, agentMetrics, ringingMetrics, cbSamples, retrySamples, freshSamples, successfulSamples, ringSamples, startupProfile, startupSnapshotThreshold, startupSnapshotThresholdCallback);

        if (ctx.avgTalk <= 0.0) {
            log.debug("[GAMMA] cid={} skipped: no talk time in window", campaign.getId());
            return PredictiveGammaCalculationResult.zero();
        }

        double xn = computeXn(campaign, ctx);

        log.debug("[GAMMA] cid={} totalCalls={} answered={} abandoned={} abandonRate={} arCb={} arRetry={} arFresh={} availableAgents={} acwAgents={} inCallAgents={} avgTalk={} avgRing={} avgAcw={} xn={}",
                campaign.getId(), ctx.totalCallCount, ctx.answeredCount, ctx.abandonedCount, ctx.abandonRate, ctx.cbAnswerRate, ctx.retryAnswerRate, ctx.freshAnswerRate, ctx.agentMetrics.getAvailAgents(), ctx.agentMetrics.getAcwAgents(), ctx.agentMetrics.getIncallAgents(), ctx.avgTalk, ctx.avgRing, ctx.avgAcw, xn);

        PredictiveGammaCallData callData = buildCallData(campaign, null, freshSamples.getSnapshotId(), ctx, xn);

        log.debug("[GAMMA] calculateXn() finished on thread {}", Thread.currentThread().getName());

        // NOT: sonuçtaki AR'ler AR_dial'dir ve öyle kalır — bunlar "kaç numara çevireyim"
        // sorusunun (getAnswerRateCost) girdisi. AR_ring yalnız borç modelinde taşınır.
        return new PredictiveGammaCalculationResult(xn, ctx.cbAnswerRate, ctx.retryAnswerRate, ctx.freshAnswerRate, callData, ctx.ringingLoadModel);
    }

    /**
     * Snapshot verilerini ve ajan durumunu tek bir CalculationContext içinde toplar.
     * Her döngüde bir kez çalışır; devamındaki metotlar aynı tutarlı snapshot üzerinden okur.
     */
    private CalculationContext buildContext(Campaign campaign, AgentMetricsDTO agentMetrics, RingingMetricsDTO ringingMetrics, PredictiveGammaSnapshot cbSamples, PredictiveGammaSnapshot retrySamples,
                                            PredictiveGammaSnapshot freshSamples, PredictiveGammaSnapshot successfulSamples, PredictiveGammaSnapshot ringSamples,
                                            PredictiveGammaStartupProfile startupProfile, int startupSnapshotThreshold, int startupSnapshotThresholdCallback) {
        CalculationContext ctx = new CalculationContext();

        // Gamma Plus tek noktada okunur; aşağıdaki tüm dallanmalar bu boolean üzerinden gider.
        // Kaynak, kampanya START'ında MANDALLANMIŞ değerdir (startup profili) — canlı kampanya
        // nesnesi değil. Bayrak çalışırken değiştirilirse tick ortasında avgRing tanımı ile
        // warm-up profili birbirinden ayrılır, CF küçük takımlarda aniden açılır ve ACW arza
        // eklenirdi; mandal bu geçişi imkânsız kılar.
        ctx.gammaPlus = startupProfile.isGammaPlus();

        ctx.cbStats = computeStats(cbSamples, ctx.gammaPlus);
        ctx.retryStats = computeStats(retrySamples, ctx.gammaPlus);
        ctx.freshStats = computeStats(freshSamples, ctx.gammaPlus);
        ctx.cbStartupAnswerRate = startupProfile.getCbAnswerRate();
        ctx.retryStartupAnswerRate = startupProfile.getRetryAnswerRate();
        ctx.freshStartupAnswerRate = startupProfile.getFreshAnswerRate();
        ctx.cbLiveAnswerRate = resolveLiveAnswerRate(ctx.cbStats, ctx.cbStartupAnswerRate);
        ctx.retryLiveAnswerRate = resolveLiveAnswerRate(ctx.retryStats, ctx.retryStartupAnswerRate);
        ctx.freshLiveAnswerRate = resolveLiveAnswerRate(ctx.freshStats, ctx.freshStartupAnswerRate);
        // AR startup->live geçiş eşikleri window capacity'den türetilir (dinamik):
        // tam-güven = windowCapacity, başlangıç = windowCapacity/4 (cb için windowCapacity/8).
        // Varsayılan capacity=400'de eski sabitler korunur: 400 / 100 / 50.
        // Küçük kampanyada (az agent) capacity düşürülerek warm-up orantılı kısalır.
        int blendFullTrust = Math.max(1, campaign.getGammaWindowCapacity());
        int blendStart = blendFullTrust / 4;
        int blendStartCallback = blendFullTrust / 8;
        ctx.cbAnswerRateBlendWeight = resolveAnswerRateBlendWeight(cbSamples.getTotalCallCount(), blendStartCallback, blendFullTrust);
        ctx.retryAnswerRateBlendWeight = resolveAnswerRateBlendWeight(retrySamples.getTotalCallCount(), blendStart, blendFullTrust);
        ctx.freshAnswerRateBlendWeight = resolveAnswerRateBlendWeight(freshSamples.getTotalCallCount(), blendStart, blendFullTrust);
        ctx.cbAnswerRate = resolveBlendedAnswerRate(ctx.cbStats, startupProfile.getCbAnswerRate(), cbSamples.getTotalCallCount(), blendStartCallback, blendFullTrust);
        ctx.retryAnswerRate = resolveBlendedAnswerRate(ctx.retryStats, startupProfile.getRetryAnswerRate(), retrySamples.getTotalCallCount(), blendStart, blendFullTrust);
        ctx.freshAnswerRate = resolveBlendedAnswerRate(ctx.freshStats, startupProfile.getFreshAnswerRate(), freshSamples.getTotalCallCount(), blendStart, blendFullTrust);

        // MADDE 2A — ringingLoad çarpanı "açtı / ARANDI" değil "açtı / ÇALDI" olmalı.
        // Payları aynı olduğu için oran tek bir ölçeğe iner:
        //     AR_ring / AR_dial = N_arandı / N_çaldı = 1 / (1 − hiç_çalmayan_payı)
        // Böylece mevcut startup↔live blend zinciri hiç değişmeden korunur; üzerine yalnız
        // ölçek uygulanır. "Çaldı" tanımı için ölçülmüş V2 kümesi kullanılır (MADDE 0).
        // Bayrak kapalıyken ölçek hesaplanmaz ve AR_ring alanları AR_dial'e eşit kalır.
        ctx.cbRingAnswerRate = ctx.cbAnswerRate;
        ctx.retryRingAnswerRate = ctx.retryAnswerRate;
        ctx.freshRingAnswerRate = ctx.freshAnswerRate;
        if (ctx.gammaPlus) {
            ctx.cbRingAnswerRate = applyRingScale(ctx.cbAnswerRate, ctx.cbStats, ctx.cbAnswerRateBlendWeight);
            ctx.retryRingAnswerRate = applyRingScale(ctx.retryAnswerRate, ctx.retryStats, ctx.retryAnswerRateBlendWeight);
            ctx.freshRingAnswerRate = applyRingScale(ctx.freshAnswerRate, ctx.freshStats, ctx.freshAnswerRateBlendWeight);
        }

        ctx.answeredCount = ctx.cbStats.answered + ctx.retryStats.answered + ctx.freshStats.answered;
        ctx.abandonedCount = ctx.cbStats.abandoned + ctx.retryStats.abandoned + ctx.freshStats.abandoned;
        long sampleCount = ctx.cbStats.total + ctx.retryStats.total + ctx.freshStats.total;

        ctx.totalCallCount = cbSamples.getTotalCallCount() + retrySamples.getTotalCallCount() + freshSamples.getTotalCallCount();
        ctx.totalAbandonCallCount = cbSamples.getTotalAbandonCallCount() + retrySamples.getTotalAbandonCallCount() + freshSamples.getTotalAbandonCallCount();
        ctx.totalAbandonedByCustomer = cbSamples.getTotalAbandonedByCustomer() + retrySamples.getTotalAbandonedByCustomer() + freshSamples.getTotalAbandonedByCustomer();
        ctx.totalAbandonedBySystem = cbSamples.getTotalAbandonedBySystem() + retrySamples.getTotalAbandonedBySystem() + freshSamples.getTotalAbandonedBySystem();

        ctx.abandonRate = sampleCount > 0 ? (double) ctx.abandonedCount / sampleCount : 0.0;

        ctx.successfulSampleCount = countSamples(successfulSamples);
        // T2 — talk ortalamasinda kademeli gecis (bkz. FULL_TRUST_THRESHOLD_TALK).
        // avgRing DEGISMEDI: olculdu, ring'de gun basi carpikligi YOK (gunden gune sapma %1-4).
        // Sebep yapisal: ring 12-21 sn ve timeout ile sinirli, cagrilar aninda tamamlanir;
        // talk ise 100-280 sn ve uzun kuyruklu -> "olculen surenin uzunlugu = carpikligin buyuklugu".
        ctx.avgTalk = resolveBlendedAverage(computeAvg(successfulSamples, PredictiveGammaCallModel::getTalkTime),
                startupProfile.getAvgTalkTime(), ctx.successfulSampleCount,
                startupSnapshotThreshold, FULL_TRUST_THRESHOLD_TALK);

        if (ctx.gammaPlus) {
            // MADDE 4A — avgRing "müşteri açana kadar geçen süre" sorusunun cevabıdır.
            // Cevapsız çağrının böyle bir süresi yok; onun ölçülen süresi ring timeout'a yakındır
            // ve ortalamayı medyan 1,37× şişiriyor (21 kampanyanın 15'inde ≥1,30×). Şişmiş ufuk
            // doğrudan fazla arz → fazla arama demek.
            RingAverage answeredRing = computeAnsweredRingAverage(ringSamples);
            ctx.ringSampleCount = answeredRing.count;
            ctx.avgRing = resolveAverage(answeredRing.average, startupProfile.getAvgRingTime(), answeredRing.count, STARTUP_SNAPSHOT_THRESHOLD_RING_ANSWERED);
        } else {
            ctx.ringSampleCount = countSamples(ringSamples);
            ctx.avgRing = resolveAverage(computeAvg(ringSamples, PredictiveGammaCallModel::getRingTime), startupProfile.getAvgRingTime(), ctx.ringSampleCount, startupSnapshotThreshold);
        }

        ctx.avgAcw = startupProfile.getAvgAcwTime();

        ctx.agentMetrics = agentMetrics;
        ctx.ringingMetrics = ringingMetrics;
        
        ctx.productionLineAgentCount = ctx.agentMetrics.getAvailAgents() + ctx.agentMetrics.getIncallAgents() + ctx.agentMetrics.getAcwAgents();
        int physicalAgentCount = (int) Math.round(ctx.agentMetrics.getPhysicalTotalAgents());
        HoltsTrendAnalyzer.HoltResult holtResult = holtsTrendAnalyzer.updateAndGetTrend(campaign.getId(), physicalAgentCount);
        ctx.holtTrend = holtResult.trend;
        ctx.holtLevel = holtResult.level;
        ctx.holtLastUpdateMs = holtResult.lastUpdateMs;

        double configuredCF = campaign.getGammaCorrectionFactor() != null ? campaign.getGammaCorrectionFactor() : 0.0;
        double effectiveAgents = ctx.agentMetrics.getTotalAgents();
        ctx.configuredCorrectionFactor = configuredCF;

        if (ctx.gammaPlus) {
            // MADDE 5 — ≤5 agent uçurumu kaldırıldı. Eski davranışta CF=0 arz terimini komple
            // siliyordu: küçük takımda herkes incall olunca avail=0 → Xn=0 → hiç arama yok
            // (all-incall ölü bölgesi), 6 agent'ta ise bir anda tam CF devreye giriyordu.
            // CF=0 aslında eksik güvenlik payının koltuk değneğiydi; MADDE 3A payı doğru yerden
            // ve doğru miktarda ürettiği için değneğe gerek kalmadı.
            // Eşiğin hangi sayıya bakacağı tartışması da (blend paylı totalAgents vs
            // physicalTotalAgents) böylece konusuz kalır — eşik yok.
            ctx.correctionFactorScale = 1.0;
            ctx.correctionFactor = configuredCF;
        } else if (effectiveAgents <= 5.0) {
            // Katmanlı (ramp) artış kaldırıldı: tek eşik. 5 agent ve altında CF devre dışı
            // (0.0), üstünde tam configuredCF uygulanır.
            ctx.correctionFactorScale = 0.0;
            ctx.correctionFactor = 0.0;
        } else {
            ctx.correctionFactorScale = 1.0;
            ctx.correctionFactor = configuredCF;
        }

        return ctx;
    }

    private static class CalculationContext {
        boolean gammaPlus;
        GammaRingingLoadModel ringingLoadModel;
        SnapshotStats cbStats, retryStats, freshStats;
        long answeredCount, abandonedCount, totalCallCount, totalAbandonCallCount, totalAbandonedByCustomer, totalAbandonedBySystem;
        double abandonRate;
        double cbAnswerRate, retryAnswerRate, freshAnswerRate;
        /** MADDE 2A: yalnız ringingLoad'da kullanılan "açtı/çaldı" oranı. Bayrak kapalıyken AR_dial'e eşittir. */
        double cbRingAnswerRate, retryRingAnswerRate, freshRingAnswerRate;
        double cbStartupAnswerRate, retryStartupAnswerRate, freshStartupAnswerRate;
        double cbLiveAnswerRate, retryLiveAnswerRate, freshLiveAnswerRate;
        double cbAnswerRateBlendWeight, retryAnswerRateBlendWeight, freshAnswerRateBlendWeight;
        double avgTalk, avgRing, avgAcw;
        long successfulSampleCount, ringSampleCount;
        double productionLineAgentCount;
        double holtTrend, holtLevel, expectedAgentChange, effectiveAvailable;
        long holtLastUpdateMs;
        AgentMetricsDTO agentMetrics;
        RingingMetricsDTO ringingMetrics;
        double configuredCorrectionFactor, correctionFactorScale, correctionFactor;
        double abandonPenalty, rawXn, ringingLoad;
        double ringingLoadMean, ringingLoadSafe, ringingLoadConfidenceK;
    }

    /**
     * Gamma formülünü uygulayarak sıradaki arama bütçesini üretir.
     * Negatif değerleri 0'a çeker; tam durma algılanırsa müsait ajan sayısına geri döner.
     */
    private double computeXn(Campaign campaign, CalculationContext ctx) {
        ctx.abandonPenalty = Math.max(0.1, 1.0 - (ctx.abandonRate * campaign.getGammaAbandonSensitivity()));
        
        // Asimetrik yavaşlatma: Yalnızca negatif ajan trendini risk kabul eder, pozitif trendi etkisiz sayar.
        ctx.expectedAgentChange = Math.min(0.0, ctx.holtTrend) * ctx.avgRing;
        ctx.effectiveAvailable = Math.max(0.0, ctx.agentMetrics.getAvailAgents() + ctx.expectedAgentChange);
        
        double avgServiceTime = ctx.avgTalk + ctx.avgAcw;

        // MADDE 4B — Little yasası: payda "talk + acw" ise pay da "incall + acw" olmalı.
        // Eski hâlde boşalma hızı talk/(talk+acw) oranında eksik tahmin ediliyordu.
        // avgAcw ölçülmemişse (0) ACW aşaması modelde yok demektir; o durumda acwAgents'ı paya
        // eklemek paydası olmayan bir terim üretip arzı şişirirdi — bu yüzden koşullu.
        double serviceAgents = ctx.agentMetrics.getIncallAgents();
        if (ctx.gammaPlus && ctx.avgAcw > 0.0) {
            serviceAgents += ctx.agentMetrics.getAcwAgents();
        }

        // T1 — Vardiya sonu freni: Holt sinyali ikinci terime de uygulanir.
        //
        // Sorun: expectedAgentChange (satir 289) yalnizca effectiveAvailable'dan dusuluyordu.
        // Vardiya sonunda bosta ajan zaten 0 oldugu icin fren isiracak bir sey bulamiyor; ancak
        // arzi ureten asil terim ikinci terimdir ("bu ajanlar birazdan bosalacak, cagri hazirla").
        // Gercekte o ajanlar bosalmayacak, CIKIS yapacak. Olculen etki: 15 gamma kampanyasinin
        // 12'sinde son 30 dk abandon'u gun ortalamasinin 1,5x-11,8x'i.
        //
        // Cozum: ayni negatif trendi serviceAgents'a da yansit. holtTrend zaten SANIYE BASINA ajan
        // degisimidir (HoltsTrendAnalyzer saniyede bir tetiklenir), ortalama servis suresiyle
        // carpilinca "bu servis penceresinde kac ajan cikacak" tahmini cikar.
        // DIKKAT: burada ek normalizasyon YAPILMAZ ve yapilmamalidir; satir 289 da ayni birim
        // varsayimiyla holtTrend'i dogrudan avgRing ile carpar. deltaSeconds'a bolmek cift
        // normalizasyon olur ve freni sessizce etkisizlestirir.
        // %50 taban: arzin hicbir kosulda sifirlanmamasi icin sigorta.
        //
        // Davranis garantisi: holtTrend >= 0 iken leaving = 0 -> serviceAgents DEGISMEZ
        // (gunun %95'inde bugunku kodla bit-esit). Yalnizca ajan AZALIRKEN arz kisilir.
        if (ctx.holtTrend < 0.0 && avgServiceTime > 0.0) {
            double leaving = ctx.holtTrend * avgServiceTime;   // holtTrend negatif -> leaving negatif
            serviceAgents = Math.max(serviceAgents * SHIFT_END_SERVICE_AGENT_FLOOR, serviceAgents + leaving);
        }

        ctx.rawXn = (ctx.effectiveAvailable + (serviceAgents / avgServiceTime) * ctx.avgRing * ctx.correctionFactor) * ctx.abandonPenalty;

        ctx.ringingLoadConfidenceK = DEFAULT_RINGING_LOAD_CONFIDENCE_K;

        if (ctx.gammaPlus) {
            // MADDE 2A + 2B + 2C tek modelde. Ayrıntı: GammaRingingLoadModel javadoc'u.
            ctx.ringingLoadModel = buildRingingLoadModel(ctx);
            ctx.ringingLoadMean = ctx.ringingLoadModel.mean();
            ctx.ringingLoadSafe = ctx.ringingLoadModel.load();
        } else {
            // ringingLoad: önce ortalamayı (mean) hesapla, sonra Binomial UCB ile güvenlik payı ekle.
            // Düşük AR + büyük havuz senaryosunda eş zamanlı cevap kümelerinden gelen rc78 abandon'u
            // önlemek için variance tabanlı tampon uygulanır.
            double cbMean    = ctx.cbAnswerRate    * ctx.ringingMetrics.getCbRinging();
            double retryMean = ctx.retryAnswerRate * ctx.ringingMetrics.getRetryRinging();
            double freshMean = ctx.freshAnswerRate * ctx.ringingMetrics.getFreshRinging();
            ctx.ringingLoadMean = cbMean + retryMean + freshMean;

            double cbUcb    = ucbRingingLoad(ctx.cbAnswerRate,    ctx.ringingMetrics.getCbRinging(),    ctx.ringingLoadConfidenceK);
            double retryUcb = ucbRingingLoad(ctx.retryAnswerRate, ctx.ringingMetrics.getRetryRinging(), ctx.ringingLoadConfidenceK);
            double freshUcb = ucbRingingLoad(ctx.freshAnswerRate, ctx.ringingMetrics.getFreshRinging(), ctx.ringingLoadConfidenceK);
            ctx.ringingLoadSafe = cbUcb + retryUcb + freshUcb;
        }

        // Aktif formülde safe değer kullanılır; mean telemetri için saklanır.
        ctx.ringingLoad = ctx.ringingLoadSafe;

        double xn = Math.max(0.0, ctx.rawXn - ctx.ringingLoad);

        // NOT: burada eskiden bir "[GAMMA] Halt detected" fallback'i vardı. Dal ispatlı no-op'tu:
        // ringing=0 iken ringingLoad=0 olur, dolayısıyla xn=0 ancak effectiveAvailable=0 iken
        // mümkündür; o durumda fallback de 0 atıyordu. Buna karşılık tüm agent'lar moladayken
        // kampanya başına 2 saniyede bir rate limit'siz WARN basıyordu. Kaldırıldı; hesaplanan
        // değer değişmez, yalnız log gürültüsü kalkar.

        return Double.isFinite(xn) ? xn : 0.0;
    }

    /**
     * MADDE 2A/2B/2C — Gamma Plus borç modeli. Yaş ağırlıkları ringing manager'da tek geçişte
     * toplandı; burada yalnız tipe göre eşleştirilip modele verilir.
     */
    private GammaRingingLoadModel buildRingingLoadModel(CalculationContext ctx) {
        RingingMetricsDTO r = ctx.ringingMetrics;
        return new GammaRingingLoadModel(
                ctx.cbRingAnswerRate, ctx.retryRingAnswerRate, ctx.freshRingAnswerRate,
                resolveWeightSum(r.getCbRingingWeight(), r.getCbRinging()),
                resolveWeightSum(r.getRetryRingingWeight(), r.getRetryRinging()),
                resolveWeightSum(r.getFreshRingingWeight(), r.getFreshRinging()),
                resolveWeightSum(r.getCbRingingWeightSq(), r.getCbRinging()),
                resolveWeightSum(r.getRetryRingingWeightSq(), r.getRetryRinging()),
                resolveWeightSum(r.getFreshRingingWeightSq(), r.getFreshRinging()),
                ctx.ringingLoadConfidenceK);
    }

    /**
     * Savunma: havuzda çalan hat varken yaş ağırlığı toplamı 0 olamaz (en küçük ağırlık 0,07).
     * 0 gelmişse ağırlıklar doldurulmamış demektir; bu durumda ham sayıya (her hat için w=1)
     * düşülür. Sessizce frenin kaybolmasındansa 2B kazancından vazgeçmek tercih edilir.
     */
    private double resolveWeightSum(double weightSum, int ringingCount) {
        if (ringingCount <= 0) return 0.0;
        if (Double.isFinite(weightSum) && weightSum > 0.0) return weightSum;
        return ringingCount;
    }

    /**
     * Güncel bağlamdan telemetri verisini üretir.
     */
    private PredictiveGammaCallData buildCallData(Campaign campaign, PredictiveGammaCallModel newCall, String snapshotId, CalculationContext ctx, double xn) {
        Date now = Date.from(ZonedDateTime.now(ZoneId.of("Europe/Istanbul")).toInstant());

        PredictiveGammaCallData data = new PredictiveGammaCallData(campaign, newCall);
        data.setSnapshotId(snapshotId);

        data.setTotalAgentsCount(ctx.agentMetrics.getTotalAgents());
        data.setPhysicalTotalAgentsCount(ctx.agentMetrics.getPhysicalTotalAgents());
        data.setAvailableAgentsCount(ctx.agentMetrics.getAvailAgents());
        data.setIncallAgentsCount(ctx.agentMetrics.getIncallAgents());
        data.setAcwAgentsCount(ctx.agentMetrics.getAcwAgents());

        data.setProductionLineAgentCount(ctx.productionLineAgentCount);
        data.setHoltTrend(ctx.holtTrend);
        data.setHoltLevel(ctx.holtLevel);
        data.setHoltLastUpdateMs(ctx.holtLastUpdateMs);
        data.setExpectedAgentChange(ctx.expectedAgentChange);
        data.setEffectiveAvailable(ctx.effectiveAvailable);

        data.setCalculationIntervalStartTime(now);

        data.setAnsweredCallCount((int) ctx.answeredCount);
        data.setAbandonedCallCount((int) ctx.abandonedCount);
        data.setTotalCallCount((int) ctx.totalCallCount);
        data.setTotalAbandonCallCount((int) ctx.totalAbandonCallCount);
        data.setTotalAbandonedByCustomer((int) ctx.totalAbandonedByCustomer);
        data.setTotalAbandonedBySystem((int) ctx.totalAbandonedBySystem);

        data.setRingingCount(ctx.ringingMetrics.getTotalRinging());
        data.setRingingCbCount(ctx.ringingMetrics.getCbRinging());
        data.setRingingRetryCount(ctx.ringingMetrics.getRetryRinging());
        data.setRingingFreshCount(ctx.ringingMetrics.getFreshRinging());
        int pendingRinging = ctx.ringingMetrics.getPendingTotalRinging();
        data.setPendingRingingCount(pendingRinging);
        data.setRealtimeOnlyRingingCount(Math.max(0, ctx.ringingMetrics.getTotalRinging() - pendingRinging));
        data.setPendingRingingMaxAgeMs(ctx.ringingMetrics.getPendingMaxAgeMs());

        data.setCbAnsweredCount((int) ctx.cbStats.answered);
        data.setCbAbandonedCount((int) ctx.cbStats.abandoned);
        data.setCbSampleCount((int) ctx.cbStats.total);
        data.setCbAnswerRate(ctx.cbAnswerRate);
        data.setCbStartupAnswerRate(ctx.cbStartupAnswerRate);
        data.setCbLiveAnswerRate(ctx.cbLiveAnswerRate);
        data.setCbAnswerRateBlendWeight(ctx.cbAnswerRateBlendWeight);

        data.setRetryAnsweredCount((int) ctx.retryStats.answered);
        data.setRetryAbandonedCount((int) ctx.retryStats.abandoned);
        data.setRetrySampleCount((int) ctx.retryStats.total);
        data.setRetryAnswerRate(ctx.retryAnswerRate);
        data.setRetryStartupAnswerRate(ctx.retryStartupAnswerRate);
        data.setRetryLiveAnswerRate(ctx.retryLiveAnswerRate);
        data.setRetryAnswerRateBlendWeight(ctx.retryAnswerRateBlendWeight);

        data.setFreshAnsweredCount((int) ctx.freshStats.answered);
        data.setFreshAbandonedCount((int) ctx.freshStats.abandoned);
        data.setFreshSampleCount((int) ctx.freshStats.total);
        data.setFreshAnswerRate(ctx.freshAnswerRate);
        data.setFreshStartupAnswerRate(ctx.freshStartupAnswerRate);
        data.setFreshLiveAnswerRate(ctx.freshLiveAnswerRate);
        data.setFreshAnswerRateBlendWeight(ctx.freshAnswerRateBlendWeight);

        data.setComputedAbandonRate(ctx.abandonRate);
        data.setComputedAbandonPenalty(ctx.abandonPenalty);
        data.setAvgTalkTime(ctx.avgTalk);
        data.setAvgRingTime(ctx.avgRing);
        data.setAvgAcwTime(ctx.avgAcw);
        data.setCurrentSuccesfulWindowSize((int) ctx.successfulSampleCount);
        data.setCurrentRingWindowSize((int) ctx.ringSampleCount);
        data.setCorrectionFactor(ctx.correctionFactor);
        data.setConfiguredCorrectionFactor(ctx.configuredCorrectionFactor);
        data.setCorrectionFactorScale(ctx.correctionFactorScale);
        data.setRawXn(ctx.rawXn);
        data.setRingingLoad(ctx.ringingLoad);
        data.setRingingLoadMean(ctx.ringingLoadMean);
        data.setRingingLoadSafe(ctx.ringingLoadSafe);
        data.setRingingLoadConfidenceK(ctx.ringingLoadConfidenceK);
        data.setXn(xn);

        // Gamma Plus izlenebilirlik. Kol ayirt edici daima yazilir; geri kalanlar yalniz plus
        // yolunda anlamli oldugu icin bayrak kapaliyken null birakilir (turetilmis degil,
        // "hesaplanmadi" bilgisi tasirlar).
        data.setGammaPlus(ctx.gammaPlus);
        if (ctx.gammaPlus) {
            data.setCbRingAnswerRate(ctx.cbRingAnswerRate);
            data.setRetryRingAnswerRate(ctx.retryRingAnswerRate);
            data.setFreshRingAnswerRate(ctx.freshRingAnswerRate);
            if (ctx.ringingLoadModel != null) {
                // Model kurulduktan sonra, drain'den ONCE okunur: havuzun tick anindaki hali.
                data.setRingingCbWeightedCount(ctx.ringingLoadModel.getCbWeightSum());
                data.setRingingRetryWeightedCount(ctx.ringingLoadModel.getRetryWeightSum());
                data.setRingingFreshWeightedCount(ctx.ringingLoadModel.getFreshWeightSum());
                data.setRingingWeightedCount(ctx.ringingLoadModel.getTotalWeightSum());
            }
        }

        return data;
    }

    public void dispatchEvent(PredictiveGammaCallData data) {
        if (data == null) return;
        gammaDispatcher.dispatchAsync(eventFactory.createGamma(data));
    }

    /** Kampanya üzerinde zorunlu Gamma parametrelerinden biri eksikse false döner. */
    private boolean isConfigured(Campaign campaign) {
        return campaign.getGammaCorrectionFactor() != null && campaign.getGammaAbandonSensitivity() != null
                && campaign.getGammaWindowCapacity() != null && campaign.getGammaRequiredWindowSize() != null;
    }

    /**
     * Snapshot içindeki toplam, cevaplanan ve abandon çağrı sayılarını SnapshotStats içine yazar.
     *
     * @param countRingValid Gamma Plus'ta ek olarak "fiilen çalan" sayısını da aynı geçişte
     *                       toplar (MADDE 0/2A). Bayrak kapalıyken tek bir ek karşılaştırma
     *                       bile yapılmaz.
     */
    private SnapshotStats computeStats(PredictiveGammaSnapshot snapshot, boolean countRingValid) {
        SnapshotStats stats = new SnapshotStats();
        for (PredictiveGammaCallModel m : snapshot) {
            stats.total++;
            if (m.isAnswered())  stats.answered++;
            if (m.isAbandoned()) stats.abandoned++;
            if (countRingValid && (m.isAnswered() || Constants.RING_VALID_RESULT_CODES_V2.contains((int) m.getResultCode()))) {
                stats.ringValid++;
            }
        }
        return stats;
    }

    /**
     * MADDE 2A — {@code AR_dial}'i {@code AR_ring}'e çevirir.
     *
     * <p>{@code AR_ring / AR_dial = N_arandı / N_çaldı}. Ölçek yalnız yeterli örnek varsa
     * uygulanır; altında 1.0 döner, yani bugünkü fren aynen korunur. Sonuç [0,1]'e kırpılır.
     */
    private double applyRingScale(double dialAnswerRate, SnapshotStats stats, double liveBlendWeight) {
        if (stats.ringValid < MIN_RING_SCALE_SAMPLES || stats.ringValid <= 0 || stats.total <= 0) {
            return dialAnswerRate;
        }
        if (!(liveBlendWeight > 0.0)) return dialAnswerRate;

        double scale = (double) stats.total / stats.ringValid;
        if (!Double.isFinite(scale) || scale < 1.0) return dialAnswerRate;

        // Ölçek, AR'nin canlı pencereden gelen PAYI kadar uygulanır. Aksi hâlde AR henüz %100
        // startup sabitiyken (ör. 0,50) canlı pencereden ölçülmüş bir çöp oranıyla çarpılır ve
        // fren gerçeğin 2 katına çıkardı: agent boş beklerken Xn=0 — tam da 3A/5'in korumaya
        // çalıştığı warm-up dakikaları. Blend ağırlığı 0 iken bugünkü fren, 1 iken tam ölçek.
        double effectiveScale = 1.0 + (scale - 1.0) * Math.min(1.0, liveBlendWeight);
        return Math.min(1.0, dialAnswerRate * effectiveScale);
    }

    /**
     * MADDE 4A — ring havuzunun YALNIZ cevaplanan kayıtları üzerinden ortalama ring süresi ve
     * örnek sayısı. Tek geçiş; ikinci tur veya kopyalama yok.
     */
    private RingAverage computeAnsweredRingAverage(PredictiveGammaSnapshot snapshot) {
        long sum = 0, count = 0;
        for (PredictiveGammaCallModel m : snapshot) {
            if (!m.isAnswered()) continue;
            sum += m.getRingTime();
            count++;
        }
        return new RingAverage(count > 0 ? (double) sum / count : 0.0, count);
    }

    private static class RingAverage {
        final double average;
        final long count;

        RingAverage(double average, long count) {
            this.average = average;
            this.count = count;
        }
    }

    /** Snapshot içindeki çağrı modellerinden alınan long alanın ortalamasını hesaplar; snapshot boşsa 0.0 döner. */
    private double computeAvg(PredictiveGammaSnapshot snapshot, ToLongFunction<PredictiveGammaCallModel> extractor) {
        long sum = 0, count = 0;
        for (PredictiveGammaCallModel m : snapshot) {
            sum += extractor.applyAsLong(m);
            count++;
        }
        return count > 0 ? (double) sum / count : 0.0;
    }

    /** Snapshot üzerinde gezerek çağrı modeli sayısını döner. */
    private long countSamples(PredictiveGammaSnapshot snapshot) {
        long count = 0;
        for (PredictiveGammaCallModel ignored : snapshot) count++;
        return count;
    }

    private double resolveBlendedAnswerRate(SnapshotStats stats, double startupAnswerRate, long totalCallCount, int startThreshold, int fullTrustThreshold) {
        if (totalCallCount <= startThreshold) return startupAnswerRate;

        double liveRate = resolveLiveAnswerRate(stats, startupAnswerRate);
        if (totalCallCount >= fullTrustThreshold) return liveRate;

        double liveWeight = resolveAnswerRateBlendWeight(totalCallCount, startThreshold, fullTrustThreshold);
        double defaultWeight = 1.0 - liveWeight;

        return (liveRate * liveWeight) + (startupAnswerRate * defaultWeight);
    }

    private double resolveLiveAnswerRate(SnapshotStats stats, double startupAnswerRate) {
        return stats.total > 0 ? stats.answerRate() : startupAnswerRate;
    }

    private double resolveAnswerRateBlendWeight(long totalCallCount, int startThreshold, int fullTrustThreshold) {
        if (totalCallCount <= startThreshold) return 0.0;
        if (totalCallCount >= fullTrustThreshold) return 1.0;
        return (double) (totalCallCount - startThreshold) / (fullTrustThreshold - startThreshold);
    }

    /**
     * T2 — {@link #resolveAverage} ile ayni sozlesme, fakat esik ile tam-guven arasinda
     * KADEMELI gecis yapar (sert atlama yerine agirlikli karisim).
     *
     * <p>{@link #resolveBlendedAnswerRate} ile ayni desen; tek fark girdi tipidir. Davranis:</p>
     * <ul>
     *   <li>{@code n <= blendStart} -> startup (eski davranisla bit-esit)</li>
     *   <li>{@code n >= fullTrust}  -> canli  (eski davranisla bit-esit)</li>
     *   <li>arada              -> agirlikli karisim (YENI; eskiden sert atlama vardi)</li>
     * </ul>
     *
     * <p>{@code live <= 0} ise olcum yok demektir; startup dondurulur (resolveAverage ile ayni koruma).
     * {@code fullTrust <= blendStart} gibi bozuk konfigurasyonda bolme hatasi olmamasi icin
     * esikler arasi fark pozitif degilse dogrudan canliya dusulur.</p>
     */
    private double resolveBlendedAverage(double liveAverage, double startupAverage, long sampleCount,
                                         int blendStartThreshold, int fullTrustThreshold) {
        if (sampleCount <= blendStartThreshold || liveAverage <= 0.0) return startupAverage;
        if (sampleCount >= fullTrustThreshold) return liveAverage;
        if (fullTrustThreshold <= blendStartThreshold) return liveAverage;

        double liveWeight = (double) (sampleCount - blendStartThreshold)
                / (fullTrustThreshold - blendStartThreshold);
        return (liveAverage * liveWeight) + (startupAverage * (1.0 - liveWeight));
    }

    private double resolveAverage(double liveAverage, double startupAverage, long sampleCount, int startupSnapshotThreshold) {
        return sampleCount >= startupSnapshotThreshold && liveAverage > 0.0 ? liveAverage : startupAverage;
    }

    /**
     * Binomial UCB ile ringingLoad güvenlik üst sınırı.
     *
     * <p>Tek bir çağrı tipinin (cb/retry/fresh) beklenen cevap sayısı + k σ tamponu döner.
     * Düşük AR'da σ'nın N ile orantılı büyümesi pool şişmesini otomatik frenler.
     *
     * @param answerRate pencereli (blend uygulanmış) AR; [0,1]
     * @param ringingCount o tipin anlık ringing havuzu sayısı (negatif değerler 0 sayılır)
     * @param k güven katsayısı (0 → sadece mean davranışı)
     * @return mean + k × √(N × AR × (1−AR)); kenar durumlarında 0
     */
    private double ucbRingingLoad(double answerRate, int ringingCount, double k) {
        if (ringingCount <= 0) return 0.0;
        if (answerRate <= 0.0) return 0.0;
        double ar = Math.min(1.0, answerRate);
        double n = ringingCount;
        double mean = n * ar;
        double variance = n * ar * (1.0 - ar);
        if (variance <= 0.0) return mean;
        double std = Math.sqrt(variance);
        return mean + k * std;
    }

    private static class SnapshotStats {
        long total = 0, answered = 0, abandoned = 0;
        /** MADDE 0/2A: fiilen çalan (V2 kümesi ∪ cevaplanan) kayıt sayısı. Yalnız Gamma Plus'ta dolar. */
        long ringValid = 0;

        double answerRate() {
            return total > 0 ? (double) answered / total : 0.0;
        }
    }
}
