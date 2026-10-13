package com.gblive.observability.logengine.burst;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FailureBurstDetector {

    private final FailureBurstProperties properties;

    public FailureBurstDetector(FailureBurstProperties properties) {
        this.properties = properties;
    }

    /**
     * Core algoritma – in-memory & deterministik
     */
    public List<FailureBurst> detectBursts(List<FailureSample> samples) {
        if (samples.isEmpty()) {
            return List.of();
        }

        // Zaman bazlı sıralama
        List<FailureSample> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparing(FailureSample::timestamp));

        List<FailureBurst> bursts = new ArrayList<>();

        int n = sorted.size();
        int j = 0;

        for (int i = 0; i < n; i++) {
            FailureSample startSample = sorted.get(i);
            Instant windowStart = startSample.timestamp();
            Instant windowEnd = windowStart.plus(properties.window());

            // window içindeki son index'i bul
            while (j < n && !sorted.get(j).timestamp().isAfter(windowEnd)) {
                j++;
            }

            List<FailureSample> windowSlice = sorted.subList(i, j);

            // rootCause bazlı grupla
            Map<String, List<FailureSample>> byCause = windowSlice.stream()
                    .collect(Collectors.groupingBy(FailureSample::rootCause));

            for (Map.Entry<String, List<FailureSample>> entry : byCause.entrySet()) {
                String cause = entry.getKey();
                List<FailureSample> causeSamples = entry.getValue();

                int count = causeSamples.size();
                if (count < properties.minFailures()) {
                    continue;
                }

                double avgConf = causeSamples.stream()
                        .mapToDouble(FailureSample::confidence)
                        .average()
                        .orElse(0.0);

                if (avgConf < properties.minAvgConfidence()) {
                    continue;
                }

                Set<String> callIds = causeSamples.stream()
                        .map(FailureSample::callId)
                        .collect(Collectors.toSet());

                bursts.add(new FailureBurst(
                        windowStart,
                        windowEnd,
                        cause,
                        count,
                        callIds,
                        avgConf
                ));
            }
        }

        // İstersen burada overlapping burst’leri merge edebilirsin.
        return bursts;
    }

    /**
     * Reactive adapter – Flux → List → Flux
     */
    public Flux<FailureBurst> detectBursts(Flux<FailureSample> failures) {
        return failures
                .collectList()
                .flatMapMany(list -> Flux.fromIterable(detectBursts(list)));
    }
}
