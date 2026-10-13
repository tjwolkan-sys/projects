package com.gblive.observability.logengine.ai.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gblive.observability.logengine.ai.model.AiInsight;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AiInsightParser {

    private final ObjectMapper objectMapper;

    public AiInsightParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<AiInsight> parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(rawContent);

            JsonNode causeNode = root.path("probableCause");
            JsonNode explanationNode = root.path("explanation");
            JsonNode confidenceNode = root.path("confidence");

            if (causeNode.isMissingNode() || explanationNode.isMissingNode() || confidenceNode.isMissingNode()) {
                return Optional.empty();
            }

            String probableCause = causeNode.asText();
            String explanation = explanationNode.asText();
            double confidence = confidenceNode.asDouble(-1.0);

            if (probableCause.isBlank() || explanation.isBlank()) {
                return Optional.empty();
            }

            if (confidence < 0.0 || confidence > 1.0) {
                return Optional.empty();
            }

            // evidence optional
            List<String> evidence = new ArrayList<>();
            JsonNode evidenceNode = root.path("evidence");
            if (evidenceNode.isArray()) {
                evidenceNode.forEach(e -> evidence.add(e.asText()));
            }

            // Şimdilik AiInsight sadece cause / explanation / confidence tutuyor.
            // İleride evidence alanı eklenebilir.
            return Optional.of(new AiInsight(
                    probableCause,
                    explanation,
                    confidence
            ));

        } catch (JsonProcessingException e) {
            // LLM JSON değilse
            return Optional.empty();
        }
    }
}
