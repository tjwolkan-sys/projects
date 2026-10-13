package com.powerdialer.engine.predictive.beta.processor;

import com.powerdialer.engine.predictive.dispatcher.PredictiveBetaEventDispatcher;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveBetaEvent;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SynchronizedExecutor}.
 *
 * <p>Verifies that after executing a synchronized operation for a campaign:
 * <ul>
 *   <li>an event is created via {@link PredictiveEventFactory#createBeta(PredictiveCampaignCallData)};</li>
 *   <li>the event is dispatched asynchronously via {@link PredictiveBetaEventDispatcher#dispatchAsync(PredictiveBetaEvent)};</li>
 *   <li>failures in the dispatcher do not propagate and do not break the flow.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class SynchronizedExecutorTest {

    @InjectMocks
    private SynchronizedExecutor executor;

    @Mock private PredictiveEventFactory eventFactory;
    @Mock private PredictiveBetaEventDispatcher betaDispatcher;
    @Mock private Campaign campaign;

    /**
     * Initializes common stubs and resets the internal state of {@link SynchronizedExecutor}.
     */
    @Before
    public void setUp() {
        when(campaign.getId()).thenReturn(42L);
        when(campaign.getName()).thenReturn("Test Campaign");
        when(campaign.getState()).thenReturn(Constants.CAMPAIGN_STATE.RUNNING);
        when(campaign.getCompletionPercent()).thenReturn(0);
        when(campaign.getCacheLimit()).thenReturn(0);
        when(campaign.getCallsPerSecond()).thenReturn(0);
        when(campaign.getAvailableUsersCount()).thenReturn(0L);
        when(campaign.getAgentsSize()).thenReturn(new AtomicInteger(0));
        when(campaign.getInCalls()).thenReturn(new AtomicInteger(0));
        when(campaign.getRingingCalls()).thenReturn(new AtomicInteger(0));
        when(campaign.getConcurrentLines()).thenReturn(0);
        when(campaign.getMaxLines()).thenReturn(0);

        when(eventFactory.createBeta(any(PredictiveCampaignCallData.class)))
                .thenAnswer(invocation -> new PredictiveBetaEvent("test-publisher", invocation.getArgument(0)));

        doNothing().when(betaDispatcher).dispatchAsync(any(PredictiveBetaEvent.class));

        ReflectionTestUtils.setField(
                executor,
                "campaignDataMap",
                new ConcurrentHashMap<Long, PredictiveCampaignCallData>()
        );
    }

    /**
     * Ensures that an event is created and dispatched after a synchronized execution.
     */
    @Test
    public void shouldPublishBetaEventAfterExecute() {
        executor.execute(campaign, callData -> callData.restartCalculation(5.0f));

        verify(eventFactory, times(1)).createBeta(any(PredictiveCampaignCallData.class));
        verify(betaDispatcher, times(1)).dispatchAsync(any(PredictiveBetaEvent.class));
        verifyNoMoreInteractions(eventFactory, betaDispatcher);
    }

    /**
     * Ensures that the flow does not break when the dispatcher throws, and that the publish attempt is still made.
     */
    @Test
    public void shouldNotBreakFlowWhenDispatcherThrows() {
        doThrow(new RuntimeException("boom"))
                .when(betaDispatcher).dispatchAsync(any(PredictiveBetaEvent.class));

        executor.execute(campaign, callData -> callData.restartCalculation(3.0f));

        verify(eventFactory, times(1)).createBeta(any(PredictiveCampaignCallData.class));
        verify(betaDispatcher, times(1)).dispatchAsync(any(PredictiveBetaEvent.class));
    }
}
