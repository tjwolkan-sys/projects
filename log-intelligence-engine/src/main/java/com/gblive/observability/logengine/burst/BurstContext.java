package com.gblive.observability.logengine.burst;

import java.util.List;

public record BurstContext(
        FailureBurst burst,
        List<FailureSample> samples
) {}
