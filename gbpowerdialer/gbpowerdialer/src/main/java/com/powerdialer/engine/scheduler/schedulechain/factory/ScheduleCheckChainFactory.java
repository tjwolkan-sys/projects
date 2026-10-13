package com.powerdialer.engine.scheduler.schedulechain.factory;

import com.powerdialer.engine.scheduler.ScheduleAutomationEngine;
import com.powerdialer.engine.scheduler.schedulechain.handler.*;
import com.powerdialer.engine.scheduler.schedulechain.model.ScheduleCheckType;
import org.springframework.stereotype.Component;

@Component
public class ScheduleCheckChainFactory {

    private final ScheduleAutomationEngine automationEngine;

    public ScheduleCheckChainFactory(ScheduleAutomationEngine automationEngine) {
        this.automationEngine = automationEngine;
    }

    public ScheduleCheckHandler buildChain() {
        ScheduleCheckHandler head = null;
        ScheduleCheckHandler current = null;

        for (ScheduleCheckType type : ScheduleCheckType.values()) {
            ScheduleCheckHandler handler = createHandler(type);
            if (head == null) {
                head = handler;
                current = handler;
            } else {
                current.setNext(handler);
                current = handler;
            }
        }

        return head;
    }

    private ScheduleCheckHandler createHandler(ScheduleCheckType type) {
        if (type == ScheduleCheckType.AUTOMATION) {
            return new AutomationCheckHandler(automationEngine);
        }


        try {
            return type.getHandlerClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Unable to create handler for type " + type);
        }
    }
}
