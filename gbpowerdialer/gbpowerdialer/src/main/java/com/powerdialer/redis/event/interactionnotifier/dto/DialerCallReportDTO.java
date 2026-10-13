package com.powerdialer.redis.event.interactionnotifier.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.powerdialer.model.Call;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DialerCallReportDTO {

    // Agent section
    @JsonProperty("agent")
    private AgentInfo agent;

    // Call section
    @JsonProperty("call")
    private CallInDTO call;

    // PD_CDR section
    @JsonProperty("pd_cdr")
    private CdrInDTO pdCdr;

    @JsonProperty("pd_cdr2")
    private CdrInDTO pdCdr2;

    @JsonProperty("pd_cdr3")
    private CdrInDTO pdCdr3;

    @JsonProperty("pd_cdr4")
    private CdrInDTO pdCdr4;

    // Campaign section
    @JsonProperty("campaign")
    private CampaignInDTO campaign;

    // Additional section
    @JsonProperty("additional")
    private AdditionalInfo additional;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentInfo {
        private String username;
        private String nickname;
        private String fullname;
    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdditionalInfo {
        private String interaction_type = "OUTBOUND_DIALER";
    }
}