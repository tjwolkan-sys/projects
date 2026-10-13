package com.gblive.observability.logengine.failure.model;

public record FailureBaseline(
        RootCauseType type,
        int normalRatePerMinute,
        int burstThreshold,
        BurstSeverity severity
) {}
