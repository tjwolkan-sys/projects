package com.powerdialer.redis.event.predictive;

import com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCallData;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PredictiveGammaEventTest {

    @Test
    public void constructor_copiesGammaBudgetAndStarvationFields() {
        Campaign campaign = mock(Campaign.class);
        when(campaign.getId()).thenReturn(10L);
        when(campaign.getName()).thenReturn("Gamma Event Test");
        when(campaign.getState()).thenReturn(Constants.CAMPAIGN_STATE.RUNNING);
        when(campaign.getCallsPerSecond()).thenReturn(5);
        when(campaign.getGammaAbandonSensitivity()).thenReturn(1.0);
        when(campaign.getGammaRequiredWindowSize()).thenReturn(100);
        when(campaign.getGammaWindowCapacity()).thenReturn(400);

        PredictiveGammaCallData data = new PredictiveGammaCallData(campaign, null);
        data.setIsSharedCampaign(true);
        data.setNextCallType("FRESH");
        data.setNextCallCost(0.90);
        data.setStarvationEligible(true);
        data.setStarvationApplied(false);
        data.setStarvationCreditBefore(0.30);
        data.setStarvationBoost(0.0);
        data.setStarvationCreditAfter(0.60);
        data.setTotalDialBudget(0.30);
        data.setDrainedCallCount(0);
        data.setMaxCallCount(1);
        data.setRemainingCapacity(2);
        data.setCacheSize(7);
        data.setQueueSize(0);

        PredictiveGammaEvent event = new PredictiveGammaEvent("test", data);

        assertTrue(event.getIsSharedCampaign());
        assertEquals("FRESH", event.getNextCallType());
        assertEquals(0.90, event.getNextCallCost(), 0.0001);
        assertTrue(event.getStarvationEligible());
        assertFalse(event.getStarvationApplied());
        assertEquals(0.30, event.getStarvationCreditBefore(), 0.0001);
        assertEquals(0.0, event.getStarvationBoost(), 0.0001);
        assertEquals(0.60, event.getStarvationCreditAfter(), 0.0001);
        assertEquals(0.30, event.getTotalDialBudget(), 0.0001);
        assertEquals(Integer.valueOf(0), event.getDrainedCallCount());
        assertEquals(Integer.valueOf(1), event.getMaxCallCount());
        assertEquals(Integer.valueOf(2), event.getRemainingCapacity());
        assertEquals(Integer.valueOf(7), event.getCacheSize());
        assertEquals(Integer.valueOf(0), event.getQueueSize());
    }
}
