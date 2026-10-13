package com.powerdialer.redis.event.interactionnotifier.mapper;

import com.powerdialer.model.CDR;
import com.powerdialer.model.Call;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.User;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO.AdditionalInfo;
import com.powerdialer.redis.event.interactionnotifier.dto.DialerCallReportDTO.AgentInfo;

public final class DialerCallReportMapper {

    private DialerCallReportMapper() {}

    public static DialerCallReportDTO toDto(Call call) {
        return toDto(call, null);
    }

    /**
     * Same mapping as {@link #toDto(Call)} but the agent section is built from the supplied
     * {@code agent} when present, without touching the live {@link Call}.
     *
     * <p>The report needs DB-only agent fields (e.g. {@code adNickname}), so the caller refreshes
     * the agent from persistence. That refreshed entity is detached and its lazy {@code group}
     * association is an uninitialized Hibernate proxy, therefore it must NEVER be written back onto
     * the live {@code Call}: the same {@code Call} instance stays registered in
     * {@code CallUpToDateKeeper} and the ACD hot path scans it on every originate tick. Passing the
     * agent here keeps the detached entity out of shared runtime state.</p>
     *
     * @param call  call to report
     * @param agent agent to use for the agent section; falls back to {@code call.getUser()} if null
     */
    public static DialerCallReportDTO toDto(Call call, User agent) {
        if (call == null) {
            return null;
        }

        DialerCallReportDTO.DialerCallReportDTOBuilder report = DialerCallReportDTO.builder();

        // Agent
        User user = agent != null ? agent : call.getUser();
        report.agent(AgentInfo.builder()
                .username(user != null ? user.getUsername() : null)
                .nickname(user != null && user.getAdNickname() != null ? user.getAdNickname() : null)
                .fullname(user != null && user.getFullname() != null ? user.getFullname() : null)
                .build());

        // Call
        report.call(CallInMapper.toDto(call));

        // CDR
        CDR cdr = call.getCdr();
        report.pdCdr(CdrInMapper.toDto(cdr));

        // Campaign
        Campaign campaign = call.getCampaign();
        report.campaign(CampaignInMapper.toDto(campaign,call));

        // Additional
        report.additional(AdditionalInfo.builder()
                .interaction_type("OUTBOUND_DIALER")
                .build());

        return report.build();
    }
}