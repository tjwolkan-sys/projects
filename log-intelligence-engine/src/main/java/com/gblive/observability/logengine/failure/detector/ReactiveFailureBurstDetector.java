package com.gblive.observability.logengine.failure.detector;

import com.gblive.observability.logengine.failure.config.FailureDetectorConfig;
import com.gblive.observability.logengine.failure.model.FailureIncident;
import com.gblive.observability.logengine.failure.model.RcaEvent;
import com.gblive.observability.logengine.failure.model.RootCauseType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReactiveFailureBurstDetector {

    private final FailureDetectorConfig config;

    public ReactiveFailureBurstDetector(FailureDetectorConfig config) {
        this.config = config;
    }

    public Flux<FailureIncident> detect(Flux<RcaEvent> events) {

        return Flux.fromIterable(config.getWindows())
                .flatMap(window ->
                        events
                                .window(window.getDuration())
                                .flatMap(this::analyzeWindow)
                );
    }

    private Flux<FailureIncident> analyzeWindow(Flux<RcaEvent> windowFlux) {

        return windowFlux
                .groupBy(RcaEvent::type)
                .flatMap(group ->
                        group.collectList()
                                .flatMapMany(events -> evaluate(group.key(), events))
                );
    }

    private Flux<FailureIncident> evaluate(RootCauseType type, List<RcaEvent> events) {

        var baseline = config.getBaselines().get(type);
        if (baseline == null || events.isEmpty()) {
            return Flux.empty();
        }

        long count = events.size();

        if (count < baseline.getBurstThreshold()) {
            return Flux.empty();
        }

        Instant start = events.get(0).timestamp();
        Instant end = events.get(events.size() - 1).timestamp();

        Set<String> hosts = events.stream()
                .map(RcaEvent::host)
                .collect(Collectors.toSet());

        return Flux.just(
                new FailureIncident(
                        type,
                        start,
                        end,
                        count,
                        baseline.getSeverity(),
                        hosts
                )
        );
    }
}
