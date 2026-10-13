package com.gblive.observability.logengine.ai.model;

import java.util.List;

public record TimelineSummary(
        String callId,
        String application,
        String environment,
        List<TimelineStep> steps
) {

    public record TimelineStep(
            String eventType,
            String time
    ) {}
}
