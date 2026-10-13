package com.gblive.observability.logengine.burst;

import java.time.Instant;
import java.util.Set;

public record BurstRcaResult(
        Instant windowStart,
        Instant windowEnd,
        String rootCause,
        String description,
        double confidence,
        Set<String> affectedCallIds
) {}
