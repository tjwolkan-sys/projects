package com.gblive.observability.logengine.burst;

import java.time.Instant;

public record FailureSample(
        String callId,
        Instant timestamp,
        String rootCause,
        double confidence
) {}
