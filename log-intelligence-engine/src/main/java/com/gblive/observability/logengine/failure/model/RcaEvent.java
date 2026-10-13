package com.gblive.observability.logengine.failure.model;

import java.time.Instant;

public record RcaEvent(
        Instant timestamp,
        String application,
        String host,
        String message,
        RootCauseType type
) {}
