package com.powerdialer.engine.scheduler.schedulechain.orchestrator;

import com.powerdialer.engine.scheduler.schedulechain.factory.ScheduleCheckChainFactory;
import com.powerdialer.engine.scheduler.schedulechain.handler.ScheduleCheckHandler;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CampaignSchedulerOrchestrator {

    private final ScheduleCheckChainFactory chainFactory;

    public CampaignSchedulerOrchestrator(ScheduleCheckChainFactory chainFactory) {
        this.chainFactory = chainFactory;
    }

    public ScheduleCheckResult evaluateCampaign(Campaign campaign) {
        ScheduleCheckResult result = new ScheduleCheckResult();
        ScheduleCheckHandler handler = chainFactory.buildChain();
        LocalDateTime applicationNow = LocalDateTime.now(Constants.ISTANBUL_ZONE);
        handler.check(campaign, applicationNow, result);
        return result;
    }
}