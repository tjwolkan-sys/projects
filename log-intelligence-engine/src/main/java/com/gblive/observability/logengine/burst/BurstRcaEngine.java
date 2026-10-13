package com.gblive.observability.logengine.burst;

import org.springframework.stereotype.Service;

@Service
public class BurstRcaEngine {

    public BurstRcaResult analyze(BurstContext context) {
        FailureBurst burst = context.burst();

        String cause = burst.rootCause();
        String description = buildDescription(burst);
        double confidence = baseConfidenceFor(cause, burst.failureCount());

        return new BurstRcaResult(
                burst.windowStart(),
                burst.windowEnd(),
                cause,
                description,
                confidence,
                burst.callIds()
        );
    }

    private String buildDescription(FailureBurst burst) {
        return "Detected a failure burst for root cause '%s' between %s and %s, " +
                "affecting %d calls."
                .formatted(
                        burst.rootCause(),
                        burst.windowStart(),
                        burst.windowEnd(),
                        burst.failureCount()
                );
    }

    private double baseConfidenceFor(String cause, int failureCount) {

        double base = 0.6;

        if (failureCount >= 10) {
            base += 0.15;
        } else if (failureCount >= 5) {
            base += 0.1;
        }

        if (cause != null && cause.toLowerCase().contains("connection")) {
            base += 0.05;
        }

        return Math.min(base, 0.95);
    }
}
