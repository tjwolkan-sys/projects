package com.powerdialer.engine.predictive.beta.processor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.predictive.beta.calculator.PredictiveBetaCallCalculator;
import com.powerdialer.engine.predictive.beta.config.PredictiveBetaCallConfiguration;
import com.powerdialer.engine.predictive.beta.model.CallType;
import com.powerdialer.engine.predictive.beta.model.PeriodicCallData;
import com.powerdialer.engine.predictive.beta.model.PredictiveCallParameters;
import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.CampaignUtil;
import com.powerdialer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 *
 * <p>
 * This class is responsible for initializing the scheduled executor service
 * and processing call events to update their state.
 */
@Component
public class PredictiveBetaCallManager {

    private static final Logger log = LoggerFactory.getLogger(PredictiveBetaCallManager.class);

    private final CallEventProcessor callEventProcessor;
    private final SynchronizedExecutor synchronizedExecutor;
    private final PredictiveBetaCallConfiguration predictiveBetaCallConfiguration;
    private final PredictiveBetaCallCalculator predictiveBetaCallCalculator;
    private final ACDEngine acdEngine;

    private ScheduledExecutorService scheduledExecutorService;


    @Autowired
    public PredictiveBetaCallManager(CallEventProcessor callEventProcessor, SynchronizedExecutor synchronizedExecutor,
                                     PredictiveBetaCallConfiguration predictiveBetaCallConfiguration, PredictiveBetaCallCalculator predictiveBetaCallCalculator, ACDEngine acdEngine) {
        this.callEventProcessor = callEventProcessor;
        this.synchronizedExecutor = synchronizedExecutor;
        this.predictiveBetaCallConfiguration = predictiveBetaCallConfiguration;
        this.predictiveBetaCallCalculator = predictiveBetaCallCalculator;
        this.acdEngine = acdEngine;
    }

    /**
     * Initializes the `PredictiveBetaCallManager` by setting up the `ScheduledExecutorService`.
     *
     * <p>This method creates a daemon thread factory with a custom name format and initializes
     * the `ScheduledExecutorService` using a single-threaded executor. Logging is added to track
     * the initialization process, and exceptions are handled gracefully by logging any errors
     * that occur during the initialization.
     */
    @PostConstruct
    private void init() {
        log.info("Initializing PredictiveBetaCallManager...");
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setDaemon(true)
                .setNameFormat("predictive-call-manager-executor-%d")
                .build();
        try {
            this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            log.info("ScheduledExecutorService initialized successfully.");
        } catch (Exception e) {
            log.error("Error initializing ScheduledExecutorService", e);
        }
    }

    /**
     * Handles the event when a campaign starts.
     *
     * <p>This method checks if the campaign is a valid predictive campaign. If it is, it restarts
     * the total number of calls calculation for the campaign using the `SynchronizedExecutor`.
     *
     * @param campaign the `Campaign` object representing the campaign that has started
     */
    public void onCampaignStart(Campaign campaign) {
        restartCalculation(campaign);
    }


    /**
     *
     * Restarts the total number of calls calculation for the given campaign.
     *
     * <p>This method checks if the campaign is a valid predictive campaign. If it is, it executes
     * the total number of calls calculation logic using the `SynchronizedExecutor`.
     *
     * @param campaign               the `Campaign` object representing the campaign to restart the calculation for
     */
    private void restartCalculation(Campaign campaign) {
        if (!CampaignUtil.isValidPredictiveCampaign(campaign)) {
            return;
        }
        synchronizedExecutor.execute(campaign, callData -> {
            log.info("Restarting total number of calls calculation for campaign {}", campaign.getId());
            restartCalculationSync(campaign, callData, true);
        });
    }

    private void restartCalculationSync(Campaign campaign, PredictiveCampaignCallData callData, boolean useInitialDelay) {
        int initialDelay = (useInitialDelay)
                ? predictiveBetaCallConfiguration.getConfig().getRecalculationInitialDelaySec()
                : predictiveBetaCallConfiguration.getConfig().getRecalculationIntervalSec();
        stopRecalculationFuture(campaign, callData);
        int campaignIdleUsersCount = acdEngine.getCampaignIdleUsers(campaign).isEmpty() ? 0 : acdEngine.getCampaignIdleUsers(campaign).size();
        callData.restartCalculation(campaignIdleUsersCount);
        callData.setRecalculationFuture(scheduledExecutorService.scheduleAtFixedRate(() -> recalculateTotalNumberOfCalls(callData.getCampaign()),
                initialDelay, predictiveBetaCallConfiguration.getConfig().getRecalculationIntervalSec(), TimeUnit.SECONDS));
    }


