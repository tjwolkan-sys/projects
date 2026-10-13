package com.gblive.observability.logengine.burst;

import com.gblive.observability.logengine.model.RawLogEvent;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class ElasticsearchFailureSampleRepository
        implements FailureSampleRepository {

    private final FailureEventQueryService queryService;

    public ElasticsearchFailureSampleRepository(
            FailureEventQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @Override
    public List<FailureSample> findFailures(
            Instant windowStart,
            Instant windowEnd,
            List<String> callIds
    ) {

        List<RawLogEvent> logs =
                queryService.query(windowStart, windowEnd, callIds);

        return logs.stream()
                .map(this::toFailureSample)
                .filter(sample -> !"UNKNOWN".equals(sample.rootCause()))
                .toList();
    }

    private FailureSample toFailureSample(RawLogEvent log) {

        String rootCause =
                FailureClassifier.classify(log.message());

        return new FailureSample(
                extractCallId(log.message()),
                log.timestamp(),
                rootCause,
                confidenceFor(rootCause)
        );
    }

    private double confidenceFor(String rootCause) {

        return switch (rootCause) {
            case "ASTERISK_CONNECTION_REFUSED" -> 0.95;
            case "ASTERISK_TIMEOUT" -> 0.90;
            case "REDIS_PUBSUB_TIMEOUT" -> 0.85;
            case "REDIS_CONNECTION_LOST" -> 0.85;
            default -> 0.50;
        };
    }

    private String extractCallId(String message) {
        return CallIdExtractor
                .extract(message)
                .orElse("unknown");
    }
}
