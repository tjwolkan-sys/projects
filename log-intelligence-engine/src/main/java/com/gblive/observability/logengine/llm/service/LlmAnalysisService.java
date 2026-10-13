package com.gblive.observability.logengine.llm.service;

import com.gblive.observability.logengine.ai.model.AiInsight;
import com.gblive.observability.logengine.ai.model.TimelineSummary;
import com.gblive.observability.logengine.ai.parser.AiInsightParser;
import com.gblive.observability.logengine.ai.port.LlmAnalysisPort;
import com.gblive.observability.logengine.ai.prompt.PromptBuilder;
import com.gblive.observability.logengine.llm.client.LlmClient;
import com.gblive.observability.logengine.llm.client.LlmResponse;
import com.gblive.observability.logengine.llm.config.LlmProperties;
import com.gblive.observability.logengine.llm.routing.LlmClientRouter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class LlmAnalysisService implements LlmAnalysisPort {

    private final LlmClientRouter router;
    private final PromptBuilder promptBuilder;
    private final LlmProperties properties;
    private final AiInsightParser parser;

    public LlmAnalysisService(
            LlmClientRouter router,
            PromptBuilder promptBuilder,
            LlmProperties properties,
            AiInsightParser parser
    ) {
        this.router = router;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public Mono<AiInsight> analyzeTimeline(TimelineSummary summary) {

        // ✅ LLM feature flag
        if (!properties.enabled()) {
            return Mono.empty();
        }

        String prompt = promptBuilder.build(summary);

        LlmClient client = router.resolve(properties.provider());

        return client.analyze(prompt)
                .flatMap(this::mapToAiInsight)
                .onErrorResume(ex -> Mono.empty()); // ✅ AI asla sistemi düşürmez
    }

    private Mono<AiInsight> mapToAiInsight(LlmResponse response) {

        return Mono.justOrEmpty(
                parser.parse(response.rawResponse())
        );
    }
}