    /**
     * Resets the total number of calls calculation for the given campaign.
     *
     * <p>This method stops any ongoing recalculation future, restarts the calculation with the provided total number of calls value,
     * and schedules the next recalculation.
     *
     * @param campaign   the `Campaign` object representing the campaign to reset the calculation for
     * @param predictiveCampaignCallData  the `PredictiveCampaignCallData` object containing the total number of calls data for the campaign
     * @param totalNumberOfCalls                                     the total number of calls to set for the campaign
     * <p>
     * This method is typically called when the campaign is updated or when there is a need to reset the calculation
     * due to changes in the campaign's parameters or configuration.
     */
    private void resetCalculation(Campaign campaign, PredictiveCampaignCallData predictiveCampaignCallData, float totalNumberOfCalls) {
        stopRecalculationFuture(campaign, predictiveCampaignCallData);
        predictiveCampaignCallData.restartCalculation(totalNumberOfCalls);
        scheduleRecalculation(campaign, predictiveCampaignCallData);
    }

    /**
     * Schedules the recalculation of the total number of calls value for the given campaign.
     *
     * <p>This method sets up a scheduled task that will periodically recalculate the total number of call value
     * for the specified campaign. The task is scheduled with an initial delay and a fixed interval
     * as configured in the `PredictiveBetaCallConfiguration`.
     *
     * @param campaign  the `Campaign` object representing the campaign for which the total number of calls value will be recalculated
     * @param predictiveCampaignCallData the `PredictiveCampaignCallData` object containing the call data for the campaign
     */
    private void scheduleRecalculation(Campaign campaign, PredictiveCampaignCallData predictiveCampaignCallData) {
        predictiveCampaignCallData.setRecalculationFuture(
                scheduledExecutorService.scheduleAtFixedRate(
                        () -> recalculateTotalNumberOfCalls(campaign),
                        predictiveBetaCallConfiguration.getConfig().getRecalculationInitialDelaySec(),
                        predictiveBetaCallConfiguration.getConfig().getRecalculationIntervalSec(),
                        TimeUnit.SECONDS
                )
        );
    }

    /**
     * Recalculates the total number of calls for the specified campaign.
     *
     * <p>This method performs the following operations:
     * <ul>
     *   <li>Gets the count of available users for the campaign</li>
     *   <li>Calculates predictive parameters based on current and previous data</li>
     *   <li>Determines the total number of calls using calculated parameters or default value</li>
     *   <li>Restarts the calculation with the new total calls value</li>
     * </ul>
     *
     * <p>The operation is executed in a synchronized manner to ensure thread safety.
     * All calculation details are logged for monitoring and debugging purposes.
     *
     * @param campaign the campaign for which to recalculate total number of calls
     * @throws IllegalArgumentException if campaign is null
     */
    private void recalculateTotalNumberOfCalls(Campaign campaign) {
        try {
            synchronizedExecutor.execute(campaign, callData -> {
                int availableUsersCount = getCampaignAvailUsersCount(campaign, callData);
                logRecalculationStart(campaign, availableUsersCount);
                PredictiveCallParameters parameters = predictiveBetaCallCalculator.calculatePredictiveParameters(availableUsersCount, callData);
                callData.setPredictiveCallParameters(parameters);
                float totalCalls = getTotalCallsFromParameters(parameters);
                callData.restartCalculation(totalCalls);
            });
        } catch (Exception e) {
            log.error("Failed to recalculate total number of calls for campaign {}", campaign.getId(), e);
        }
    }

    /**
     * Determines the total number of calls from predictive parameters.
     *
     * <p>If the parameters are valid and contain a non-zero Xn value, that value is used.
     * Otherwise, falls back to the default total number of calls value from configuration.
     *
     * @param parameters the predictive call parameters (can be null)
     * @return the total number of calls to use; either from parameters or default configuration
     */
    private float getTotalCallsFromParameters(PredictiveCallParameters parameters) {
        return (parameters != null && parameters.getXn() != 0)
                ? parameters.getXn()
                : predictiveBetaCallConfiguration.getConfig().getDefaultTotalNumberOfCallsValue();
    }

    /**
     * Logs the start of recalculation process with campaign and user count information.
     *
     * @param campaign the campaign being processed
     * @param availableUsersCount the number of available users for the campaign
     */
    private void logRecalculationStart(Campaign campaign, int availableUsersCount) {
        log.info("Recalculating total calls for campaign {} with {} available users", campaign.getId(), availableUsersCount);
    }

