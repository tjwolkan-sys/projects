package com.powerdialer.engine.scheduler.schedulechain.model;

import com.powerdialer.engine.scheduler.schedulechain.handler.*;

public enum ScheduleCheckType {
    PRE_CHECK(SchedulePreCheckHandler.class),
    START_END(StartEndDateCheckHandler.class),
    BLACKOUT(BlackoutScheduleCheckHandler.class),
    SCHEDULE_TIME(ScheduleTimeCheckHandler.class),
    AUTOMATION(AutomationCheckHandler.class);

    private final Class<? extends ScheduleCheckHandler> handlerClass;

    ScheduleCheckType(Class<? extends ScheduleCheckHandler> handlerClass) {
        this.handlerClass = handlerClass;
    }

    public Class<? extends ScheduleCheckHandler> getHandlerClass() {
        return handlerClass;
    }
}
