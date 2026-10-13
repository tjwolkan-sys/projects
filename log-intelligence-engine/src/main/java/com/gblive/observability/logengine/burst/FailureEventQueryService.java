package com.gblive.observability.logengine.burst;

import com.gblive.observability.logengine.model.RawLogEvent;

import java.time.Instant;
import java.util.List;

public interface FailureEventQueryService {

    List<RawLogEvent> query(
            Instant windowStart,
            Instant windowEnd,
            List<String> callIds
    );
}
