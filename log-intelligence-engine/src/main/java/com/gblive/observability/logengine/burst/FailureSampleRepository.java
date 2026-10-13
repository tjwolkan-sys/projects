package com.gblive.observability.logengine.burst;


import java.time.Instant;
import java.util.List;

public interface FailureSampleRepository {

    List<FailureSample> findFailures(
            Instant windowStart,
            Instant windowEnd,
            List<String> callIds
    );
}
