package com.gblive.observability.logengine.aggregation;

import com.gblive.observability.logengine.parsing.ExtractedEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Comparator;
import java.util.Objects;

@Component
public class CallTimelineAggregator {

    public Flux<CallTimeline> aggregate(Flux<ExtractedEvent> events) {

        return events
                // ✅ callId'siz eventleri ayıkla (infra vs)
                .filter(e -> Objects.nonNull(e.callId()))

                // ✅ callId bazlı grupla
                .groupBy(ExtractedEvent::callId)

                // ✅ her grup için timeline oluştur
                .flatMap(groupedFlux ->
                        groupedFlux
                                .sort(Comparator.comparing(ExtractedEvent::timestamp))
                                .collectList()
                                .map(list -> new CallTimeline(groupedFlux.key(), list))
                );
    }
}
