package com.powerdialer.engine.predictive.gamma.processor;

import com.powerdialer.dto.AgentMetricsDTO;
import com.powerdialer.dto.AgentStatusBucket;
import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.CampaignContext;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.RedisUtil;
import com.powerdialer.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
public class SharedGammaSnapshotProvider {
    private static final Logger log = LoggerFactory.getLogger(SharedGammaSnapshotProvider.class);

    private final DialerFacade dialerFacade;
    private final ACDEngine acdEngine;
    private final RedisUtil redisUtil;
    private final AtomicReference<SharedGammaSnapshot> currentSnapshot = new AtomicReference<>(SharedGammaSnapshot.empty());
    private final ReentrantLock refreshLock = new ReentrantLock();

    public SharedGammaSnapshotProvider(@Lazy DialerFacade dialerFacade, ACDEngine acdEngine, RedisUtil redisUtil) {
        this.dialerFacade = dialerFacade;
        this.acdEngine = acdEngine;
        this.redisUtil = redisUtil;
    }

    @Scheduled(fixedRate = 2000, initialDelay = 0)
    public void refreshSnapshot() {
        if (!refreshLock.tryLock()) return;
        try {
            currentSnapshot.set(buildSnapshot());
        } catch (Exception e) {
            log.error("[GAMMA-PROVIDER] Scheduled snapshot refresh failed.", e);
        } finally {
            refreshLock.unlock();
        }
    }

    public AgentMetricsDTO getAgentMetrics(long campaignId) {
        SharedGammaSnapshot snapshot = currentSnapshot.get();
        if (snapshot.metricsByCampaignId.isEmpty()) {
            refreshLock.lock();
            try {
                snapshot = currentSnapshot.get();
                if (snapshot.metricsByCampaignId.isEmpty()) {
                    currentSnapshot.set(buildSnapshot());
                    snapshot = currentSnapshot.get();
                }
            } catch (Exception e) {
                log.error("[GAMMA-PROVIDER] Lazy snapshot creation failed.", e);
            } finally {
                refreshLock.unlock();
            }
        }
        return snapshot.metricsByCampaignId.getOrDefault(campaignId, AgentMetricsDTO.zero());
    }

    public boolean isSharedCampaign(long campaignId) {
        return currentSnapshot.get().sharedCampaignIds.contains(campaignId);
    }

    private SharedGammaSnapshot buildSnapshot() {
        List<Campaign> runningCampaigns = getRunningCampaigns();
        List<Campaign> runningGammaCampaigns = runningCampaigns.stream()
                .filter(this::isRunningGammaCampaign)
                .collect(Collectors.toList());
        if (runningGammaCampaigns.isEmpty()) return SharedGammaSnapshot.empty();

        Map<Long, Set<Long>> userToCampaignIds = new HashMap<>();
        Set<Long> gammaUserIds = new LinkedHashSet<>();
        Map<Long, Double> pressureByCampaignId = new HashMap<>();
        Map<Long, Long> userGroupMap = new HashMap<>();
        Set<Long> gammaCampaignIds = runningGammaCampaigns.stream()
                .map(Campaign::getId)
                .collect(Collectors.toSet());

        // Task 325654: getCampaignAllActiveUsers her cagrida CallUpToDateKeeper uzerinden
        // tum aktif cagri koleksiyonunu tariyordu; bu scheduler 2 saniyede bir tum running
        // kampanyalar icin donuyor. Batch cagri registered-user snapshot'ini tur basina bir
        // kez alir, donen listeler per-campaign sonucla birebir aynidir.
        Map<Long, List<User>> activeUsersByCampaignId = acdEngine.getCampaignAllActiveUsersByCampaignId(runningCampaigns);

        for (Campaign campaign : runningCampaigns) {
            pressureByCampaignId.put(campaign.getId(), calculatePressure(campaign));
            for (User user : activeUsersByCampaignId.getOrDefault(campaign.getId(), Collections.<User>emptyList())) {
                if (user == null || user.getId() == null) continue;
                userToCampaignIds.computeIfAbsent(user.getId(), k -> new LinkedHashSet<>()).add(campaign.getId());
                if (gammaCampaignIds.contains(campaign.getId())) {
                    gammaUserIds.add(user.getId());
                    if (user.getGroup() != null) {
                        userGroupMap.put(user.getId(), user.getGroup().getId());
                    }
                }
            }
        }

        Map<Long, AgentStatusBucket> statusByUserId = redisUtil.getUsersCurrentStatusMap(gammaUserIds);
        Map<Long, MutableAgentMetrics> mutableMetrics = new HashMap<>();
        Set<Long> sharedCampaignIds = new LinkedHashSet<>();

        for (Campaign campaign : runningGammaCampaigns) {
            mutableMetrics.put(campaign.getId(), new MutableAgentMetrics());
        }

        Map<Long, Double> ratioByGroupId = redisUtil.getAllGroupBlendRatios();

        for (Long userId : gammaUserIds) {
            Set<Long> eligibleCampaignIds = userToCampaignIds.getOrDefault(userId, Collections.emptySet());
            if (eligibleCampaignIds.isEmpty()) continue;

            if (eligibleCampaignIds.size() > 1) {
                eligibleCampaignIds.stream().filter(gammaCampaignIds::contains).forEach(sharedCampaignIds::add);
            }

            Long groupId = userGroupMap.get(userId);
            double ratio = ratioByGroupId.getOrDefault(groupId, 1.0);

            Map<Long, Double> shares = calculateShares(eligibleCampaignIds, pressureByCampaignId);
            AgentStatusBucket bucket = statusByUserId.getOrDefault(userId, AgentStatusBucket.OTHER);

            for (Map.Entry<Long, Double> entry : shares.entrySet()) {
                if (!gammaCampaignIds.contains(entry.getKey())) continue;
                MutableAgentMetrics metrics = mutableMetrics.get(entry.getKey());
                if (metrics != null) {
                    double finalShare = entry.getValue() * ratio;
                    metrics.add(bucket, finalShare);
                    metrics.addPhysical();
                }
            }
        }

        Map<Long, AgentMetricsDTO> metricsByCampaignId = new HashMap<>();
        for (Map.Entry<Long, MutableAgentMetrics> entry : mutableMetrics.entrySet()) {
            metricsByCampaignId.put(entry.getKey(), entry.getValue().toDto());
        }

        return new SharedGammaSnapshot(metricsByCampaignId, statusByUserId, sharedCampaignIds);
    }

