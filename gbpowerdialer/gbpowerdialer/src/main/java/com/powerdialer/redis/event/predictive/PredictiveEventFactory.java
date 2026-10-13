package com.powerdialer.redis.event.predictive;

import com.powerdialer.engine.boost.CampaignBoostData;
import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.util.ApplicationUtil;
import org.springframework.stereotype.Component;

@Component
public class PredictiveEventFactory {

    private final ApplicationUtil applicationUtil;

    public PredictiveEventFactory(ApplicationUtil applicationUtil) {
        this.applicationUtil = applicationUtil;
    }


    /** CampaignBoostData → PredictiveAlphaEvent */
    public PredictiveAlphaEvent createAlpha(CampaignBoostData data) {
        String publisher = applicationUtil.getThisApplicationLocalIpAddress();
        return new PredictiveAlphaEvent(publisher, data);
    }

    public PredictiveBetaEvent createBeta(PredictiveCampaignCallData data) {
        String publisher = applicationUtil.getThisApplicationLocalIpAddress();
        return new PredictiveBetaEvent(publisher, data);
    }

    public PredictiveGammaEvent createGamma(PredictiveGammaCallData data) {
        String publisher = applicationUtil.getThisApplicationLocalIpAddress();
        return new PredictiveGammaEvent(publisher, data);
    }

}
