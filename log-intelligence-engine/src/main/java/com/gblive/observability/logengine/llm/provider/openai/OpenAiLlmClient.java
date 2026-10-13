package com.gblive.observability.logengine.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.gblive.observability.logengine.config.AiProperties;
import com.gblive.observability.logengine.llm.client.LlmClient;
import com.gblive.observability.logengine.llm.client.LlmProviderType;
import com.gblive.observability.logengine.llm.client.LlmResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiLlmClient implements LlmClient {

    private static final String OPENAI_ENDPOINT = "/v1/chat/completions";

    private final WebClient webClient;
    private final AiProperties.OpenAi config;

    public OpenAiLlmClient(
            WebClient.Builder builder,
            AiProperties properties
    ) {
        this.config = properties.openai();

        this.webClient = builder
                .baseUrl("https://api.openai.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + config.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<LlmResponse> analyze(String prompt) {

        Map<String, Object> body = Map.of(
                "model", config.model(),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", "You are an expert call-center platform SRE."
                        ),
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                ),
                "temperature", 0.2
        );

        return webClient.post()
                .uri(OPENAI_ENDPOINT)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .mapNotNull(this::toResponse);
    }

    private LlmResponse toResponse(JsonNode json) {
        if (json.path("choices").isEmpty()) {
            return null;
        }

        String content = json
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText();

        if (content.isBlank()) {
            return null;
        }

        return new LlmResponse(content, 0.85, Map.of());
    }

    @Override
    public LlmProviderType type() {
        return LlmProviderType.OPENAI;
    }
}