    private double calculatePressure(Campaign campaign) {
        if (campaign == null || campaign.getCacheSize() <= 0) return 0.0;
        int cacheLimit = campaign.getCacheLimit() == null ? 0 : campaign.getCacheLimit();
        double pressure = (double) campaign.getCacheSize() / Math.max(1, cacheLimit);
        return Math.max(0.05, pressure);
    }

    private Map<Long, Double> calculateShares(Set<Long> eligibleCampaignIds, Map<Long, Double> pressureByCampaignId) {
        Map<Long, Double> shares = new LinkedHashMap<>();
        List<Long> activePressureCampaignIds = eligibleCampaignIds.stream()
                .filter(cid -> pressureByCampaignId.getOrDefault(cid, 0.0) > 0.0)
                .collect(Collectors.toList());
        Collection<Long> base = activePressureCampaignIds.isEmpty() ? eligibleCampaignIds : activePressureCampaignIds;

        double totalPressure = base.stream().mapToDouble(cid -> pressureByCampaignId.getOrDefault(cid, 0.0)).sum();
        if (base.isEmpty()) return shares;
        if (base.size() == 1) {
            shares.put(base.iterator().next(), 1.0);
            return shares;
        }
        if (totalPressure <= 0.0) {
            double equalShare = 1.0 / base.size();
            for (Long cid : base) shares.put(cid, equalShare);
            return shares;
        }
        for (Long cid : base) shares.put(cid, pressureByCampaignId.getOrDefault(cid, 0.0) / totalPressure);
        return shares;
    }

    private List<Campaign> getRunningCampaigns() {
        return dialerFacade.getCampaignContexts().values().stream()
                .map(CampaignContext::getCampaign)
                .filter(c -> c != null && Constants.CAMPAIGN_STATE.RUNNING.equals(c.getState()))
                .collect(Collectors.toList());
    }

    private boolean isRunningGammaCampaign(Campaign campaign) {
        return campaign.isPredictiveCampaign() && Constants.PREDICTIVE_STRATEGY.GAMMA.equals(campaign.getPredictiveStrategy());
    }

    private static class MutableAgentMetrics {
        private double physicalTotalAgents;
        private double totalAgents;
        private double incallAgents;
        private double availAgents;
        private double acwAgents;

        private void addPhysical() {
            physicalTotalAgents += 1.0;
        }

        private void add(AgentStatusBucket bucket, double finalShare) {
            totalAgents += finalShare;
            if (bucket == AgentStatusBucket.AVAILABLE) availAgents += finalShare;
            else if (bucket == AgentStatusBucket.INCALL) incallAgents += finalShare;
            else if (bucket == AgentStatusBucket.ACW) acwAgents += finalShare;
        }

        private AgentMetricsDTO toDto() {
            return new AgentMetricsDTO(physicalTotalAgents, totalAgents, incallAgents, availAgents, acwAgents);
        }
    }

    private static class SharedGammaSnapshot {
        private final Map<Long, AgentMetricsDTO> metricsByCampaignId;
        private final Map<Long, AgentStatusBucket> statusByUserId;
        private final Set<Long> sharedCampaignIds;

        private SharedGammaSnapshot(Map<Long, AgentMetricsDTO> metricsByCampaignId, Map<Long, AgentStatusBucket> statusByUserId, Set<Long> sharedCampaignIds) {
            this.metricsByCampaignId = metricsByCampaignId;
            this.statusByUserId = statusByUserId;
            this.sharedCampaignIds = sharedCampaignIds;
        }

        private static SharedGammaSnapshot empty() {
            return new SharedGammaSnapshot(Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());
        }
    }
}
