package com.powerdialer.engine.scheduler.schedulechain.handler;

import com.powerdialer.engine.scheduler.EvaluationResultContext;
import com.powerdialer.engine.scheduler.ScheduleAutomationEngine;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckResult;
import com.powerdialer.model.Campaign;
import com.powerdialer.model.ScheduleAutomation;
import com.powerdialer.util.Constants;
import java.time.LocalDateTime;

public class AutomationCheckHandler implements ScheduleCheckHandler {

    private final ScheduleAutomationEngine engine;
    private ScheduleCheckHandler next;

    public AutomationCheckHandler(ScheduleAutomationEngine engine) {
        this.engine = engine;
    }

    @Override
    public void setNext(ScheduleCheckHandler next) {
        this.next = next;
    }

    @Override
    public void check(Campaign campaign, LocalDateTime now, ScheduleCheckResult result) {
        if (Boolean.TRUE.equals(campaign.getScheduleAutomationEnabled())) {
            ScheduleAutomation startAutomation =
                    campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.START);
            if (startAutomation != null) {
                EvaluationResultContext ctx = engine.processScheduleAutomation(startAutomation);
                if (ctx.getResultBoolean()) {
                    result.setMustBeStarted(true);
                }
            }

            ScheduleAutomation stopAutomation =
                    campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.STOP);
            if (stopAutomation != null) {
                EvaluationResultContext ctx = engine.processScheduleAutomation(stopAutomation);
                if (ctx.getResultBoolean()) {
                    if (result.isMustBeStarted()) {
                        result.setMustBeStarted(false);
                    }
                    result.setMustBeStopped(true);
                }
            }

            ScheduleAutomation pauseAutomation =
                    campaign.getScheduleAutomationByType(Constants.SCHEDULE_ACTION_TYPE.PAUSE);
            if (pauseAutomation != null) {
                EvaluationResultContext ctx = engine.processScheduleAutomation(pauseAutomation);
                if (ctx.getResultBoolean()) {
                    if (result.isMustBeStarted()) {
                        result.setMustBeStarted(false);
                    }
                    result.setMustBePaused(true);
                }
            }
        }

        if (next != null) {
            next.check(campaign, now, result);
        }
    }
}
