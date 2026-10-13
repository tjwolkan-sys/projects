package com.powerdialer.engine.predictive.beta.processor;

import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.engine.predictive.dispatcher.PredictiveBetaEventDispatcher;
import com.powerdialer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * This class provides a mechanism to execute tasks in a synchronized manner for each campaign.
 * It ensures that tasks for the same campaign are executed sequentially.
 */
@Component
public class SynchronizedExecutor {

    private static final Logger log = LoggerFactory.getLogger(SynchronizedExecutor.class);

    public Map<Long, PredictiveCampaignCallData> getCampaignDataMap() {
        return campaignDataMap;
    }

    private final Map<Long, PredictiveCampaignCallData> campaignDataMap = new ConcurrentHashMap<>();

    @Autowired
    private PredictiveEventFactory eventFactory;

    @Autowired
    private PredictiveBetaEventDispatcher betaDispatcher;

    /**
     * Executes the given consumer for the specified campaign in a synchronized manner.
     * This method ensures that tasks for the same campaign are executed sequentially,
     * preventing concurrent modifications and potential data inconsistencies.
     *
     * @param campaign the ID of the campaign for which the task is to be executed
     *                 the task to be executed, which accepts a PredictiveCampaignCallData object
     */
    public void execute(Campaign campaign, Consumer<PredictiveCampaignCallData> consumer) {
        PredictiveCampaignCallData callData = getOrCreateCampaignData(campaign);
        callData.lock();
        try {
            log.debug("Executing consumer for campaign {}", campaign.getId());
            consumer.accept(callData);
            publishPredictiveEvent(callData);
        } catch (Exception e) {
            log.error("Error executing consumer for campaign {}", campaign.getId(), e);
        } finally {
            callData.unlock();
            log.debug("Unlocked callData for campaign {}", campaign.getId());
        }
    }

    public void publishPredictiveEvent (PredictiveCampaignCallData boostData) {
        try {
            log.debug("Publishing predictive BETA event for campaign {}", boostData.getCampaign().getId());
            betaDispatcher.dispatchAsync(
                    eventFactory.createBeta(boostData)
            );
        } catch (Exception e) {
            log.warn("Could not publish predictive BETA event for campaign {}. Continuing.", boostData.getCampaign().getId(), e);
        }
    }

    /**
     * Executes the given consumer for the specified campaign in a synchronized manner.
     * This method ensures that tasks for the same campaign are executed sequentially,
     * preventing concurrent modifications and potential data inconsistencies.
     *
     * @param campaignId the ID of the campaign for which the task is to be executed
     *                   the task to be executed, which accepts a PredictiveCampaignCallData object
     */
    public void execute(Long campaignId, Consumer<PredictiveCampaignCallData> consumer) {
        PredictiveCampaignCallData callData = campaignDataMap.get(campaignId);
        callData.lock();
        try {
            log.debug("Executing consumer for campaign {}", campaignId);
            consumer.accept(callData);
        } catch (Exception e) {
            log.error("Error executing consumer for campaign {}", campaignId, e);
        } finally {
            callData.unlock();
            log.debug("Unlocked call data for campaign {}", campaignId);
        }
    }

    /**
     * Retrieves the campaign data for the specified campaign, creating it if it does not exist.
     *
     * @param campaign the ID of the campaign
     * @return the campaign data
     */
    private PredictiveCampaignCallData getOrCreateCampaignData(Campaign campaign) {
        PredictiveCampaignCallData data = campaignDataMap.computeIfAbsent(campaign.getId(), id -> new PredictiveCampaignCallData(campaign));
        data.setCampaign(campaign);
        return data;
    }
}