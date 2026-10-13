package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;

import java.time.LocalDateTime;

public interface ScheduleCheckHandler {

    void setNext(ScheduleCheckHandler next);
    void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result);

}