package com.powerdialer.engine.predictive.gamma.model;

import lombok.Getter;

@Getter
public class PredictiveGammaCalculationResult {
    private final double xn;
    private final double cbAnswerRate;
    private final double retryAnswerRate;
    private final double freshAnswerRate;
    private final PredictiveGammaCallData callData;

    /**
     * Gamma Plus (MADDE 3A) marjinal drain fiyatlaması için kurulan borç modeli.
     * Bayrak KAPALI iken daima {@code null}; drain o zaman bugünkü {@code AR_dial} maliyetini kullanır.
     */
    private final GammaRingingLoadModel ringingLoadModel;

    public PredictiveGammaCalculationResult(double xn, double cbAnswerRate, double retryAnswerRate, double freshAnswerRate) {
        this(xn, cbAnswerRate, retryAnswerRate, freshAnswerRate, null, null);
    }

    public PredictiveGammaCalculationResult(double xn, double cbAnswerRate, double retryAnswerRate, double freshAnswerRate, PredictiveGammaCallData callData) {
        this(xn, cbAnswerRate, retryAnswerRate, freshAnswerRate, callData, null);
    }

    public PredictiveGammaCalculationResult(double xn, double cbAnswerRate, double retryAnswerRate, double freshAnswerRate, PredictiveGammaCallData callData, GammaRingingLoadModel ringingLoadModel) {
        this.xn = xn;
        this.cbAnswerRate = cbAnswerRate;
        this.retryAnswerRate = retryAnswerRate;
        this.freshAnswerRate = freshAnswerRate;
        this.callData = callData;
        this.ringingLoadModel = ringingLoadModel;
    }

    public static PredictiveGammaCalculationResult zero() { return new PredictiveGammaCalculationResult(0.0, 0.0, 0.0, 0.0); }
}
