package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.util.Constants;

import java.time.LocalDateTime;

public class SchedulePreCheckHandler implements ScheduleCheckHandler {

    private ScheduleCheckHandler next;

    @Override
    public void setNext(ScheduleCheckHandler next) {
        this.next = next;
    }

    //TODO : should if condition have isStoppedbyCondition check also?

    @Override
    public void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        if (isManuallyStopped(campaign)) {
            return;
        }
        if (next != null) next.check(campaign, now, result);
    }


    private boolean isManuallyStopped(Campaign campaign) {
        return Constants.CAMPAIGN_STOP_TYPE.MANUAL.equals(campaign.getStopType());
    }

}
