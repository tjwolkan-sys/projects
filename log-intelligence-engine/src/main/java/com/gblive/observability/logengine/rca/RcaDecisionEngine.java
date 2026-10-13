package com.gblive.observability.logengine.rca;

import com.gblive.observability.logengine.aggregation.CallTimeline;
import com.gblive.observability.logengine.parsing.EventType;
import com.gblive.observability.logengine.parsing.ExtractedEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RcaDecisionEngine {

    public RcaResult analyze(CallTimeline timeline) {

        List<EventType> eventTypes = timeline.events()
                .stream()
                .map(ExtractedEvent::type)
                .toList();

        // 🔥 Rule priority sırası önemlidir
        for (RcaRule rule : RcaRule.values()) {
            if (eventTypes.containsAll(rule.requiredEvents)) {
                return buildResult(timeline.callId(), rule);
            }
        }

        return buildResult(timeline.callId(), RcaRule.UNKNOWN);
    }

    private RcaResult buildResult(String callId, RcaRule rule) {
        return new RcaResult(
                callId,
                rule.code,
                rule.description,
                rule.confidence,
                rule.requiredEvents
                        .stream()
                        .map(Enum::name)
                        .toList()
        );
    }
}
