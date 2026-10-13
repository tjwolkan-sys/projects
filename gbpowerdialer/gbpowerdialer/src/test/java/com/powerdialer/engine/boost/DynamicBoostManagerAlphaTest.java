package com.powerdialer.engine.boost;

import com.powerdialer.engine.predictive.dispatcher.PredictiveAlphaEventDispatcher;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.predictive.PredictiveAlphaEvent;
import com.powerdialer.redis.PredictivePublisher;
import com.powerdialer.redis.event.predictive.PredictiveEventFactory;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertTrue;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;
/**
 * Unit tests for {@link DynamicBoostManager} focusing on Alpha flow.
 *
 * <p>Verifies that after a (re)start of the Alpha period:
 * <ul>
 *   <li>an Alpha event is created through {@link PredictiveEventFactory#createAlpha(CampaignBoostData)};</li>
 *   <li>the event is dispatched asynchronously via {@link PredictiveAlphaEventDispatcher#dispatchAsync(PredictiveAlphaEvent)};</li>
 *   <li>dispatcher failures do not propagate to callers.</li>
 * </ul>
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicBoostManagerAlphaTest {

    private DynamicBoostManager manager;

    @Mock private DynamicBoostCalculator dynamicBoostCalculator;
    @Spy  private DynamicBoostTestConfigurator dynamicBoostTestConfigurator = new DynamicBoostTestConfigurator();
    @Mock private PredictiveAlphaEventDispatcher alphaDispatcher;
    @Mock private PredictiveEventFactory eventFactory;
    @Mock private ScheduledExecutorService scheduledExecutorService;
    @Mock private ScheduledFuture<?> scheduledFuture;
    @Mock private PredictivePublisher publisher;

    @Mock private Campaign campaign;

    @Before
    public void setUp() {
        manager = new DynamicBoostManager();

        // Wire dependencies
        ReflectionTestUtils.setField(manager, "dynamicBoostCalculator", dynamicBoostCalculator);
        ReflectionTestUtils.setField(manager, "dynamicBoostTestConfigurator", dynamicBoostTestConfigurator);
        ReflectionTestUtils.setField(manager, "predictiveEventFactory", eventFactory);
        ReflectionTestUtils.setField(manager, "scheduledExecutorService", scheduledExecutorService);
        PredictiveAlphaEventDispatcher realDispatcher = new PredictiveAlphaEventDispatcher(publisher);
        ReflectionTestUtils.setField(manager, "predictiveAlphaEventDispatcher", realDispatcher);
        // Prevent actual scheduling
        Mockito.<ScheduledFuture<?>>when(
                scheduledExecutorService.scheduleAtFixedRate(
                        any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)
                )).thenReturn(scheduledFuture);

        // Minimal Campaign stubbing for validity checks
        when(campaign.isDeleted()).thenReturn(false);
        when(campaign.getType()).thenReturn(Constants.CAMPAIGN_TYPE.PREDICTIVE);
        when(campaign.getId()).thenReturn(100L);

        // Factory returns a mock event; we assert dispatch invocation, not payload
        when(eventFactory.createAlpha(any(CampaignBoostData.class)))
                .thenReturn(mock(PredictiveAlphaEvent.class));

        // Default publisher behavior: do nothing (success path)
        doNothing().when(publisher).publish(any(PredictiveAlphaEvent.class));
    }

    /**
     * Ensures that starting a predictive campaign triggers Alpha event creation and asynchronous dispatch.
     */
    @Test
    public void shouldDispatchAlphaEventOnCampaignStart() {
        manager.onCampaignStart(campaign);

        verify(eventFactory, times(1)).createAlpha(any(CampaignBoostData.class));
        verify(publisher, times(1)).publish(any(PredictiveAlphaEvent.class));
        verifyNoMoreInteractions(eventFactory, publisher);
    }

    /**
     * Verifies that Alpha publish is executed asynchronously via dispatcher (@Async).
     * Uses CountDownLatch to wait for the background thread to call publisher.publish(...).
     */
    @Test
    public void shouldPublishAlphaEventAsynchronously() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(inv -> { latch.countDown(); return null; })
                .when(publisher).publish(any(PredictiveAlphaEvent.class));

        manager.onCampaignStart(campaign);

        assertTrue("Async publish did not happen in time",
                latch.await(1000, TimeUnit.MILLISECONDS));

        verify(publisher, times(1)).publish(any(PredictiveAlphaEvent.class));
    }


    /**
     * Ensures that the flow does not break when the Alpha dispatcher throws an exception.
     */
    @Test
    public void shouldNotBreakFlowWhenDispatcherThrows() {
        // Simulate failure inside dispatchAsync by throwing from the underlying publisher
                doThrow(new RuntimeException("boom"))
                .when(publisher).publish(any(PredictiveAlphaEvent.class));

        manager.onCampaignStart(campaign);

        verify(eventFactory, times(1)).createAlpha(any(CampaignBoostData.class));
        verify(publisher, times(1)).publish(any(PredictiveAlphaEvent.class));
    }
}
