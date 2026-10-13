package com.gblive.observability.logengine.aggregation;

import com.gblive.observability.logengine.parsing.ExtractedEvent;

import java.util.List;

public record CallTimeline(
        String callId,
        List<ExtractedEvent> events
) {}
