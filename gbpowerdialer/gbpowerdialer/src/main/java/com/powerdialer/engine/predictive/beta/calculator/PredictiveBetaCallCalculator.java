package com.powerdialer.engine.predictive.beta.calculator;

import com.powerdialer.engine.predictive.beta.model.PeriodicCallData;
import com.powerdialer.engine.predictive.beta.model.PredictiveCallParameters;
import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PredictiveBetaCallCalculator {

    private static final Logger log = LoggerFactory.getLogger(PredictiveBetaCallCalculator.class);
    private final float PHI = 0.1f;

    /**
     * Calculate the Predictive call parameters based on current and previous data.
     * Parameters are calculated in this order: c1, β1, β2, γ1, γ2, α1
     *
     * @param currentData current period's call data
     * @param previousData previous period's call data
     * @param availableAgents number of available agents in the system
     * @return the all the parameters along with X[n] value
     */
    public PredictiveCallParameters calculatePredictiveParameters(int availableAgents, PredictiveCampaignCallData campaignCallData) {

        campaignCallData.getCampaign().setAvailableUsersCount(availableAgents);
        if(availableAgents == 0) {
            log.debug("Available agents is zero, cannot calculate Predictive parameters");
            return null;
        }

        PeriodicCallData currentData = campaignCallData.getCurrentData();
        PeriodicCallData previousData = campaignCallData.getPreviousData();

        if (previousData == null) {
            log.debug("Previous data is null, cannot calculate Predictive parameters");
            return null;
        }

        // Extract data from current and previous periods
        int currentNoCon = currentData.getNoConnectionCallCount().get();
        int currentInCalls = currentData.getInCallCount().get();
        int currentActiveCalls = currentData.getActiveCallCount().get();
        int currentOfferedCalls = currentData.getOfferedCallCount().get();
        int currentAbandonedCalls = currentData.getAbandonedCallCount().get();
        int currentBadConnectionCalls = currentData.getBadConnectionCallCount().get();
        float currentNewCalls = currentData.getTotalNumberOfCalls();

        int prevRingCalls = previousData.getRingingCallCount().get();
        int prevNoCon = previousData.getNoConnectionCallCount().get();
        int prevInCalls = previousData.getInCallCount().get();
        int prevActiveCalls = previousData.getActiveCallCount().get();
        int prevOfferedCalls = previousData.getOfferedCallCount().get();
        int prevAbandonedCalls = previousData.getAbandonedCallCount().get();
        int prevBadConnectionCalls = previousData.getBadConnectionCallCount().get();

        // ====== Previous Parameters from Campaign ======
        float previousAlpha1 = campaignCallData.getPreviousAlpha1();
        float previousBeta1 = campaignCallData.getPreviousBeta1();
        float previousBeta2 = campaignCallData.getPreviousBeta2();
        float previousGamma1 = campaignCallData.getPreviousGamma1();
        float previousGamma2 = campaignCallData.getPreviousGamma2();
        float previousC1 = campaignCallData.getPreviousC1();

        // ====== Calculate WI[n-1] ======
        float previousWI = currentInCalls + prevOfferedCalls - prevInCalls;

        // ====== Calculate RW[n-1] ======
        float previousRW = currentActiveCalls + prevAbandonedCalls + previousWI - prevActiveCalls;

        // ====== Calculate C1 ======
        float currentC1 = (currentNewCalls > 0) ? applyControlFactor(previousC1, (currentBadConnectionCalls - prevBadConnectionCalls) / currentNewCalls) : previousC1;

        // ====== Calculate Beta2 ======
        float currentBeta2 = (prevRingCalls > 0) ? applyControlFactor(previousBeta2, (float)(currentNoCon - prevNoCon) / prevRingCalls) : previousBeta2;

        // ====== Calculate Beta1 ======
        float currentBeta1 = (prevRingCalls > 0) ? applyControlFactor(previousBeta1, previousRW / prevRingCalls) : previousBeta1;

        // ====== Calculate Gamma2 ======
        float currentGamma2 = (prevActiveCalls + previousRW > 0) ? applyControlFactor(previousGamma2, previousWI / (prevActiveCalls + previousRW)) : previousGamma2;

        // ====== Calculate Gamma1 ======
        float currentGamma1 = (prevActiveCalls + previousRW > 0) ? applyControlFactor(previousGamma1, (float)(currentAbandonedCalls - prevAbandonedCalls) / (prevActiveCalls + previousRW)) : previousGamma1;

        // ====== Calculate Alpha1 ======
        float currentAlpha1 = (prevInCalls > 0) ? applyControlFactor(previousAlpha1, (float)(currentOfferedCalls - prevOfferedCalls) / prevInCalls) : previousAlpha1;

        // ====== Calculate Xn ======
        float numerator = (currentBeta1 + currentBeta2) * currentAlpha1 * (currentGamma1 + currentGamma2) * availableAgents;
        float denominator = (1 - currentC1) * (currentGamma2 * currentBeta1 + currentGamma1);

        float xn = (denominator != 0) ? numerator / denominator : 0f;

        if (!Float.isFinite(xn) || xn <= 0) {
            xn = availableAgents;
        }

        // ===== Update Recursive State =====
        campaignCallData.setPreviousAlpha1(currentAlpha1);
        campaignCallData.setPreviousBeta1(currentBeta1);
        campaignCallData.setPreviousBeta2(currentBeta2);
        campaignCallData.setPreviousGamma1(currentGamma1);
        campaignCallData.setPreviousGamma2(currentGamma2);
        campaignCallData.setPreviousC1(currentC1);

        // ===== Build Parameter DTO =====
        PredictiveCallParameters params = new PredictiveCallParameters();
        params.setPreviousAlpha1(previousAlpha1);
        params.setPreviousBeta1(previousBeta1);
        params.setPreviousBeta2(previousBeta2);
        params.setPreviousGamma1(previousGamma1);
        params.setPreviousGamma2(previousGamma2);
        params.setPreviousC1(previousC1);

        params.setPreviousWI(previousWI);
        params.setPreviousRW(previousRW);

        params.setCurrentAlpha1(currentAlpha1);
        params.setCurrentBeta1(currentBeta1);
        params.setCurrentBeta2(currentBeta2);
        params.setCurrentGamma1(currentGamma1);
        params.setCurrentGamma2(currentGamma2);
        params.setCurrentC1(currentC1);

        params.setXn(xn);

        log.debug("Predictive Beta Parameters calculated: {}", params);

        return params;
    }

    private float applyControlFactor(float previousValue, float newValue) {
        newValue = (1 - PHI) * previousValue + PHI * newValue;
        return Math.max(0, Math.min(1, newValue));
    }
}
