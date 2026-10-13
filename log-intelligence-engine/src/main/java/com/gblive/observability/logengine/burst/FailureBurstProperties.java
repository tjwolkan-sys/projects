package com.gblive.observability.logengine.burst;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "burst")
public record FailureBurstProperties(
        Duration window,          // örn: 30s, 60s
        int minFailures,          // örn: 5
        double minAvgConfidence   // örn: 0.7
) {}
