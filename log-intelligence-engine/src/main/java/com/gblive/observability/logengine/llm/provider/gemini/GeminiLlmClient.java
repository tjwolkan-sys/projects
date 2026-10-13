package com.gblive.observability.logengine.llm.provider.gemini;

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
public class GeminiLlmClient implements LlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebClient client;
    private final LlmProperties.GeminiConfig props;

    public GeminiLlmClient(WebClient.Builder builder, LlmProperties properties) {
        this.props = properties.gemini();

        this.client = builder
                .baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Mono<LlmResponse> analyze(String prompt) {

        GeminiRequest body = new GeminiRequest(prompt);

        return client.post()
                .uri(uri -> uri
                        .path("/v1/models/{model}:generateContent")
                        .queryParam("key", props.apiKey())
                        .build(props.model()))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {

                    String text = json
                            .path("candidates").get(0)
                            .path("content")
                            .path("parts").get(0)
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
        return LlmProviderType.GEMINI;
    }
}
