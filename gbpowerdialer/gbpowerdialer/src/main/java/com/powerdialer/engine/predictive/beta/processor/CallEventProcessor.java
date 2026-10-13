package com.powerdialer.engine.predictive.beta.processor;

import com.powerdialer.engine.predictive.beta.model.CallType;
import com.powerdialer.model.Call;
import com.powerdialer.util.CampaignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * This class processes call events and updates the call counts for different types of calls in a campaign.
 */
@Component
public class CallEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(CallEventProcessor.class);
    private final CallCountUpdater callCountUpdater;

    /**
     * Constructs a new {@code CallEventProcessor} with the specified {@link CallCountUpdater}.
     *
     * @param callCountUpdater the updater to be used for updating the call counts
     */
    public CallEventProcessor(CallCountUpdater callCountUpdater) {
        this.callCountUpdater = callCountUpdater;
    }

    /**
     * Processes the call event by updating the call count for the specified call type.
     *
     * @param call      the call for which the event is being processed
     * @param callType  the type of call whose count is to be updated
     * @param increment if {@code true}, the call count is incremented; if {@code false}, the call count is decremented
     */
    private void process(Call call, CallType callType, boolean increment) {
        Optional.ofNullable(call)
                .map(Call::getCampaign)
                .filter(CampaignUtil::isValidPredictiveCampaign)
                .ifPresent(campaign -> {
                    if (increment) {
                        callCountUpdater.incrementCallCount(campaign.getId(), callType);
                    } else {
                        callCountUpdater.decrementCallCount(campaign.getId(), callType);
                    }
                });
    }

    /**
     * Handles the call event by processing the call and updating the call count.
     * <p>
     * This method is responsible for handling the call event by invoking the process method.
     * It takes the call object, the type of call, and a boolean flag indicating whether to increment
     * or decrement the call count. The process method is then called with these parameters to update
     * the call count accordingly.
     * </p>
     *
     * @param call      the call for which the event is being handled
     * @param callType  the type of call whose count is to be updated
     * @param increment if {@code true}, the call count is incremented; if {@code false}, the call count is decremented
     */
    public void handleCallEvent(Call call, CallType callType, boolean increment) {
        log.info("Handling call event for call ID: {}, CallType: {}, Increment: {}",
                 Optional.ofNullable(call).map(Call::getId).orElse(null), callType, increment);
        process(call, callType, increment);
    }
}