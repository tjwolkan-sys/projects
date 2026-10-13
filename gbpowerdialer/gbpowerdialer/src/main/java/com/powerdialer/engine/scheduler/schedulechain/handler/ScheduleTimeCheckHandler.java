package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.Schedule;
import com.powerdialer.util.Constants;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

public class ScheduleTimeCheckHandler implements ScheduleCheckHandler {

    private ScheduleCheckHandler next;

    @Override
    public void setNext(ScheduleCheckHandler next) {
        this.next = next;
    }

    @Override
    public void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        if (campaign.getScheduleEnabled()) {
            Constants.DAY_OF_WEEK today = getCurrentDayOfWeek(now);
            for (Schedule schedule : campaign.getEnabledSchedules()) {
                result.setResetEnabled(schedule.isResetEnabled());
                if (isScheduleForToday(schedule, today)) {
                    if(evaluateScheduleTimeFrame(schedule, now, result)){
                        delegateToNextHandler(campaign, now, result);
                    }
                    break;
                }
            }
        }
    }

    private Constants.DAY_OF_WEEK getCurrentDayOfWeek(LocalDateTime now) {
        int day = now.getDayOfWeek().getValue();
        return Constants.DAY_OF_WEEK.intToDayOfWeek(day);
    }

    private boolean isScheduleForToday(Schedule schedule, Constants.DAY_OF_WEEK today) {
        return schedule.getDay().equals(today);
    }

    private boolean evaluateScheduleTimeFrame(Schedule schedule, LocalDateTime now, ScheduleCheckResult result) {
        LocalTime nowTime = now.toLocalTime();
        LocalTime from = toLocalTime(schedule.getFrom());
        LocalTime till = toLocalTime(schedule.getTill());

        if (!nowTime.isBefore(from) && nowTime.isBefore(till)) {
            result.setMustBeStarted(true);
            return true;
        } else if (!nowTime.isBefore(till)) {
            result.setMustBeStopped(true);
            return true;
        }
        return false;
    }

    private LocalTime toLocalTime(Date date) {
        return date.toInstant().atZone(Constants.ISTANBUL_ZONE).toLocalTime();
    }

    private void delegateToNextHandler(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        if (next != null) {
            next.check(campaign, now, result);
        }
    }



}