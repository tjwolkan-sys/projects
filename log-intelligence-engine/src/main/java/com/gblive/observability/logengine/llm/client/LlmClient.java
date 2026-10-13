package com.gblive.observability.logengine.llm.client;

import reactor.core.publisher.Mono;

public interface LlmClient {

    Mono<LlmResponse> analyze(String prompt);

    default LlmProviderType type() {
        return LlmProviderType.UNKNOWN;
    }
}
