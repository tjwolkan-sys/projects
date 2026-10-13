// com.powerdialer.engine.predictive.gamma.processor.PredictiveGammaCallManager
package com.powerdialer.engine.predictive.gamma.processor;

import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Supplier;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.RingingMetricsDTO;
import com.powerdialer.engine.predictive.gamma.calculator.PredictiveGammaCallCalculator;
import com.powerdialer.engine.predictive.gamma.model.GammaAnswerSample;
import com.powerdialer.engine.predictive.gamma.model.GammaRingingLoadModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalculationResult;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallModel;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaRecentCalls;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaSnapshot;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaStartupProfile;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.service.CDRService;
import com.powerdialer.util.Constants;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PredictiveGammaCallManager {

    private static final Logger log = LoggerFactory.getLogger(PredictiveGammaCallManager.class);
    private static final double EPSILON = 0.000001d;

    private final PredictiveGammaCallCalculator calculator;
    private final HoltsTrendAnalyzer holtsTrendAnalyzer;
    private final CDRService cdrService;
    private final SharedGammaSnapshotProvider sharedGammaSnapshotProvider;

    private final Map<Long, PredictiveGammaRecentCalls> recentCbCallsMap = new ConcurrentHashMap<>(); //CampaignId, RecentCalls
    private final Map<Long, PredictiveGammaRecentCalls> recentRetryCallsMap = new ConcurrentHashMap<>(); //CampaignId, RecentCalls
    private final Map<Long, PredictiveGammaRecentCalls> recentFreshCallsMap = new ConcurrentHashMap<>(); //CampaignId, RecentCalls
    private final Map<Long, PredictiveGammaRecentCalls> recentSuccessfulCallsMap = new ConcurrentHashMap<>(); //CampaignId, RecentCalls
    private final Map<Long, PredictiveGammaRecentCalls> recentRingCallsMap = new ConcurrentHashMap<>(); //CampaignId, RecentCalls

    private final Map<Long, Double> cbAnswerRateMap = new ConcurrentHashMap<>(); //CampaignId, answerRate
    private final Map<Long, Double> retryAnswerRateMap = new ConcurrentHashMap<>(); //CampaignId, answerRate
    private final Map<Long, Double> freshAnswerRateMap = new ConcurrentHashMap<>(); //CampaignId, answerRate
    private final Map<Long, PredictiveGammaStartupProfile> startupProfileMap = new ConcurrentHashMap<>();
    private final Map<Long, Integer> lastAcwRefreshSuccessfulCountMap = new ConcurrentHashMap<>();
    private final Map<Long, Double> starvationCreditMap = new ConcurrentHashMap<>();

    /**
     * FIX-333607: dünkü (son çalışma günü) ortalamalar gün içinde değişmez; aynı gün yeniden start'ta
     * ağır {@code pd_cdr} sorguları tekrar çalışmaz. Campaign başına tek giriş tutulur ve tarih
     * değişince yeniden hesaplanır. Bugünkü cevap oranı canlı veri olduğu için önbelleğe alınmaz.
     */
    private final Map<Long, YesterdayAverages> yesterdayAveragesCache = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemDefaultZone();

    /** Test için saat kaynağı. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private static final int RING_CALLS_WINDOW_SIZE = 400;
    private static final int SUCCESSFUL_CALLS_WINDOW_SIZE = 400;
    private static final int STARTUP_SNAPSHOT_THRESHOLD = 100;
    private static final int STARTUP_SNAPSHOT_THRESHOLD_CALLBACK = 50;
    private static final int ACW_REFRESH_SUCCESSFUL_CALL_THRESHOLD = 200;
    private static final int MIN_TALK_TIME_SEC = 10;
    private static final double STARTUP_CALLBACK_ANSWER_RATE = 0.50d;
    private static final double STARTUP_RETRY_ANSWER_RATE = 0.40d;
    private static final double STARTUP_FRESH_ANSWER_RATE = 0.50d;
    private static final int DEFAULT_GAMMA_WINDOW_CAPACITY = 400;
    private static final double DEFAULT_STARTUP_AVG_TALK_TIME_SEC = 200.0d;
    private static final double DEFAULT_STARTUP_AVG_RING_TIME_SEC = 30.0d;
    /**
     * MADDE 4A — Gamma Plus'ta {@code avgRing} yalnız cevaplananlardan ölçülür; bu metriğin
     * varsayılanı da aynı tanımda olmalıdır.
     *
     * <p>Filtresiz sabit (30 sn) burada KULLANILAMAZ: kampanyanın dünkü verisi yoksa ufuk
     * 2 kattan fazla şişer ve tam en riskli anda (kampanya açılışı) fazla arz üretir — yani
     * maddenin düzeltmeye çalıştığı hatanın büyüğünü geri getirir. Ölçüm (Tcell, 21 kampanya):
     * cevaplanan ring ortalaması 11,7-16,0 sn, medyan ~13,6 sn. Güvenli taraf için üst banda
     * yakın 15 sn seçildi.
     */
    private static final double DEFAULT_STARTUP_AVG_RING_TIME_ANSWERED_SEC = 15.0d;
    private static final double DEFAULT_STARTUP_AVG_ACW_TIME_SEC = 0.0d;

    public void onCampaignStart(Campaign campaign) {
        if (campaign.getGammaWindowCapacity() == null || campaign.getGammaRequiredWindowSize() == null) {
            log.warn("[GAMMA] cid={} missing gamma config, skipping onCampaignStart.", campaign.getId());
            return;
        }

        long cid = campaign.getId();
        recentCbCallsMap.put(cid, new PredictiveGammaRecentCalls(campaign.getGammaWindowCapacity()));
        recentRetryCallsMap.put(cid, new PredictiveGammaRecentCalls(campaign.getGammaWindowCapacity()));
        recentFreshCallsMap.put(cid, new PredictiveGammaRecentCalls(campaign.getGammaWindowCapacity()));
        recentSuccessfulCallsMap.put(cid, new PredictiveGammaRecentCalls(SUCCESSFUL_CALLS_WINDOW_SIZE));
        recentRingCallsMap.put(cid, new PredictiveGammaRecentCalls(RING_CALLS_WINDOW_SIZE));

        PredictiveGammaStartupProfile startupProfile = buildStartupProfile(campaign);
        startupProfileMap.put(cid, startupProfile);
        cbAnswerRateMap.put(cid, startupProfile.getCbAnswerRate());
        retryAnswerRateMap.put(cid, startupProfile.getRetryAnswerRate());
        freshAnswerRateMap.put(cid, startupProfile.getFreshAnswerRate());
        lastAcwRefreshSuccessfulCountMap.put(cid, 0);

        log.info("GAMMA started for campaign {}", campaign.getId());
    }

    public void onCampaignStop(Campaign campaign) {
        long cid = campaign.getId();
        holtsTrendAnalyzer.reset(cid);
        recentCbCallsMap.remove(cid);
        recentRetryCallsMap.remove(cid);
        recentFreshCallsMap.remove(cid);
        recentSuccessfulCallsMap.remove(cid);
        recentRingCallsMap.remove(cid);
        cbAnswerRateMap.remove(cid);
        retryAnswerRateMap.remove(cid);
        freshAnswerRateMap.remove(cid);
        startupProfileMap.remove(cid);
        lastAcwRefreshSuccessfulCountMap.remove(cid);
        starvationCreditMap.remove(cid);
        log.info("GAMMA stopped for campaign {}", cid);
    }

    public void onCallEnded(Call call, int talkTimeSec, int ringTimeSec) {
        if (call == null || call.getCampaign() == null) return;
        Campaign campaign = call.getCampaign();
        final long cid = campaign.getId();

        if (campaign.getGammaWindowCapacity() == null || campaign.getGammaRequiredWindowSize() == null) {
            log.warn("[GAMMA] cid={} missing gamma config, skipping onCallEnded.", cid);
            return;
        }

        PredictiveGammaRecentCalls recentCbCalls = recentCbCallsMap.get(cid);
        PredictiveGammaRecentCalls recentRetryCalls = recentRetryCallsMap.get(cid);
        PredictiveGammaRecentCalls recentFreshCalls = recentFreshCallsMap.get(cid);
        PredictiveGammaRecentCalls recentSuccessfulCalls = recentSuccessfulCallsMap.get(cid);
        PredictiveGammaRecentCalls recentRingCalls = recentRingCallsMap.get(cid);

        if (recentCbCalls == null || recentRetryCalls == null || recentFreshCalls == null || recentSuccessfulCalls == null || recentRingCalls == null) {
            log.debug("[GAMMA] cid={} onCallEnded ignored, campaign already stopped or not initialized", cid);
            return;
        }

        PredictiveGammaCallModel recentCall = buildCallModel(call, talkTimeSec, ringTimeSec);

        GammaAnswerSample answerSample = call.getGammaAnswerSample();
        PredictiveGammaRecentCalls typeBuffer = resolveTypeBuffer(recentCall.getCallType(), recentCbCalls, recentRetryCalls, recentFreshCalls);
        if (answerSample != null && answerSample.isOwnedBy(typeBuffer)) {
            // MADDE 1 — bu çağrı AR penceresine cevap anında zaten girdi. İkinci kez eklemek
            // AR'yi yukarı saptırırdı (çift sayım). Bunun yerine mevcut kayıt sonuçlandırılır.
            finalizeAnswerSample(answerSample.getModel(), recentCall, typeBuffer);
        } else {
            // Örnek yok, VEYA kampanya bu arada stop/start olup buffer'lar değişti: eski
            // pencereye yazılmış kayıt artık kimseyi temsil etmiyor, çağrı normal yoldan
            // yeni pencereye eklenir. Aksi hâlde cevaplanan çağrı hiç sayılmaz ve AR aşağı
            // saparadı (güvensiz yön).
            addToTypeBuffer(recentCall, cid, recentCbCalls, recentRetryCalls, recentFreshCalls);
        }
        call.setGammaAnswerSample(null);

        // TRC 85 (Ambiguous) da bu pencereye girer: gorusme gerceklesti, yalniz agent'in sonuc
        // kodu eksik. Talk suresi AMI damgalarindan gelir, URC'den bagimsizdir -> gecerli bir
        // kapasite gozlemidir. Ayrinti ve gerekce: Constants.GAMMA_TALK_VALID_RESULT_CODES.
        // Ayni kume CDRDao.findGammaStartupAverageTalkTime'i da besler; ikisi AYRILMAMALIDIR.
        if (recentCall.isAnswered()
                && Constants.GAMMA_TALK_VALID_RESULT_CODES.contains((int) recentCall.getResultCode())
                && recentCall.getTalkTime() >= MIN_TALK_TIME_SEC) {
            recentSuccessfulCalls.add(recentCall);
            refreshAcwIfNeeded(campaign, recentSuccessfulCalls.getTotalCallCount());
        }

        if (recentCall.isAnswered() || Constants.RING_VALID_RESULT_CODES.contains((int) recentCall.getResultCode())) {
            recentRingCalls.add(recentCall);
        }

    }

    /**
     * MADDE 1 — Gamma Plus'ta müşteri cevabı geldiği anda çağrıyı AR penceresine yazar.
     *
     * <p><b>Sorun:</b> Bugün çağrı AR penceresine ancak hangup'ta giriyor. Cevapsız ~30 sn'de,
     * cevaplanan ~230 sn'de rapor ediliyor — 7 kat gecikme farkı. Hız artınca (taze liste,
     * vardiya başı) pencere hızlı raporlayan BAŞARISIZLIKLARLA dolar, ölçülen AR gerçeğin
     * altına düşer. Hata iki yerden birden girer: dial maliyeti düşer (aynı bütçeyle daha çok
     * numara) ve ringingLoad düşer (fren zayıflar). Üstüne pozitif geri besleme biner.
     *
     * <p><b>Çözüm:</b> sonuç belli olur olmaz yaz. Cevaplanan ~15 sn'de, cevapsız yine hangup'ta
     * (daha erken bilgi yok). Sapma ~7 kat küçülür ve yönü güvenli tarafa döner: hız artışında
     * AR yukarı sapar, yani sistem daha AZ arar. Kararlı halde hiçbir şey değişmez.
     *
     * <p><b>Neden ayrı model kurucu:</b> {@code buildCallModel} answer anında çalışamaz —
     * {@code cdr.getTelephonyRC()} yalnız finalize'da set edilir.
     *
     * <p>Yalnız tip buffer'ı (AR) taşınır. {@code recentSuccessfulCalls} (talkTime) ve
     * {@code recentRingCalls} (ringTime) doğası gereği end-time metrikleridir, yerinde kalır.
     */
    public void onCallAnswered(Call call) {
        if (call == null || call.getCampaign() == null) return;
        Campaign campaign = call.getCampaign();
        if (call.getGammaAnswerSample() != null) return; // aynı çağrı için ikinci answer event'i

        final long cid = campaign.getId();
        if (campaign.getGammaWindowCapacity() == null || campaign.getGammaRequiredWindowSize() == null) return;

        // Bayrak, kampanya START'ında mandallanan profilden okunur; canlı kampanya nesnesinden
        // değil. Böylece answer yolu ile tick yolu daima aynı bayrağı görür.
        PredictiveGammaStartupProfile profile = startupProfileMap.get(cid);
        if (profile == null || !profile.isGammaPlus()) return;

        PredictiveGammaRecentCalls recentCbCalls = recentCbCallsMap.get(cid);
        PredictiveGammaRecentCalls recentRetryCalls = recentRetryCallsMap.get(cid);
        PredictiveGammaRecentCalls recentFreshCalls = recentFreshCallsMap.get(cid);
        if (recentCbCalls == null || recentRetryCalls == null || recentFreshCalls == null) {
            log.debug("[GAMMA] cid={} onCallAnswered ignored, campaign already stopped or not initialized", cid);
            return;
        }

        PredictiveGammaCallModel sample = buildAnswerTimeCallModel(call);
        PredictiveGammaRecentCalls owner = resolveTypeBuffer(sample.getCallType(), recentCbCalls, recentRetryCalls, recentFreshCalls);
        // Önce buffer'a yaz, sonra çağrıya bağla: ters sırada bir hata olsaydı çağrı hiçbir
        // buffer'da olmayan bir örneğe işaret eder ve hangup'ta hiç sayılmazdı.
        owner.add(sample);
        call.setGammaAnswerSample(new GammaAnswerSample(sample, owner));
    }

    /**
     * Bu metot artık üretim dial yolunda DEĞİLDİR; {@code CallConsumer} gamma bütçesini
     * {@link #calculateDialBudget} üzerinden alır. Yalnız test/teşhis amaçlı korunur.
     * Çağrıldığında Holt state'ini ve AR map'lerini günceller — üretim akışından çağrılmamalıdır.
     */
    public int allowedDialNow(Campaign campaign, int cps, int remainingCapacity, DialerFacade facade) {
        return allowedDialNow(campaign, cps, remainingCapacity, facade, null);
    }

    public int allowedDialNow(Campaign campaign, int cps, int remainingCapacity, DialerFacade facade, Call nextCall) {
        GammaDialBudget budget = calculateDialBudget(campaign, cps, remainingCapacity, facade, nextCall);
        int xn = (int) Math.floor(budget.getTotalDialBudget());
        return (xn > 0) ? Math.min(xn, Math.min(cps, remainingCapacity)) : 0;
    }

    public GammaDialBudget calculateDialBudget(Campaign campaign, int cps, int remainingCapacity, DialerFacade facade, Call nextCall) {
        if (campaign.getGammaWindowCapacity() == null || campaign.getGammaRequiredWindowSize() == null) {
            log.warn("[GAMMA] cid={} missing gamma config, skipping allowedDialNow.", campaign.getId());
            return GammaDialBudget.zero();
        }

        long cid = campaign.getId();

        PredictiveGammaRecentCalls cbCalls    = recentCbCallsMap.get(cid);
        PredictiveGammaRecentCalls retryCalls = recentRetryCallsMap.get(cid);
        PredictiveGammaRecentCalls freshCalls = recentFreshCallsMap.get(cid);
        PredictiveGammaRecentCalls successfulCalls = recentSuccessfulCallsMap.get(cid);
        PredictiveGammaRecentCalls ringCalls = recentRingCallsMap.get(cid);
        PredictiveGammaStartupProfile startupProfile = startupProfileMap.get(cid);

        if (cbCalls == null || retryCalls == null || freshCalls == null || successfulCalls == null || ringCalls == null || startupProfile == null) {
            log.warn("[GAMMA] cid={} allowedDialNow called but campaign not initialized", cid);
            return GammaDialBudget.zero();
        }

        AgentMetricsDTO agentMetrics = sharedGammaSnapshotProvider.getAgentMetrics(campaign.getId());
        RingingMetricsDTO ringingMetrics = calculateRealTimeRinging(campaign, facade);

        PredictiveGammaSnapshot cbSnap = cbCalls.snapshot(campaign);
        PredictiveGammaSnapshot retrySnap = retryCalls.snapshot(campaign);
        PredictiveGammaSnapshot freshSnap = freshCalls.snapshot(campaign);
        PredictiveGammaSnapshot successfulSnap = successfulCalls.snapshot(campaign);
        PredictiveGammaSnapshot ringSnap = ringCalls.snapshot(campaign);

        PredictiveGammaCalculationResult result = calculator.calculateXn(campaign, agentMetrics, ringingMetrics, cbSnap, retrySnap, freshSnap, successfulSnap, ringSnap, startupProfile, STARTUP_SNAPSHOT_THRESHOLD, STARTUP_SNAPSHOT_THRESHOLD_CALLBACK);

        double baseDialBudget = result.getXn();

        cbAnswerRateMap.put(cid, result.getCbAnswerRate());
        retryAnswerRateMap.put(cid, result.getRetryAnswerRate());
        freshAnswerRateMap.put(cid, result.getFreshAnswerRate());

        double nextCallCost = sanitizeAnswerRateCost(getAnswerRateCost(nextCall, cid));
        boolean sharedCampaign = sharedGammaSnapshotProvider.isSharedCampaign(cid);
        double creditBefore = starvationCreditMap.getOrDefault(cid, 0.0);
        boolean starvationEligible = isStarvationEligible(campaign, baseDialBudget, nextCallCost, cps, remainingCapacity, ringingMetrics, sharedCampaign);
        double boost = calculateStarvationBoost(cid, baseDialBudget, nextCallCost, cps, remainingCapacity);
        PredictiveGammaCallData callData = result.getCallData();
        if (callData != null) {
            callData.setIsSharedCampaign(sharedCampaign);
            callData.setNextCallType(resolveCallTypeName(nextCall));
            callData.setNextCallCost(nextCallCost);
            callData.setStarvationEligible(starvationEligible);
            callData.setStarvationApplied(boost > 0.0);
            callData.setStarvationCreditBefore(creditBefore);
            callData.setStarvationBoost(boost);
            callData.setStarvationCreditAfter(creditBefore);
            callData.setTotalDialBudget(Math.max(0.0, baseDialBudget) + Math.max(0.0, boost));
        }

        return new GammaDialBudget(baseDialBudget, boost, nextCallCost, callData, result.getRingingLoadModel());
    }

    public double calculateStarvationBoost(long cid, double baseDialBudget, double nextCallCost, int cps, int remainingCapacity) {
        if (!(baseDialBudget > 0.0)) return 0.0;
        if (cps <= 0 || remainingCapacity <= 0 || nextCallCost <= 0.0) return 0.0;
        double missingBudget = Math.max(0.0, nextCallCost - Math.max(0.0, baseDialBudget));
        if (missingBudget <= 0.0) return 0.0;

        Double credit = starvationCreditMap.getOrDefault(cid, 0.0);
        if (credit + EPSILON < missingBudget) return 0.0;

        return missingBudget;
    }

    public double consumeStarvationBoost(long cid, double freshBudget, double nextCallCost, int cps, int remainingCapacity) {
        double boost = calculateStarvationBoost(cid, freshBudget, nextCallCost, cps, remainingCapacity);
        if (boost <= 0.0) return 0.0;

        Double credit = starvationCreditMap.getOrDefault(cid, 0.0);
        starvationCreditMap.put(cid, Math.max(0.0, credit - boost));
        return boost;
    }

    public void updateStarvationCredit(Campaign campaign, double freshBudget, double nextCallCost, int drainedCount, DialerFacade facade) {
        if (campaign == null) return;
        long cid = campaign.getId();
        if (drainedCount > 0 || !(freshBudget > 0.0) || !(nextCallCost > 0.0) || freshBudget >= nextCallCost
                || !sharedGammaSnapshotProvider.isSharedCampaign(cid) || campaign.getCacheSize() <= 0) {
            starvationCreditMap.put(cid, 0.0);
            return;
        }

        int queueSize = campaign.getQueueSize();
        if (queueSize > 0) {
            starvationCreditMap.put(cid, 0.0);
            return;
        }

        try {
            RingingMetricsDTO ringingMetrics = facade.getCampaignRealTimeRingingManager().getRealTimeRingingMetrics(campaign);
            if (ringingMetrics != null && ringingMetrics.getTotalRinging() == 0) {
                starvationCreditMap.put(cid, starvationCreditMap.getOrDefault(cid, 0.0) + freshBudget);
            } else {
                starvationCreditMap.put(cid, 0.0);
            }
        } catch (Exception e) {
            log.warn("[GAMMA-STARVATION] Credit update failed. Campaign: " + cid, e);
            starvationCreditMap.put(cid, 0.0);
        }
    }

    public void dispatchEvent(PredictiveGammaCallData data) {
        calculator.dispatchEvent(data);
    }

    public double getStarvationCredit(long cid) {
        return starvationCreditMap.getOrDefault(cid, 0.0);
    }

    private RingingMetricsDTO calculateRealTimeRinging(Campaign campaign, DialerFacade facade) {
        return facade.getCampaignRealTimeRingingManager().getRealTimeRingingMetrics(campaign);
    }

    public double getAnswerRateCost(Call call, long cid) {
        if (call == null || call.getCallType() == null) return freshAnswerRateMap.getOrDefault(cid, getStartupAnswerRate(cid, Constants.CALL_TYPES.FRESH));
        switch (call.getCallType()) {
            case CALLBACK: return cbAnswerRateMap.getOrDefault(cid, getStartupAnswerRate(cid, Constants.CALL_TYPES.CALLBACK));
            case RETRY: return retryAnswerRateMap.getOrDefault(cid, getStartupAnswerRate(cid, Constants.CALL_TYPES.RETRY));
            case FRESH: return freshAnswerRateMap.getOrDefault(cid, getStartupAnswerRate(cid, Constants.CALL_TYPES.FRESH));
            default: return 1.0;
        }
    }

    private double sanitizeAnswerRateCost(double cost) {
        return Double.isFinite(cost) && cost > 0.0 ? cost : 1.0;
    }

    private boolean isStarvationEligible(Campaign campaign, double baseDialBudget, double nextCallCost, int cps, int remainingCapacity, RingingMetricsDTO ringingMetrics, boolean sharedCampaign) {
        return sharedCampaign
                && baseDialBudget > 0.0
                && nextCallCost > 0.0
                && baseDialBudget < nextCallCost
                && cps > 0
                && remainingCapacity > 0
                && campaign.getCacheSize() > 0
                && campaign.getQueueSize() <= 0
                && ringingMetrics != null
                && ringingMetrics.getTotalRinging() == 0;
    }

    private String resolveCallTypeName(Call nextCall) {
        Constants.CALL_TYPES callType = (nextCall != null && nextCall.getCallType() != null) ? nextCall.getCallType() : Constants.CALL_TYPES.FRESH;
        return callType.name();
    }

    public static class GammaDialBudget {
        private final double baseDialBudget;
        private final double starvationBoost;
        private final double nextCallCost;
        private final PredictiveGammaCallData callData;
        private final GammaRingingLoadModel ringingLoadModel;

        private GammaDialBudget(double baseDialBudget, double starvationBoost, double nextCallCost, PredictiveGammaCallData callData) {
            this(baseDialBudget, starvationBoost, nextCallCost, callData, null);
        }

        private GammaDialBudget(double baseDialBudget, double starvationBoost, double nextCallCost, PredictiveGammaCallData callData, GammaRingingLoadModel ringingLoadModel) {
            this.baseDialBudget = baseDialBudget;
            this.starvationBoost = starvationBoost;
            this.nextCallCost = nextCallCost;
            this.callData = callData;
            this.ringingLoadModel = ringingLoadModel;
        }

        /**
         * MADDE 3A — çıkan partiyi marjinal UCB ile fiyatlayan model. Gamma Plus KAPALI iken
         * {@code null}; drain o zaman bugünkü sabit {@code AR_dial} maliyetini kullanır.
         */
        public GammaRingingLoadModel getRingingLoadModel() {
            return ringingLoadModel;
        }

        private static GammaDialBudget zero() {
            return new GammaDialBudget(0.0, 0.0, 1.0, null);
        }

        public double getBaseDialBudget() {
            return baseDialBudget;
        }

        public double getStarvationBoost() {
            return starvationBoost;
        }

        public double getNextCallCost() {
            return nextCallCost;
        }

        public PredictiveGammaCallData getCallData() {
            return callData;
        }

        public double getTotalDialBudget() {
            return Math.max(0.0, baseDialBudget) + Math.max(0.0, starvationBoost);
        }
    }

    private PredictiveGammaStartupProfile buildStartupProfile(Campaign campaign) {
        long campaignId = campaign.getId();
        boolean gammaPlus = campaign.isGammaPlusEnabled();
        double defaultAvgRing = gammaPlus ? DEFAULT_STARTUP_AVG_RING_TIME_ANSWERED_SEC : DEFAULT_STARTUP_AVG_RING_TIME_SEC;
        try {
            YesterdayAverages averages = resolveYesterdayAverages(campaignId, gammaPlus);
            Double avgTalk = averages.avgTalk;
            Double avgRing = averages.avgRing;
            Double avgAcw = averages.avgAcw;

            // Gun-ici stop/start senaryosu: bugune ait gercek AR varsa warm-up cezasi olmadan
            // gercek deger ile baslat; yetersiz veride guvenli sabite dus (yalniz BUGUN, gecmis gun karistirilmaz).
            TodayAnswerStats todayStats = loadTodayAnswerStats(campaignId);
            int fullThreshold = campaign.getGammaWindowCapacity() != null
                    ? campaign.getGammaWindowCapacity() : DEFAULT_GAMMA_WINDOW_CAPACITY;
            int callbackThreshold = Math.max(1, fullThreshold / 2);

            double cbAr = resolveStartupAnswerRate(todayStats.cbTotal, todayStats.cbAnswered, callbackThreshold, STARTUP_CALLBACK_ANSWER_RATE);
            double retryAr = resolveStartupAnswerRate(todayStats.retryTotal, todayStats.retryAnswered, fullThreshold, STARTUP_RETRY_ANSWER_RATE);
            double freshAr = resolveStartupAnswerRate(todayStats.freshTotal, todayStats.freshAnswered, fullThreshold, STARTUP_FRESH_ANSWER_RATE);

            PredictiveGammaStartupProfile profile = new PredictiveGammaStartupProfile(
                    cbAr,
                    retryAr,
                    freshAr,
                    sanitizeAverage(avgTalk, DEFAULT_STARTUP_AVG_TALK_TIME_SEC),
                    sanitizeAverage(avgRing, defaultAvgRing),
                    sanitizeAverage(avgAcw, DEFAULT_STARTUP_AVG_ACW_TIME_SEC),
                    gammaPlus
            );

            log.info("[GAMMA] cid={} startup profile prepared: cbAr={}, retryAr={}, freshAr={}, avgTalk={}, avgRing={}, avgAcw={}, todayCb={}/{}, todayRetry={}/{}, todayFresh={}/{}, fullThreshold={}, cbThreshold={}",
                    campaignId, profile.getCbAnswerRate(), profile.getRetryAnswerRate(), profile.getFreshAnswerRate(),
                    profile.getAvgTalkTime(), profile.getAvgRingTime(), profile.getAvgAcwTime(),
                    todayStats.cbAnswered, todayStats.cbTotal, todayStats.retryAnswered, todayStats.retryTotal,
                    todayStats.freshAnswered, todayStats.freshTotal, fullThreshold, callbackThreshold);

            return profile;
        } catch (Exception e) {
            log.error("[GAMMA] cid={} startup profile query failed, fallback defaults will be used", campaignId, e);
            return new PredictiveGammaStartupProfile(
                    STARTUP_CALLBACK_ANSWER_RATE,
                    STARTUP_RETRY_ANSWER_RATE,
                    STARTUP_FRESH_ANSWER_RATE,
                    DEFAULT_STARTUP_AVG_TALK_TIME_SEC,
                    defaultAvgRing,
                    DEFAULT_STARTUP_AVG_ACW_TIME_SEC,
                    gammaPlus
            );
        }
    }

    /**
     * FIX-333607: dünkü ortalamaları önbellekten verir; yoksa veya gün değiştiyse sorgular. Her sorgu
     * ayrı korunur: biri hata verirse yalnız o değer {@code null} (startup varsayılanı) olur. Konuşma
     * veya çalma ortalaması yoksa (veri yok, timeout, hata) giriş önbelleğe alınmaz; sonraki start
     * yeniden dener. ACW sorgusu zaman aşımında 0 döner; ACW önbelleğe yalnız sıfırdan büyükse girer,
     * değilse sonraki start (eskiden olduğu gibi) yalnız ACW'yi yeniden sorgular.
     */
    private YesterdayAverages resolveYesterdayAverages(long campaignId, boolean gammaPlus) {
        LocalDate today = LocalDate.now(clock);
        YesterdayAverages cached = yesterdayAveragesCache.get(campaignId);
        if (cached != null && cached.day.equals(today) && cached.gammaPlus == gammaPlus) {
            if (cached.avgAcw != null) {
                return cached;
            }
            Double avgAcw = queryStartupValue(campaignId, "ACW",
                    () -> cdrService.findGammaStartupAverageAcwTime(campaignId));
            yesterdayAveragesCache.put(campaignId, new YesterdayAverages(today, gammaPlus, cached.avgTalk, cached.avgRing, cacheableAcw(avgAcw)));
            return new YesterdayAverages(today, gammaPlus, cached.avgTalk, cached.avgRing, avgAcw);
        }
        Double avgTalk = queryStartupValue(campaignId, "talk time",
                () -> cdrService.findGammaStartupAverageTalkTime(campaignId, MIN_TALK_TIME_SEC));
        // MADDE 4A — warm-up profili de canlı yolla aynı tanımı kullanmalı. Yalnız canlı yol
        // düzeltilirse startup avgRing şişik kalır ve gün başında yanlış ufuk uygulanır.
        // Sorgu SAYISI değişmez: bayrağa göre iki varyanttan biri çalışır, ikisi birden değil.
        Double avgRing = queryStartupValue(campaignId, "ring time",
                () -> gammaPlus
                        ? cdrService.findGammaStartupAverageRingTimeAnswered(campaignId)
                        : cdrService.findGammaStartupAverageRingTime(campaignId));
        Double avgAcw = queryStartupValue(campaignId, "ACW",
                () -> cdrService.findGammaStartupAverageAcwTime(campaignId));
        if (avgTalk != null && avgRing != null) {
            yesterdayAveragesCache.put(campaignId, new YesterdayAverages(today, gammaPlus, avgTalk, avgRing, cacheableAcw(avgAcw)));
        }
        return new YesterdayAverages(today, gammaPlus, avgTalk, avgRing, avgAcw);
    }

    /** Zaman aşımı (0) veya veri yok (null) önbelleğe alınmaz. */
    private static Double cacheableAcw(Double avgAcw) {
        return avgAcw != null && avgAcw > 0.0 ? avgAcw : null;
    }

    private Double queryStartupValue(long campaignId, String name, Supplier<Double> query) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            log.error("[GAMMA] cid={} startup {} query failed, startup default will be used for this value", campaignId, name, e);
            return null;
        }
    }

    private static final class YesterdayAverages {
        private final LocalDate day;
        private final boolean gammaPlus;
        private final Double avgTalk;
        private final Double avgRing;
        private final Double avgAcw;

        private YesterdayAverages(LocalDate day, boolean gammaPlus, Double avgTalk, Double avgRing, Double avgAcw) {
            this.day = day;
            this.gammaPlus = gammaPlus;
            this.avgTalk = avgTalk;
            this.avgRing = avgRing;
            this.avgAcw = avgAcw;
        }
    }

    /**
     * Bugune ait tip bazli toplam ve cevaplanan cagri sayisini CDR'den ceker ve FRESH/RETRY/CALLBACK'e indirir.
     * outdialing_type_id: 1=FRESH, 2/3=RETRY, 4=CALLBACK ({@link Constants.CDR_OUTDIALING_TYPE}).
     * Veri yoksa veya satir cozulemezse ilgili sayaclar 0 kalir; cagiran taraf sabite duser.
     */
    private TodayAnswerStats loadTodayAnswerStats(long campaignId) {
        TodayAnswerStats stats = new TodayAnswerStats();
        List<Object[]> rows = cdrService.findGammaTodayAnswerStatsByType(campaignId);
        if (rows == null) {
            return stats;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }
            long typeId = ((Number) row[0]).longValue();
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            long answered = row[2] != null ? ((Number) row[2]).longValue() : 0L;

            if (typeId == Constants.CDR_OUTDIALING_TYPE.CALLBACK.getId()) {
                stats.cbTotal += total;
                stats.cbAnswered += answered;
            } else if (typeId == Constants.CDR_OUTDIALING_TYPE.IMMEDIATE_RETRY.getId()
                    || typeId == Constants.CDR_OUTDIALING_TYPE.SCHEDULED_RETRY.getId()) {
                stats.retryTotal += total;
                stats.retryAnswered += answered;
            } else if (typeId == Constants.CDR_OUTDIALING_TYPE.NEW.getId()) {
                stats.freshTotal += total;
                stats.freshAnswered += answered;
            }
        }
        return stats;
    }

    /**
     * Bugune ait yeterli ornek (>= threshold) varsa gercek gunluk AR'yi (answered/total) doner;
     * aksi halde guvenli sabite duser.
     */
    private double resolveStartupAnswerRate(long total, long answered, int threshold, double fallbackConstant) {
        if (total >= threshold && total > 0) {
            return (double) answered / total;
        }
        return fallbackConstant;
    }

    private static class TodayAnswerStats {
        long cbTotal = 0, cbAnswered = 0;
        long retryTotal = 0, retryAnswered = 0;
        long freshTotal = 0, freshAnswered = 0;
    }

    private void refreshAcwIfNeeded(Campaign campaign, int totalSuccessfulCount) {
        long cid = campaign.getId();
        int lastRefreshSuccessfulCount = lastAcwRefreshSuccessfulCountMap.getOrDefault(cid, 0);
        if (totalSuccessfulCount - lastRefreshSuccessfulCount < ACW_REFRESH_SUCCESSFUL_CALL_THRESHOLD) {
            return;
        }

        PredictiveGammaStartupProfile currentProfile = startupProfileMap.get(cid);
        if (currentProfile == null) {
            return;
        }

        try {
            Double currentAvgAcw = cdrService.findGammaCurrentAverageAcwTime(campaign.getId());
            if (currentAvgAcw == null || !Double.isFinite(currentAvgAcw) || currentAvgAcw < 0.0) {
                lastAcwRefreshSuccessfulCountMap.put(cid, totalSuccessfulCount);
                return;
            }

            startupProfileMap.put(cid, new PredictiveGammaStartupProfile(
                    currentProfile.getCbAnswerRate(),
                    currentProfile.getRetryAnswerRate(),
                    currentProfile.getFreshAnswerRate(),
                    currentProfile.getAvgTalkTime(),
                    currentProfile.getAvgRingTime(),
                    currentAvgAcw,
                    currentProfile.isGammaPlus()
            ));
            lastAcwRefreshSuccessfulCountMap.put(cid, totalSuccessfulCount);

            log.info("[GAMMA] cid={} avgAcw refreshed to {} at successfulCount={}", cid, currentAvgAcw, totalSuccessfulCount);
        } catch (Exception e) {
            lastAcwRefreshSuccessfulCountMap.put(cid, totalSuccessfulCount);
            log.error("[GAMMA] cid={} avgAcw refresh failed at successfulCount={}", cid, totalSuccessfulCount, e);
        }
    }

    private double sanitizeAverage(Double value, double fallback) {
        return value != null && Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private double getStartupAnswerRate(long campaignId, Constants.CALL_TYPES callType) {
        PredictiveGammaStartupProfile profile = startupProfileMap.get(campaignId);
        if (profile == null) {
            return 1.0;
        }

        switch (callType) {
            case CALLBACK:
                return profile.getCbAnswerRate();
            case RETRY:
                return profile.getRetryAnswerRate();
            case FRESH:
                return profile.getFreshAnswerRate();
            default:
                return 1.0;
        }
    }

    private PredictiveGammaCallModel buildCallModel(Call call, int talkTimeSec, int ringTimeSec) {
        PredictiveGammaCallModel m = new PredictiveGammaCallModel();
        m.setCdrId(resolveCdrId(call));
        m.setResultCode(resolveResultCode(call));
        m.setAnswered(call.getAnswerDate() != null);
        m.setAbandoned(call.isAbandoned());
        m.setTalkTime(talkTimeSec);
        m.setRingTime(ringTimeSec);
        m.setCallType(call.getCallType());
        return m;
    }

    /**
     * Cevap anındaki kayıt: RC henüz yok (yalnız finalize'da set edilir), talk/ring süreleri
     * henüz belirsiz. Tip buffer'ı bu alanları kullanmaz; AR yalnız answered/total oranıdır,
     * "fiilen çaldı" testi de {@code isAnswered} ile kısa devre olur.
     */
    private PredictiveGammaCallModel buildAnswerTimeCallModel(Call call) {
        PredictiveGammaCallModel m = new PredictiveGammaCallModel();
        m.setCdrId(resolveCdrId(call));
        m.setAnswered(true);
        m.setAbandoned(false);
        m.setCallType(call.getCallType());
        return m;
    }

    /**
     * MADDE 1 — cevap anında yazılmış kaydı hangup bilgisiyle tamamlar.
     *
     * <p>Önce RC yazılır (abandon'un sistem/müşteri kırılımı ona bakar), sonra gerekiyorsa
     * abandon damgası vurulur. {@code PredictiveGammaCallModel} alanları Atomic olduğu için
     * eşzamanlı snapshot okuması güvenlidir.
     *
     * <p>Kayıt pencereden düşmüşse damga sessizce atlanır (sayaç zaten o kaydı temsil etmiyor).
     */
    private void finalizeAnswerSample(PredictiveGammaCallModel sample, PredictiveGammaCallModel finalModel, PredictiveGammaRecentCalls owner) {
        sample.setResultCode(finalModel.getResultCode());
        sample.setTalkTime(finalModel.getTalkTime());
        sample.setRingTime(finalModel.getRingTime());

        if (!finalModel.isAbandoned()) return;

        owner.markAbandoned(sample);
    }

    /**
     * {@code telephony_rc} NULL olabilen bir kolondur (ölçüm: çağrıların ~%0,04'ü). Eski kod
     * burada NPE atıyordu; exception {@code notifyGammaOnCallEnded} tarafından yutulup
     * loglanıyor, ama o çağrı gamma pencerelerinin HİÇBİRİNE girmiyordu — sessiz veri kaybı.
     * Bayraktan bağımsız düzeltme; RC yoksa 0 yazılır ("çalmadı" tarafında kalır).
     */
    private long resolveResultCode(Call call) {
        if (call.getCdr() == null || call.getCdr().getTelephonyRC() == null) {
            log.debug("[GAMMA] cid={} call has no telephony result code, resultCode=0 assumed", call.getCampaign() != null ? call.getCampaign().getId() : null);
            return 0L;
        }
        return call.getCdr().getTelephonyRC().getId();
    }

    private long resolveCdrId(Call call) {
        return (call.getCdr() != null && call.getCdr().getId() != null) ? call.getCdr().getId() : 0L;
    }

    private PredictiveGammaRecentCalls resolveTypeBuffer(Constants.CALL_TYPES callType, PredictiveGammaRecentCalls cbCalls,
                                                         PredictiveGammaRecentCalls retryCalls, PredictiveGammaRecentCalls freshCalls) {
        if (callType == null) return freshCalls;
        switch (callType) {
            case CALLBACK: return cbCalls;
            case RETRY: return retryCalls;
            case FRESH: return freshCalls;
            default: return freshCalls;
        }
    }

    private void addToTypeBuffer(PredictiveGammaCallModel recentCall, long cid, PredictiveGammaRecentCalls cbCalls, PredictiveGammaRecentCalls retryCalls, PredictiveGammaRecentCalls freshCalls) {
        if (recentCall.getCallType() == null) return;

        switch (recentCall.getCallType()) {
            case CALLBACK: cbCalls.add(recentCall); break;
            case RETRY: retryCalls.add(recentCall); break;
            case FRESH: freshCalls.add(recentCall); break;
            default:
                freshCalls.add(recentCall);
                log.warn("[GAMMA] cid={} unknown callType={}, added to freshCalls", cid, recentCall.getCallType());
                break;
        }
    }
}
