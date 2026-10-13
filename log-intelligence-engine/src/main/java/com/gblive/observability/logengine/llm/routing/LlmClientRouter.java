package com.gblive.observability.logengine.llm.routing;

import com.gblive.observability.logengine.llm.client.LlmClient;
import com.gblive.observability.logengine.llm.client.LlmProviderType;
import com.gblive.observability.logengine.llm.client.LlmResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class LlmClientRouter {

    private final Map<LlmProviderType, LlmClient> clients = new EnumMap<>(LlmProviderType.class);

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(8);

    public LlmClientRouter(List<LlmClient> clientList) {
        clientList.forEach(c -> clients.put(c.type(), c));
    }

    /**
     * Resolve a client with automatic fallback
     */
    public LlmClient resolve(LlmProviderType preferred) {
        return prompt -> executeWithFallback(prompt, fallbackChain(preferred));
    }

    /**
     * Core fallback engine
     */
    private Mono<LlmResponse>
    executeWithFallback(String prompt, List<LlmProviderType> order) {

        Mono<LlmResponse> chain = Mono.empty();

        for (LlmProviderType type : order) {
            LlmClient client = clients.get(type);
            if (client == null) continue;

            chain = chain.switchIfEmpty(
                    client.analyze(prompt)
                            .timeout(PROVIDER_TIMEOUT)
                            .onErrorResume(ex -> Mono.empty())
            );
        }

        return chain;
    }

    /**
     * Defines fallback priority
     */
    private List<LlmProviderType> fallbackChain(LlmProviderType preferred) {

        return switch (preferred) {
            case OPENAI -> List.of(
                    LlmProviderType.OPENAI,
                    LlmProviderType.CLAUDE,
                    LlmProviderType.GEMINI
            );
            case CLAUDE -> List.of(
                    LlmProviderType.CLAUDE,
                    LlmProviderType.OPENAI,
                    LlmProviderType.GEMINI
            );
            case GEMINI -> List.of(
                    LlmProviderType.GEMINI,
                    LlmProviderType.OPENAI,
                    LlmProviderType.CLAUDE
            );
            case UNKNOWN -> List.of(
                    LlmProviderType.UNKNOWN
            );
        };
    }
}
