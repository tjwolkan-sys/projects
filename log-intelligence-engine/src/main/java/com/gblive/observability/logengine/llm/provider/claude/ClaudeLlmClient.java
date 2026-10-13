package com.gblive.observability.logengine.llm.provider.claude;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gblive.observability.logengine.llm.client.LlmClient;
import com.gblive.observability.logengine.llm.client.LlmProviderType;
import com.gblive.observability.logengine.llm.client.LlmResponse;
import com.gblive.observability.logengine.llm.config.LlmProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ClaudeLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient client;
    private final LlmProperties.ClaudeConfig props;

    public ClaudeLlmClient(WebClient.Builder builder, LlmProperties properties) {
        this.props = properties.claude();

        this.client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    @Override
    public Mono<LlmResponse> analyze(String prompt) {

        ClaudeRequest body = new ClaudeRequest(props.model(), prompt);

        return client.post()
                .uri("/v1/messages")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {

                    String text = json
                            .path("content")
                            .get(0)
                            .path("text")
                            .asText();

                    Map<String, Object> parsed =
                            MAPPER.convertValue(json, new TypeReference<>() {});

                    return new LlmResponse(
                            text,
                            0.85,
                            parsed
                    );
                });
    }

    @Override
    public LlmProviderType type() {
        return LlmProviderType.CLAUDE;
    }
}
