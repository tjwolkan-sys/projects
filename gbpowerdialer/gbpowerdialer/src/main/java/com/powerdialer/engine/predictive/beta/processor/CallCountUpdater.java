package com.powerdialer.engine.predictive.beta.processor;

import com.powerdialer.engine.predictive.beta.model.CallType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is responsible for updating the call counts for different types of calls in a campaign.
 * It uses a {@link SynchronizedExecutor} to ensure thread safety while updating the call counts.
 */
@Component
public class CallCountUpdater {

    private static final Logger log = LoggerFactory.getLogger(CallCountUpdater.class);
    private final SynchronizedExecutor executor;

    /**
     * Constructs a new {@code CallCountUpdater} with the specified {@link SynchronizedExecutor}.
     *
     * @param executor the executor to be used for synchronizing the call count updates
     */
    public CallCountUpdater(SynchronizedExecutor executor) {
        this.executor = executor;
    }

    /**
     * Updates the call count for the specified campaign and call type.
     *
     * @param campaignId the ID of the campaign for which the call count is to be updated
     * @param callType   the type of call whose count is to be updated
     * @param increment  if {@code true}, the call count is incremented; if {@code false}, the call count is decremented
     */
    public void updateCallCount(Long campaignId, CallType callType, boolean increment) {
        executor.execute(campaignId, callData -> {
            AtomicInteger counter = callType.extract(callData.getCurrentData());
            if (increment) {
                counter.incrementAndGet();
                log.debug("Incremented {} call count for campaign {}", callType, campaignId);
            } else {
                if(counter.get() > 0)
                    counter.decrementAndGet();

                log.debug("Decremented {} call count for campaign {}", callType, campaignId);
            }
        });
    }

    /**
     * Increments the call count for the specified campaign and call type.
     *
     * @param campaignId the ID of the campaign for which the call count is to be incremented
     * @param callType   the type of call whose count is to be incremented
     */
    public void incrementCallCount(Long campaignId, CallType callType) {
        log.debug("Incremented {} call count for campaign {}", callType, campaignId);
        updateCallCount(campaignId, callType, true);
    }

    /**
     * Decrements the call count for the specified campaign and call type.
     *
     * @param campaignId the ID of the campaign for which the call count is to be decremented
     * @param callType   the type of call whose count is to be decremented
     */
    public void decrementCallCount(Long campaignId, CallType callType) {
        log.debug("Decremented {} call count for campaign {}", callType, campaignId);
        updateCallCount(campaignId, callType, false);
    }
}