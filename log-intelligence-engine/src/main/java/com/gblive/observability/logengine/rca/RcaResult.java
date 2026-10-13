package com.gblive.observability.logengine.rca;

import java.util.List;

public record RcaResult(
        String callId,
        String rootCause,
        String description,
        double confidence,
        List<String> evidence
) {}
