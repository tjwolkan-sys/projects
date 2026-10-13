package com.powerdialer.redis.event.predictive;

import com.powerdialer.engine.predictive.beta.model.PeriodicCallData;
import com.powerdialer.engine.predictive.beta.model.PredictiveCallParameters;
import com.powerdialer.engine.predictive.beta.model.PredictiveCampaignCallData;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.PredictiveEvent;
import com.powerdialer.util.Constants.PREDICTIVE_STRATEGY;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

import java.util.List;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PredictiveBetaEvent extends PredictiveEvent {

    private final String predictiveType;
    private Integer callPerSecond;
    private Long campaignId;
    private String campaignName;
    private String campaignState;
    private Integer completionPercent;
    private Integer cacheLimit;
    private Integer ringingCount;
    private Integer availableAgentsCount;
    private Integer totalAgentsCount;
    private Integer incallAgentsCount;
    private Integer concurrenLines;
    private Integer maxLines;
    private PeriodicCallData currentData;
    private PeriodicCallData previousData;
    private PredictiveCallParameters predictiveCallParameters;

    public PredictiveBetaEvent(String publisher, PredictiveCampaignCallData data) {

        super(publisher);
        Campaign c = data.getCampaign();
        this.predictiveType = PREDICTIVE_STRATEGY.BETA.name();
        this.callPerSecond = c.getCallsPerSecond();
        this.campaignId = c.getId();
        this.completionPercent = c.getCompletionPercent();
        this.campaignName = c.getName();
        this.campaignState = StringUtils.hasText(c.getState().name()) ? c.getState().toString() : "UNKNOWN";
        this.cacheLimit = c.getCacheLimit();
        this.availableAgentsCount = Math.toIntExact(c.getAvailableUsersCount());
        this.totalAgentsCount = c.getAgentsSize().get();
        this.incallAgentsCount = c.getInCalls().get();
        this.ringingCount = c.getRingingCalls().get();
        this.concurrenLines = c.getConcurrentLines();
        this.maxLines = c.getMaxLines();
        this.currentData = data.getCurrentData();
        this.previousData = data.getPreviousData();
        this.predictiveCallParameters = data.getPredictiveCallParameters();
    }
}
