package com.gblive.observability.logengine.burst;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record FailureBurst(
        Instant windowStart,
        Instant windowEnd,
        String rootCause,
        int failureCount,
        Set<String> callIds,
        double averageConfidence
) {

    public static FailureBurst fromSamples(
            Instant start,
            Instant end,
            List<FailureSample> samples
    ) {

        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Failure samples cannot be empty");
        }

        // 1️⃣ rootCause string’e göre grupla
        Map<String, Long> byCause = samples.stream()
                .collect(Collectors.groupingBy(
                        FailureSample::rootCause,
                        Collectors.counting()
                ));

        // 2️⃣ En çok görülen rootCause’u seç
        Map.Entry<String, Long> dominant = byCause.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        String dominantRootCause = dominant.getKey();

        // 3️⃣ Etkilenen callId’leri topla (uniq)
        Set<String> callIds = samples.stream()
                .map(FailureSample::callId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        // 4️⃣ Ortalama confidence hesapla
        double avgConfidence = samples.stream()
                .mapToDouble(FailureSample::confidence)
                .average()
                .orElse(0.0);

        return new FailureBurst(
                start,
                end,
                dominantRootCause,
                samples.size(),
                callIds,
                avgConfidence
        );
    }
}
