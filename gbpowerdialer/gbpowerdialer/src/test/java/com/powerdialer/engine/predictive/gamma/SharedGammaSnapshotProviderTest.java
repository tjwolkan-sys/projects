package com.powerdialer.engine.predictive.gamma;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.AgentStatusBucket;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.engine.predictive.gamma.processor.SharedGammaSnapshotProvider;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Group;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SharedGammaSnapshotProviderTest {

    @Mock private DialerFacade dialerFacade;
    @Mock private ACDEngine acdEngine;
    @Mock private RedisUtil redisUtil;
    @Mock private CampaignContext firstContext;
    @Mock private CampaignContext secondContext;

    private SharedGammaSnapshotProvider provider;

    @Before
    public void setUp() {
        provider = new SharedGammaSnapshotProvider(dialerFacade, acdEngine, redisUtil);
    }

    @Test
    public void refreshSnapshot_splitsSharedGammaAgentByPressureAndAppliesBlendRatio() {
        Group group = new Group();
        group.setId(10L);

        User user = new User();
        user.setId(100L);
        user.setGroup(group);

        Campaign firstCampaign = runningGammaCampaign(1L, 50, 100);
        Campaign secondCampaign = runningGammaCampaign(2L, 50, 100);

        Map<Long, CampaignContext> contexts = new LinkedHashMap<>();
        contexts.put(firstCampaign.getId(), firstContext);
        contexts.put(secondCampaign.getId(), secondContext);
        when(dialerFacade.getCampaignContexts()).thenReturn(contexts);
        when(firstContext.getCampaign()).thenReturn(firstCampaign);
        when(secondContext.getCampaign()).thenReturn(secondCampaign);
        // Task 325654: provider artik registered-user snapshot'ini tur basina bir kez alan
        // batch cagriyi kullanir; donen listeler per-campaign sonucla birebir aynidir.
        Map<Long, List<User>> activeUsersByCampaignId = new HashMap<>();
        activeUsersByCampaignId.put(firstCampaign.getId(), Collections.singletonList(user));
        activeUsersByCampaignId.put(secondCampaign.getId(), Collections.singletonList(user));
        when(acdEngine.getCampaignAllActiveUsersByCampaignId(anyCollection())).thenReturn(activeUsersByCampaignId);

        Map<Long, AgentStatusBucket> statusByUserId = new HashMap<>();
        statusByUserId.put(user.getId(), AgentStatusBucket.AVAILABLE);
        when(redisUtil.getUsersCurrentStatusMap(Collections.singleton(user.getId()))).thenReturn(statusByUserId);

        Map<Long, Double> blendByGroupId = new HashMap<>();
        blendByGroupId.put(group.getId(), 0.50);
        when(redisUtil.getAllGroupBlendRatios()).thenReturn(blendByGroupId);

        provider.refreshSnapshot();

        AgentMetricsDTO firstMetrics = provider.getAgentMetrics(firstCampaign.getId());
        AgentMetricsDTO secondMetrics = provider.getAgentMetrics(secondCampaign.getId());

        assertEquals(1.0, firstMetrics.getPhysicalTotalAgents(), 0.0001);
        assertEquals(0.25, firstMetrics.getTotalAgents(), 0.0001);
        assertEquals(0.25, firstMetrics.getAvailAgents(), 0.0001);
        assertEquals(1.0, secondMetrics.getPhysicalTotalAgents(), 0.0001);
        assertEquals(0.25, secondMetrics.getTotalAgents(), 0.0001);
        assertEquals(0.25, secondMetrics.getAvailAgents(), 0.0001);
        assertTrue(provider.isSharedCampaign(firstCampaign.getId()));
        assertTrue(provider.isSharedCampaign(secondCampaign.getId()));
    }

    private Campaign runningGammaCampaign(long id, int cacheSize, int cacheLimit) {
        Campaign campaign = new Campaign();
        campaign.setId(id);
        campaign.setName("Gamma " + id);
        campaign.setType(Constants.CAMPAIGN_TYPE.PREDICTIVE);
        campaign.setPredictiveStrategy(Constants.PREDICTIVE_STRATEGY.GAMMA);
        campaign.setState(Constants.CAMPAIGN_STATE.RUNNING);
        campaign.setCacheSize(cacheSize);
        campaign.setCacheLimit(cacheLimit);
        return campaign;
    }
}
