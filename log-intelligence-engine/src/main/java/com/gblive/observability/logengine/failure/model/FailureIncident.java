package com.gblive.observability.logengine.failure.model;

import java.time.Instant;
import java.util.Set;

public record FailureIncident(
        RootCauseType type,
        Instant windowStart,
        Instant windowEnd,
        long count,
        BurstSeverity severity,
        Set<String> hosts
) {}
