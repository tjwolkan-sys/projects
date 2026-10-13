package com.gblive.observability.logengine.ai.prompt;

import com.gblive.observability.logengine.burst.BurstContext;
import com.gblive.observability.logengine.burst.FailureSample;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

@Component
public class BurstPromptBuilder {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ISO_INSTANT;

    public String build(BurstContext context) {

        var burst = context.burst();

        String header = """
                You are an expert SRE for a large call-center platform.
                You are analyzing a FAILURE BURST (many calls failing in a short time window).
                                
                You MUST respond in valid JSON ONLY, with the following schema:
                {
                  "probableCause": string,
                  "explanation": string,
                  "confidence": number between 0 and 1,
                  "evidence": string[]
                }
                Do not include markdown. Do not include any text outside the JSON.
                """;

        String burstInfo = """
                Burst window:
                  start: %s
                  end:   %s
                  rootCause (rule-based): %s
                  failureCount: %d
                """.formatted(
                TS_FORMAT.format(burst.windowStart()),
                TS_FORMAT.format(burst.windowEnd()),
                burst.rootCause(),
                burst.failureCount()
        );

        // Örnek log / failure özetleri – ilk N failure’dan
        String sampleLines = context.samples().stream()
                .limit(10)
                .map(this::formatSample)
                .collect(Collectors.joining("\n"));

        String samplesBlock = """
                Failure samples:
                %s
                """.formatted(sampleLines.isBlank() ? "(no samples)" : sampleLines);

        return header + "\n\n" + burstInfo + "\n\n" + samplesBlock;
    }

    private String formatSample(FailureSample s) {
        return "- time=%s | callId=%s | rootCause=%s | confidence=%.2f"
                .formatted(
                        TS_FORMAT.format(s.timestamp()),
                        s.callId(),
                        s.rootCause(),
                        s.confidence()
                );
    }
}
