package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class StartEndDateCheckHandler implements ScheduleCheckHandler {

    private ScheduleCheckHandler next;

    @Override
    public void setNext(ScheduleCheckHandler next) {
        this.next = next;
    }


    @Override
    public void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        if (now.isAfter(LocalDateTime.ofInstant(campaign.getStartDate().toInstant(), ZoneId.systemDefault())) &&
            now.isBefore(LocalDateTime.ofInstant(campaign.getEndDate().toInstant(), ZoneId.systemDefault()))) {
            if (next != null) next.check(campaign, now, result);
        } else {
            result.setMustBeStopped(true);
        }
    }
}