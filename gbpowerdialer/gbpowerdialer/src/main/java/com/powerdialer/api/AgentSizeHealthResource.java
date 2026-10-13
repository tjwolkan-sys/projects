package com.powerdialer.api;

import com.powerdialer.engine.acd.ACDEngine;
import com.powerdialer.engine.dialer.DialerFacade;
import com.powerdialer.model.Campaign;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for monitoring agent size consistency across running campaigns.
 *
 * <p>Provides pull-based health check endpoints to detect discrepancies between
 * the stored {@code agentsSize} counter (maintained via increment/decrement on
 * login/logout events) and the actual count recalculated from {@link ACDEngine}
 * at runtime.</p>
 *
 * <p>Intended for use during load testing and production monitoring.
 * Use {@code /discrepancies} for lightweight polling — returns an empty list
 * when all campaign agent counters are consistent.</p>
 */
@RestController
@RequestMapping(value = "/api/health/agentSize", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AgentSizeHealthResource {

    /** Minimum absolute difference between stored and calculated to flag as discrepancy. */
    private static final int DISCREPANCY_THRESHOLD = 2;

    private final DialerFacade dialerFacade;
    private final ACDEngine acdEngine;

    /**
     * Returns an agent size report for every currently running campaign.
     *
     * @return list of {@link AgentSizeReport} for all active campaign contexts
     */
    @GetMapping
    public List<AgentSizeReport> getAgentSizeReport() {
        return dialerFacade.getCampaignContexts().values().stream()
                .map(ctx -> buildReport(ctx.getCampaign()))
                .collect(Collectors.toList());
    }

    /**
     * Returns only campaigns whose stored {@code agentsSize} differs from
     * the value recalculated by {@link ACDEngine} beyond {@value DISCREPANCY_THRESHOLD}.
     *
     * <p>Use this endpoint for load-test polling.
     * An empty response means all campaign agent counters are consistent.</p>
     *
     * @return list of {@link AgentSizeReport} entries with a discrepancy;
     *         empty list if everything is consistent
     */
    @GetMapping("/discrepancies")
    public List<AgentSizeReport> getDiscrepancies() {
        return dialerFacade.getCampaignContexts().values().stream()
                .map(ctx -> buildReport(ctx.getCampaign()))
                .filter(AgentSizeReport::isDiscrepancy)
                .collect(Collectors.toList());
    }

    /**
     * Returns a detailed agent size report for a single campaign,
     * including a per-user breakdown (id, group, status, idle flag).
     *
     * <p>Use this endpoint after a discrepancy is detected to identify
     * which users are being counted incorrectly.</p>
     *
     * @param campaignId the id of the campaign to inspect
     * @return {@link AgentSizeDetailReport} for the given campaign,
     *         or {@code null} if no active context exists for that id
     */
    @GetMapping("/campaign/{campaignId}")
    public AgentSizeDetailReport getCampaignDetail(@PathVariable Long campaignId) {
        return Optional.ofNullable(dialerFacade.getCampaignContexts().get(campaignId))
                .map(ctx -> buildDetailReport(ctx.getCampaign()))
                .orElse(null);
    }

    // ── private builders ──────────────────────────────────────────────────────

    /**
     * Builds a summary report by comparing the stored counter against the
     * value recalculated from {@link ACDEngine} for the given campaign.
     * Does not mutate campaign state — all ACDEngine calls are pure.
     *
     * @param campaign the campaign to evaluate
     * @return populated {@link AgentSizeReport}
     */
    private AgentSizeReport buildReport(Campaign campaign) {
        int stored      = campaign.getAgentsSize() != null ? campaign.getAgentsSize().get() : 0;
        int storedAvail = campaign.getAvailAgentsSize() != null ? campaign.getAvailAgentsSize().get() : 0;
        int calculated  = acdEngine.countCampaignAllActiveUsers(campaign);
        int idle        = acdEngine.countCampaignIdleUsers(campaign);
        int available   = acdEngine.countCampaignAvailableUsersNow(campaign);
        int diff        = stored - calculated;

        return AgentSizeReport.builder()
                .campaignId(campaign.getId())
                .campaignName(campaign.getName())
                .storedAgentsSize(stored)
                .storedAvailAgentsSize(storedAvail)
                .calculatedAgentsSize(calculated)
                .idleUsersCount(idle)
                .availableUsersCount(available)
                .discrepancy(Math.abs(diff) > DISCREPANCY_THRESHOLD)
                .discrepancyAmount(diff)
                .build();
    }

    /**
     * Builds a detailed report combining the summary with a per-user breakdown
     * of every user currently associated with the campaign.
     *
     * @param campaign the campaign to inspect in detail
     * @return populated {@link AgentSizeDetailReport}
     */
    private AgentSizeDetailReport buildDetailReport(Campaign campaign) {
        List<UserAgentInfo> users = acdEngine.getCampaignUsers(campaign).stream()
                .map(u -> UserAgentInfo.builder()
                        .userId(u.getId())
                        .username(u.getUsername())
                        .groupId(u.getGroup() != null ? u.getGroup().getId() : null)
                        .status(u.getStatus() != null ? u.getStatus().getName() : "N/A")
                        .availableForDialer(u.getStatus() != null
                                && u.getStatus().isAvailableForDialerCalls())
                        .idle(u.getGroup() != null
                                && acdEngine.getIdleUsersInGroup(u.getGroup()) != null
                                && acdEngine.getIdleUsersInGroup(u.getGroup()).contains(u))
                        .build())
                .collect(Collectors.toList());

        return AgentSizeDetailReport.builder()
                .summary(buildReport(campaign))
                .users(users)
                .build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /**
     * Summary DTO holding stored vs. calculated agent size for one campaign.
     */
    @Getter
    @Builder
    public static class AgentSizeReport {

        /** Database/context id of the campaign. */
        private final Long campaignId;

        /** Human-readable name of the campaign. */
        private final String campaignName;

        /** Agent size maintained by increment/decrement on login/logout events. */
        private final int storedAgentsSize;

        /** Available agent size maintained by {@code setAvailAgentsSize} on status changes. */
        private final int storedAvailAgentsSize;

        /**
         * Agent size recalculated on demand from {@link ACDEngine}
         * by inspecting all active and idle users matching the campaign's
         * group and skill requirements.
         */
        private final int calculatedAgentsSize;

        /** Number of users currently in the idle queue for this campaign. */
        private final int idleUsersCount;

        /**
         * Number of users currently available for dialer calls —
         * combines idle queue users and active-call users with available status.
         */
        private final int availableUsersCount;

        /**
         * {@code true} when {@code Math.abs(stored - calculated)} exceeds
         * {@value DISCREPANCY_THRESHOLD}, indicating a counter drift.
         */
        private final boolean discrepancy;

        /**
         * Signed difference: {@code storedAgentsSize - calculatedAgentsSize}.
         * Positive means over-counting; negative means under-counting.
         */
        private final int discrepancyAmount;
    }

    /**
     * Detailed DTO combining a campaign-level summary with a per-user breakdown.
     * Returned only by the single-campaign detail endpoint.
     */
    @Getter
    @Builder
    public static class AgentSizeDetailReport {

        /** Campaign-level summary including discrepancy flags. */
        private final AgentSizeReport summary;

        /** Individual agent entries for every user matched to this campaign. */
        private final List<UserAgentInfo> users;
    }

    /**
     * Lightweight snapshot of a single user's state within a campaign context,
     * used to diagnose agent size discrepancies.
     */
    @Getter
    @Builder
    public static class UserAgentInfo {

        /** Primary key of the user. */
        private final Long userId;

        /** Login username. */
        private final String username;

        /** Id of the group the user belongs to; {@code null} if unassigned. */
        private final Long groupId;

        /** Name of the user's current status; {@code "N/A"} if no status is set. */
        private final String status;

        /** {@code true} if the user is present in the idle queue of their group. */
        private final boolean idle;

        /** {@code true} if {@code user.getStatus().isAvailableForDialerCalls()} returns true. */
        private final boolean availableForDialer;
    }
}