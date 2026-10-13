package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ScheduleBlackoutAutomation;
import com.powerdialer.util.Constants;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class BlackoutScheduleCheckHandler implements ScheduleCheckHandler {

    private ScheduleCheckHandler next;

    @Override
    public void setNext(ScheduleCheckHandler next) {
        this.next = next;
    }

    @Override
    public void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        List<ScheduleBlackoutAutomation> blackoutList = campaign.getBlackoutSchedules();
        boolean blackoutFound = false;

        if (blackoutList != null) {
            for (ScheduleBlackoutAutomation blackout : blackoutList) {
                LocalDateTime start = toLocalDateTime(blackout.getStartDate());
                LocalDateTime end = toLocalDateTime(blackout.getEndDate());

                if (!now.isBefore(start) && now.isBefore(end)) {
                    blackoutFound = true;

                    if (result.isMustBeStarted()) {
                        result.setMustBeStarted(false);
                    }

                    result.setMustBeStopped(true);
                    break;
                }
            }
        }

        // Delegate to next handler if no active blackout found
        if (!blackoutFound && next != null) {
            next.check(campaign, now, result);
        }
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(Constants.ISTANBUL_ZONE).toLocalDateTime();
    }
}
