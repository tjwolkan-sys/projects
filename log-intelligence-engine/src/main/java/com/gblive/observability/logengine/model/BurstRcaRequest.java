package com.gblive.observability.logengine.model;

import java.time.Instant;
import java.util.List;

public record BurstRcaRequest(

        Instant windowStart,
        Instant windowEnd,

        List<String> callIds

) {}
