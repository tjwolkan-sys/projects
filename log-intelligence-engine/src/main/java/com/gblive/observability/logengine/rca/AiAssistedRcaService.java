package com.gblive.observability.logengine.rca;

import com.gblive.observability.logengine.aggregation.CallTimeline;
import com.gblive.observability.logengine.ai.model.AiInsight;
import com.gblive.observability.logengine.ai.model.TimelineSummary;
import com.gblive.observability.logengine.ai.port.LlmAnalysisPort;
import com.gblive.observability.logengine.ai.wrapper.TimelineSummaryMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AiAssistedRcaService {

    private final RcaDecisionEngine rcaDecisionEngine;
    private final LlmAnalysisPort llmAnalysisPort;

    public AiAssistedRcaService(
            RcaDecisionEngine rcaDecisionEngine,
            LlmAnalysisPort llmAnalysisPort
    ) {
        this.rcaDecisionEngine = rcaDecisionEngine;
        this.llmAnalysisPort = llmAnalysisPort;
    }

    public Mono<RcaResult> analyze(CallTimeline timeline) {

        RcaResult baseResult = rcaDecisionEngine.analyze(timeline);

        if (baseResult.confidence() >= 0.85) {
            return Mono.just(baseResult);
        }

        TimelineSummary summary = TimelineSummaryMapper.from(timeline);

        return llmAnalysisPort.analyzeTimeline(summary)
                .map(ai -> merge(baseResult, ai))
                .onErrorReturn(baseResult)
                .defaultIfEmpty(baseResult); // ✅ JSON invalid / AI empty → baseResult
    }

    private RcaResult merge(RcaResult base, AiInsight ai) {
        return new RcaResult(
                base.callId(),
                ai.probableCause() != null && !ai.probableCause().isBlank()
                        ? ai.probableCause()
                        : base.rootCause(),
                base.description() + " | AI: " + ai.explanation(),
                Math.max(base.confidence(), ai.confidence()),
                base.evidence()
        );
    }
}
