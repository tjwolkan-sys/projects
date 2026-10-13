package com.powerdialer.engine;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background consistency checker that periodically compares the stored
 * {@code agentsSize} counter on each running {@link Campaign} against the
 * value recalculated from {@link ACDEngine}.
 * <p>
 * The checker is intentionally <em>silent</em> during normal operation:
 * it only emits a {@code WARN} log entry when the absolute difference between
 * the two values exceeds {@link #DISCREPANCY_THRESHOLD}. This keeps log
 * volume near zero even with hundreds of agents online.
 * <p>
 * For active investigation, prefer the REST endpoint
 * {@code GET /api/health/agentSize/discrepancies}, which returns real-time
 * data on demand without relying on the scheduler interval.
 */
@Component
public class AgentSizeConsistencyChecker {

    private static final Logger LOG = LoggerFactory.getLogger(AgentSizeConsistencyChecker.class);

    /**
     * Maximum tolerated difference between stored and calculated agent size
     * before a warning is emitted. A value of {@code 2} allows for a brief
     * transient gap (e.g. a login event being processed) without generating
     * noise during high-throughput periods.
     * <p>
     * Lower this to {@code 0} for zero-tolerance environments or raise it
     * during known bulk-login scenarios.
     */
    private static final int DISCREPANCY_THRESHOLD = 2;

    @Autowired
    private DialerFacade dialerFacade;

    @Autowired
    private ACDEngine acdEngine;

    /**
     * Runs every 30 seconds and checks every active campaign context.
     * <p>
     * Only campaigns whose stored-vs-calculated difference exceeds
     * {@link #DISCREPANCY_THRESHOLD} produce a log entry, keeping output
     * minimal under normal conditions.
     * <p>
     * Reduce {@code fixedDelay} to {@code 5000} during load tests for
     * higher-frequency sampling without changing business logic.
     */
    @Scheduled(fixedDelay = 30_000)
    public void checkConsistency() {
        dialerFacade.getCampaignContexts().values().forEach(ctx -> {
            Campaign campaign = ctx.getCampaign();

            int stored     = campaign.getAgentsSize().get();
            int calculated = acdEngine.countCampaignAllActiveUsers(campaign); // ✅ same semantics
            int diff       = Math.abs(stored - calculated);

            if (diff > DISCREPANCY_THRESHOLD) {
                LOG.warn("[AGENT_SIZE_CHECK] Discrepancy detected — campaignId={} storedAgentsSize={} calculatedAgentsSize={} diff={}",
                        campaign.getId(), stored, calculated, diff);
            }
        });
    }
}