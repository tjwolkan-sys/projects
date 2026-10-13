package com.gblive.observability.logengine.model;

import java.time.Instant;
import java.util.Set;

public record BurstRcaResponse(

        Instant windowStart,
        Instant windowEnd,

        String rootCause,
        String description,

        double confidence,

        Set<String> affectedCallIds
) {}
