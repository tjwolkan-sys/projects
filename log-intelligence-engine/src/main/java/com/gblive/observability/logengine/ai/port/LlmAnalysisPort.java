package com.gblive.observability.logengine.ai.port;

import com.gblive.observability.logengine.ai.model.AiInsight;
import com.gblive.observability.logengine.ai.model.TimelineSummary;
import reactor.core.publisher.Mono;

public interface LlmAnalysisPort {

    Mono<AiInsight> analyzeTimeline(TimelineSummary summary);
}
