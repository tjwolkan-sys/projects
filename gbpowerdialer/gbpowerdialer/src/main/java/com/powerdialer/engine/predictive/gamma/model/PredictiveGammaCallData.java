package com.powerdialer.engine.predictive.gamma.model;

import com.powerdialer.model.Campaign;
import lombok.Data;

import java.util.Date;

@Data
public class PredictiveGammaCallData {
    private String snapshotId;
    private final Campaign campaign;
    private final PredictiveGammaCallModel recentCall;

    private Date calculationIntervalStartTime;
    private Double totalAgentsCount;
    private Double physicalTotalAgentsCount;
    private Double availableAgentsCount;
    private Double incallAgentsCount;
    private Double acwAgentsCount;
    private Double productionLineAgentCount;
    private Double holtTrend;
    private Double holtLevel;
    private Long holtLastUpdateMs;
    private Double expectedAgentChange;
    private Double effectiveAvailable;
    private Integer answeredCallCount;
    private Integer abandonedCallCount;
    private Integer totalCallCount;
    private Integer totalAbandonCallCount;
    private Integer totalAbandonedByCustomer;
    private Integer totalAbandonedBySystem;
    private Integer ringingCount;
    private Integer ringingCbCount;
    private Integer ringingRetryCount;
    private Integer ringingFreshCount;
    private Integer pendingRingingCount;
    private Integer realtimeOnlyRingingCount;
    private Long pendingRingingMaxAgeMs;
    private Integer cbAnsweredCount;
    private Integer cbAbandonedCount;
    private Integer cbSampleCount;
    private Double cbAnswerRate;
    private Double cbStartupAnswerRate;
    private Double cbLiveAnswerRate;
    private Double cbAnswerRateBlendWeight;
    private Integer retryAnsweredCount;
    private Integer retryAbandonedCount;
    private Integer retrySampleCount;
    private Double retryAnswerRate;
    private Double retryStartupAnswerRate;
    private Double retryLiveAnswerRate;
    private Double retryAnswerRateBlendWeight;
    private Integer freshAnsweredCount;
    private Integer freshAbandonedCount;
    private Integer freshSampleCount;
    private Double freshAnswerRate;
    private Double freshStartupAnswerRate;
    private Double freshLiveAnswerRate;
    private Double freshAnswerRateBlendWeight;
    private Double computedAnswerRate; //TODO remove
    private Double computedBoostRate; //TODO remove
    private Double computedAbandonRate;
    private Double computedAbandonPenalty;
    private Double avgTalkTime;
    private Double avgRingTime;
    private Double avgAcwTime;
    private Integer currentWindowSize; //TODO remove
    private Integer currentSuccesfulWindowSize;
    private Integer currentRingWindowSize;
    private Double correctionFactor;
    private Double configuredCorrectionFactor;
    private Double correctionFactorScale;
    private Double rawXn;
    private Double ringingLoad;
    private Double ringingLoadMean;
    private Double ringingLoadSafe;
    private Double ringingLoadConfidenceK;
    private Double xn;

    // --- Gamma Plus izlenebilirlik alanlari ---
    // gammaPlus DAIMA dolar (A/B icin kol ayirt edici). Digerleri yalniz bayrak acikken
    // dolar; bayrak kapali event'te null kalirlar ve "bu kolda hesaplanmiyor" demektir.
    /** Bu kampanya Gamma Plus kolunda mi (START'ta mandallanmis deger). */
    private Boolean gammaPlus;
    /** MADDE 2A: ringingLoad carpani olarak kullanilan "acti/CALDI" orani. cbAnswerRate'e bolununce sapma cikar. */
    private Double cbRingAnswerRate;
    private Double retryRingAnswerRate;
    private Double freshRingAnswerRate;
    /** MADDE 2B: yas agirlikli calan hat toplami. ringingCount'a bolununce havuzun "erime" orani cikar. */
    private Double ringingWeightedCount;
    private Double ringingCbWeightedCount;
    private Double ringingRetryWeightedCount;
    private Double ringingFreshWeightedCount;
    /** MADDE 3A: bu tick'te cikan partiye yazilan toplam guvenlik payi = UCB(R+B) - UCB(R). */
    private Double drainMarginalCost;
    /** MADDE 3A: ilk cagri ortalama fiyat tabaniyla mi gecti (kampanya butce sinirinda). */
    private Boolean drainFloorApplied;
    private Boolean isSharedCampaign;
    private String nextCallType;
    private Double nextCallCost;
    private Boolean starvationEligible;
    private Boolean starvationApplied;
    private Double starvationCreditBefore;
    private Double starvationBoost;
    private Double starvationCreditAfter;
    private Double totalDialBudget;
    private Integer drainedCallCount;
    private Integer maxCallCount;
    private Integer remainingCapacity;
    private Integer cacheSize;
    private Integer queueSize;
}

