package com.gblive.observability.logengine.burst;

import com.gblive.observability.logengine.ai.model.AiInsight;
import com.gblive.observability.logengine.ai.parser.AiInsightParser;
import com.gblive.observability.logengine.ai.prompt.BurstPromptBuilder;
import com.gblive.observability.logengine.config.AiProperties;
import com.gblive.observability.logengine.llm.client.LlmClient;
import com.gblive.observability.logengine.llm.client.LlmResponse;
import com.gblive.observability.logengine.llm.routing.LlmClientRouter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiAssistedBurstRcaService {

    private final BurstRcaEngine burstRcaEngine;
    private final LlmClientRouter llmRouter;
    private final BurstPromptBuilder promptBuilder;
    private final AiProperties aiProperties;
    private final AiInsightParser insightParser;

    public AiAssistedBurstRcaService(
            BurstRcaEngine burstRcaEngine,
            LlmClientRouter llmRouter,
            BurstPromptBuilder promptBuilder,
            AiProperties aiProperties,
            AiInsightParser insightParser
    ) {
        this.burstRcaEngine = burstRcaEngine;
        this.llmRouter = llmRouter;
        this.promptBuilder = promptBuilder;
        this.aiProperties = aiProperties;
        this.insightParser = insightParser;
    }

    public Mono<BurstRcaResult> analyze(BurstContext context) {

        BurstRcaResult base = burstRcaEngine.analyze(context);

        // Rule-based zaten yüksek güvenliyse AI'yi hiç çağırma
        if (base.confidence() >= 0.85 || !aiProperties.enabled()) {
            return Mono.just(base);
        }

        String prompt = promptBuilder.build(context);
        LlmClient client = llmRouter.resolve(aiProperties.provider());

        return client.analyze(prompt)
                .flatMap(this::toAiInsightMono)
                .map(ai -> merge(base, ai))
                .onErrorReturn(base)       // LLM patlarsa → rule-based
                .defaultIfEmpty(base);     // JSON invalid vs. → rule-based
    }

    private Mono<AiInsight> toAiInsightMono(LlmResponse response) {
        return Mono.justOrEmpty(
                insightParser.parse(response.rawResponse())
        );
    }

    private BurstRcaResult merge(BurstRcaResult base, AiInsight ai) {

        String finalCause =
                ai.probableCause() != null && !ai.probableCause().isBlank()
                        ? ai.probableCause()
                        : base.rootCause();

        String finalDescription = base.description()
                + " | AI: "
                + ai.explanation();

        double finalConfidence = Math.max(base.confidence(), ai.confidence());

        return new BurstRcaResult(
                base.windowStart(),
                base.windowEnd(),
                finalCause,
                finalDescription,
                finalConfidence,
                base.affectedCallIds()
        );
    }
}