    /**
     * Retrieves the count of available users for the given campaign.
     *
     * <p>This method calculates the total number of available users for the specified campaign
     * by summing the number of idle users and the current in-call count.
     *
     * @param campaign  the `Campaign` object representing the campaign for which to retrieve the available users count
     * @param callData  the `PredictiveCampaignCallData` object containing the call data for the campaign
     * @return the total number of available users for the specified campaign
     */
    private int getCampaignAvailUsersCount(Campaign campaign, PredictiveCampaignCallData callData) {
        // TODO: getCampaignIdleUsers || getCampaignAvailableUsersCount
        int campaignIdleUsersCount = acdEngine.getCampaignIdleAndAvailableUsers(campaign).isEmpty() ? 0 : acdEngine.getCampaignIdleAndAvailableUsers(campaign).size();
        return campaignIdleUsersCount;
    }

    /**
     * Stops any ongoing recalculation future for the given campaign.
     *
     * <p>This method checks if there is an existing recalculation future for the specified campaign.
     * If a recalculation future is found, it cancels the future and sets it to null in the `PredictiveCampaignCallData`.
     * Logging is added to track the stopping process.
     *
     * @param campaign  the `Campaign` object representing the campaign for which the recalculation future will be stopped
     * @param predictiveCampaignCallData the `PredictiveCampaignCallData` object containing the call data for the campaign
     */
    private void stopRecalculationFuture(Campaign campaign, PredictiveCampaignCallData predictiveCampaignCallData) {
        ScheduledFuture<?> recalculationFuture = predictiveCampaignCallData.getRecalculationFuture();
        if (recalculationFuture != null) {
            log.info("Stopping recalculation future for campaign {}...", campaign.getId());
            recalculationFuture.cancel(true);
            log.info("Recalculation future cancelled for campaign {}", campaign.getId());
            predictiveCampaignCallData.setRecalculationFuture(null);
        }
    }

    /**
     * Handles the event when a campaign stops.
     *
     * <p>This method stops the total number of calls calculation for the given campaign.
     * If the campaign is not a valid predictive campaign, the method returns immediately.
     * Otherwise, it executes the stop logic using the `SynchronizedExecutor`.
     *
     * @param campaign the `Campaign` object representing the campaign that has stopped
     */
    public void onCampaignStop(Campaign campaign) {
        if (CampaignUtil.isValidPredictiveCampaign(campaign)) {
            synchronizedExecutor.execute(campaign, callData -> {
                log.info("Stopping total number of calls calculation for campaign {}", campaign.getId());
                stopRecalculationFuture(campaign, callData);
            });
        }
    }

    /**
     * Updates the state of a call based on the provided call type and increment flag.
     *
     * <p>This method delegates the handling of the call event to the `CallEventProcessor`.
     * The `CallEventProcessor` will process the call event and update the state of the call
     * accordingly. The `callType` parameter specifies the type of the call event, and the
     * `increment` flag indicates whether the call count should be incremented or decremented.
     *
     * @param call      the `Call` object representing the call to be updated
     * @param callType  the type of the call event (e.g., INCOMING, OUTGOING)
     * @param increment a boolean flag indicating whether to increment (true) or decrement (false) the call count
     */
    public void updateCallState(Call call, CallType callType, boolean increment) {
        if (checkCampaign(call.getCampaign())) {
            log.info("Updating call state for call {} with type {} and increment {}", call.getId(), callType, increment);
            callEventProcessor.handleCallEvent(call, callType, increment);
        }
    }

    /**
     * Retrieves the total number of calls for the specified campaign.
     *
     * <p>This method returns the total number of calls for the given campaign ID.
     * If the campaign data is not available, it returns a default value specified in the configuration.
     *
     * @param campaignId the ID of the campaign for which to retrieve the total number of calls
     * @return the total number of calls for the specified campaign
     */
    public int getPredictiveTotalNumberOfCall(Long campaignId) {
        return (int) Math.floor(Optional.ofNullable(synchronizedExecutor.getCampaignDataMap().get(campaignId))
                .map(PredictiveCampaignCallData::getCurrentData)
                .map(PeriodicCallData::getTotalNumberOfCalls)
                .orElse(predictiveBetaCallConfiguration.getConfig().getDefaultTotalNumberOfCallsValue()));
    }

    /**
     *
     * Resets the total number of calls calculation for the specified campaign.
     *
     * <p>This method forces the restart of the total number of calls calculation for the specified campaign.
     * It ensures that the initial value is set, and the calculation logic is executed
     * using the `SynchronizedExecutor`.
     *
     * @param campaign the `Campaign` object representing the campaign to reset the calculation for
     */
    public void resetCalculation(Campaign campaign) {
        restartCalculation(campaign);
    }

    private boolean checkCampaign(Campaign campaign) {
        return campaign != null && !campaign.isDeleted() && campaign.getType() == Constants.CAMPAIGN_TYPE.PREDICTIVE;
    }
}
