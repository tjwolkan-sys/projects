package com.gblive.observability.logengine.llm.config;

import com.gblive.observability.logengine.llm.client.LlmProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
        boolean enabled,
        LlmProviderType provider,
        List<LlmProviderType> fallback,
        OpenAiConfig openai,
        ClaudeConfig claude,
        GeminiConfig gemini
) {

    public record OpenAiConfig(String apiKey, String model, String baseUrl, long timeout) {}
    public record ClaudeConfig(String apiKey, String model, String baseUrl, long timeout) {}
    public record GeminiConfig(String apiKey, String model, String baseUrl, long timeout) {}
}

