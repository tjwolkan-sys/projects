package com.powerdialer.engine.scheduler;

import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.engine.scheduler.schedulechain.orchestrator.CampaignSchedulerOrchestrator;
import com.powerdialer.model.Campaign;
import com.powerdialer.service.CampaignService;
import com.powerdialer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Executes scheduled campaigns by evaluating schedule conditions and performing
 * start, stop, or pause actions based on the result.
 */
@Component
public class ScheduledCampaignRunner {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledCampaignRunner.class);

    private final CampaignService campaignService;
    private final CampaignSchedulerOrchestrator schedulerOrchestrator;

    /**
     * Creates a new ScheduledCampaignRunner.
     *
     * @param campaignService         service used to manage campaign actions
     * @param schedulerOrchestrator   orchestrator that evaluates scheduling rules
     */
    public ScheduledCampaignRunner(CampaignService campaignService,
                                   CampaignSchedulerOrchestrator schedulerOrchestrator) {
        this.campaignService = campaignService;
        this.schedulerOrchestrator = schedulerOrchestrator;
    }

    /**
     * Processes all campaigns that have active scheduling configurations.
     */
    public void processScheduledCampaigns() {
        logger.info("Starting ScheduledCampaignRunner");
        try {
            campaignService.getCampaignsWithSchedules()
                    .forEach(this::safeCheckCampaign);
        } catch (Exception e) {
            logger.error("Failed to process scheduled campaigns. Will retry on next cycle.", e);
        }
    }

    /**
     * Checks a campaign safely and logs any exceptions during evaluation.
     *
     * @param campaign campaign to check
     */
    private void safeCheckCampaign(Campaign campaign) {
        try {
            checkAndExecuteCampaign(campaign);
        } catch (Exception e) {
            logger.error("Could not perform schedule check for {}", campaign, e);
        }
    }

    /**
     * Evaluates scheduling rules for a campaign and triggers required actions.
     *
     * @param campaign campaign to evaluate
     */
    private void checkAndExecuteCampaign(Campaign campaign) {
        // FIX-333607: aksiyonlar asenkron; önceki turun aksiyonu (or. RESET ardından START) bitmeden
        // bayat state ile yeni karar verilmez. Eskiden senkron aksiyonlar bitince bir sonraki tur başlardı.
        if (campaignService.hasPendingAction(campaign.getId())) {
            logger.debug("Skipping schedule check for {}: an action is still pending", campaign);
            return;
        }
        ScheduleCheckResult result = schedulerOrchestrator.evaluateCampaign(campaign);
        processCampaignActions(campaign, result);
    }

    /**
     * Applies start, stop, or pause actions based on schedule evaluation result.
     *
     * @param campaign campaign to act on
     * @param result   evaluation result from the scheduler orchestrator
     */
    private void processCampaignActions(Campaign campaign, ScheduleCheckResult result) {
        Campaign runtimeCampaign = getRuntimeCampaign(campaign);
        Constants.CAMPAIGN_STATE state = (runtimeCampaign == null)
                ? Constants.CAMPAIGN_STATE.STOPPED
                : runtimeCampaign.getState();
        boolean manuallyPaused = runtimeCampaign != null && runtimeCampaign.isManuallyPaused();

        if (shouldStopCampaign(result, state)) {
            stopCampaign(campaign);
            return;
        }
        if (shouldResetCampaign(result, state)) {
            resetCampaign(campaign);
        }

        if (shouldStartCampaign(result, state, manuallyPaused)) {
            startCampaign(campaign);
            return;
        }
        if (shouldPauseCampaign(result, state)) pauseCampaign(campaign);
    }

    /**
     * Determines if the campaign should be stopped.
     *
     * @param result evaluation result
     * @param state  current campaign state
     * @return true if campaign should be stopped
     */
    private boolean shouldStopCampaign(ScheduleCheckResult result, Constants.CAMPAIGN_STATE state) {
        return result.isMustBeStopped()
                && state != Constants.CAMPAIGN_STATE.STOPPED
                && state != Constants.CAMPAIGN_STATE.STOPPING
                && !isBusyState(state);
    }

    /**
     * Determines if the campaign should be started.
     *
     * <p>A campaign that is currently {@code PAUSED} is only auto-started when the
     * pause originated from schedule automation ({@code manuallyPaused == false}).
     * A manual (operator) pause is kept sticky: the scheduler will not restart it
     * while inside the schedule window, even though {@code mustBeStarted} is true.</p>
     *
     * @param result         evaluation result
     * @param state          current campaign state
     * @param manuallyPaused true if the campaign was paused manually by an operator
     * @return true if campaign should be started
     */
    private boolean shouldStartCampaign(ScheduleCheckResult result, Constants.CAMPAIGN_STATE state, boolean manuallyPaused) {
        return result.isMustBeStarted()
                && (state == Constants.CAMPAIGN_STATE.STOPPED
                    || state == Constants.CAMPAIGN_STATE.STOPPING
                    || (state == Constants.CAMPAIGN_STATE.PAUSED && !manuallyPaused));
    }


    /**
     * Determines if the campaign should be paused.
     *
     * @param result evaluation result
     * @param state  current campaign state
     * @return true if campaign should be paused
     */
    private boolean shouldPauseCampaign(ScheduleCheckResult result, Constants.CAMPAIGN_STATE state) {
        return result.isMustBePaused()
                && state == Constants.CAMPAIGN_STATE.RUNNING;
    }

    /**
     * Determines if the campaign should be reset.
     *
     * @param result evaluation result
     * @param state  current campaign state
     * @return true if campaign should be reset
     */
    private boolean shouldResetCampaign(ScheduleCheckResult result, Constants.CAMPAIGN_STATE state) {
        return result.isResetEnabled()
                && state == Constants.CAMPAIGN_STATE.STOPPED;
    }

    /**
     * Stops the campaign by calling the campaign service.
     *
     * @param campaign campaign to stop
     */
    private void stopCampaign(Campaign campaign) {
        logger.info("Stopping campaign {}", campaign);
        campaignService.actionAsync(
                campaign.getId(),
                Constants.CAMPAIGN_ACTION.STOP_REGULAR,
                Constants.CAMPAIGN_STOP_TYPE.AUTOMATIC
        );
    }

    /**
     * Starts the campaign by calling the campaign service.
     *
     * @param campaign campaign to start
     */
    private void startCampaign(Campaign campaign) {
        logger.info("Starting campaign {}", campaign);
        campaignService.actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.START, null);
    }

    /**
     * Pauses the campaign by calling the campaign service.
     *
     * @param campaign campaign to pause
     */
    private void pauseCampaign(Campaign campaign) {
        logger.info("Pausing campaign {}", campaign);
        campaignService.actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.PAUSE, null);
    }

    /**
     * Resets the campaign by calling the campaign service.
     *
     * @param campaign campaign to reset
     */
    private void resetCampaign(Campaign campaign) {
        logger.info("Resetting campaign {}", campaign);
        campaignService.actionAsync(campaign.getId(), Constants.CAMPAIGN_ACTION.RESET, null);
    }

    /**
     * Retrieves the live runtime campaign held in the in-memory campaign context.
     * Both the current state and the {@code manuallyPaused} flag are read from this
     * single object so they always reflect the same context snapshot.
     *
     * @param campaign campaign whose runtime context is to be fetched
     * @return runtime campaign, or {@code null} when no context exists (treated as STOPPED)
     */
    private Campaign getRuntimeCampaign(Campaign campaign) {
        CampaignContext context = campaignService.getDialerFacade().getCampaignContexts().get(campaign.getId());
        return (context == null) ? null : context.getCampaign();
    }

    /**
     * Checks if the campaign is in a busy or transitional state.
     *
     * @param state campaign state
     * @return true if campaign is busy
     */
    private boolean isBusyState(Constants.CAMPAIGN_STATE state) {
        return BUSY_STATES.contains(state);
    }

    /**
     * Set of campaign states considered busy (e.g., running or transitioning).
     */
    private static final EnumSet<Constants.CAMPAIGN_STATE> BUSY_STATES = EnumSet.of(
            Constants.CAMPAIGN_STATE.STARTING,
            Constants.CAMPAIGN_STATE.PAUSING,
            Constants.CAMPAIGN_STATE.RELOADING,
            Constants.CAMPAIGN_STATE.RESETTING
    );
}
