package com.gblive.observability.logengine.model;

import java.time.Instant;

public record RawLogEvent(
        String index,
        Instant timestamp,
        String applicationName,
        String hostname,
        String message
) {}
