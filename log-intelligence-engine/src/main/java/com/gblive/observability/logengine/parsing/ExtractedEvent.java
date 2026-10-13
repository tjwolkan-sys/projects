package com.gblive.observability.logengine.parsing;

import java.time.Instant;

public record ExtractedEvent(
        EventType type,
        String callId,
        String campaignName,
        String hostname,
        String severity,
        Instant timestamp,
        double confidence,
        String rawMessage
) {}
