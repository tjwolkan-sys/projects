package com.gblive.observability.logengine.config;

import com.gblive.observability.logengine.llm.client.LlmProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(

        boolean enabled,

        LlmProviderType provider,

        OpenAi openai,
        Claude claude,
        Gemini gemini

) {

    public record OpenAi(
            String apiKey,
            String model,
            long timeoutMs
    ) {}

    public record Claude(
            String apiKey,
            String model,
            long timeoutMs
    ) {}

    public record Gemini(
            String apiKey,
            String model,
            long timeoutMs
    ) {}
}
