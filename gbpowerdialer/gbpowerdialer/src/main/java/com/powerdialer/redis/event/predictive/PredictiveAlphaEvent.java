package com.powerdialer.redis.event.predictive;

import com.powerdialer.engine.boost.CampaignBoostData;
import com.powerdialer.engine.boost.PeriodicBoostData;
import com.powerdialer.model.Campaign;
import com.powerdialer.redis.event.PredictiveEvent;
import com.powerdialer.util.Constants;
import com.powerdialer.util.Constants.PREDICTIVE_STRATEGY;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.EqualsAndHashCode;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PredictiveAlphaEvent extends PredictiveEvent {

    private final String predictiveType;
    private Integer maxLines;
    private Float maxAnswerRate;
    private Float maxAbandonedRate;
    private Float dialingBoost;
    private Integer callPerSecond;
    private Long campaignId;
    private Integer completionPercent;
    private String campaignName;
    private String campaignState;
    private Integer cacheLimit;
    private Integer availableAgentsCount;
    private Integer totalAgentsCount;
    private Integer incallAgentsCount;
    private Integer concurrentines;
    private PeriodicBoostData currentData;
    private PeriodicBoostData previousData;
    private List<PeriodicBoostData> dataHistory;

    public PredictiveAlphaEvent(String publisher, CampaignBoostData data) {
        super(publisher);
        Campaign c = data.getCampaign();
        this.maxLines = c.getMaxLines();
        this.predictiveType = PREDICTIVE_STRATEGY.ALPHA.name();
        this.maxAnswerRate = c.getMaxAnswerRate();
        this.maxAbandonedRate = c.getMaxAbandonRate();
        this.callPerSecond = c.getCallsPerSecond();
        this.dialingBoost = c.getDialingBoost();
        this.campaignId = c.getId();
        this.completionPercent = c.getCompletionPercent();
        this.campaignName = c.getName();
        this.campaignState = Objects.nonNull(c.getState()) ? c.getState().toString() : "UNKNOWN";
        this.cacheLimit = c.getCacheLimit();
        this.availableAgentsCount = Math.toIntExact(c.getAvailableUsersCount());
        this.totalAgentsCount = c.getAgentsSize().get();
        this.incallAgentsCount = c.getInCalls().get();
        this.concurrentines = c.getConcurrentLines();
        this.currentData = data.getCurrentData();
        this.previousData = data.getPreviousData();
        this.dataHistory = data.getDataHistory();
    }

}
