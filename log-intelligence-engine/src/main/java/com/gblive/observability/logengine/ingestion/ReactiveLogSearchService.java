package com.gblive.observability.logengine.ingestion;

import com.gblive.observability.logengine.aggregation.CallTimeline;
import com.gblive.observability.logengine.aggregation.CallTimelineAggregator;
import com.gblive.observability.logengine.model.RawLogEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.gblive.observability.logengine.parsing.ExtractedEvent;
import com.gblive.observability.logengine.parsing.LogEventExtractor;
import com.gblive.observability.logengine.rca.AiAssistedRcaService;
import com.gblive.observability.logengine.rca.RcaDecisionEngine;
import com.gblive.observability.logengine.rca.RcaResult;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Instant;

@Service
public class ReactiveLogSearchService {

    private final WebClient elasticsearchWebClient;
    private final LogEventExtractor logEventExtractor;
    private final CallTimelineAggregator callTimelineAggregator;
    private final RcaDecisionEngine rcaDecisionEngine;
    private final AiAssistedRcaService aiAssistedRcaService;

    public ReactiveLogSearchService(
            WebClient elasticsearchWebClient,
            LogEventExtractor logEventExtractor, CallTimelineAggregator callTimelineAggregator, RcaDecisionEngine rcaDecisionEngine, AiAssistedRcaService aiAssistedRcaService
    ) {
        this.elasticsearchWebClient = elasticsearchWebClient;
        this.logEventExtractor = logEventExtractor;
        this.callTimelineAggregator = callTimelineAggregator;
        this.rcaDecisionEngine = rcaDecisionEngine;
        this.aiAssistedRcaService = aiAssistedRcaService;
    }

    public Flux<ExtractedEvent> searchLogs(
            String index,
            String applicationName,
            String timeRange
    ) {

        String query = """
        {
          "size": 100,
          "query": {
            "bool": {
              "must": [
                { "match": { "application_name": "%s" }},
                { "range": { "@timestamp": { "gte": "%s" }}}
              ]
            }
          }
        }
        """.formatted(applicationName, timeRange);

        return elasticsearchWebClient.post()
                .uri("/" + index + "/_search")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMapMany(root ->
                        Flux.fromIterable(root.path("hits").path("hits"))
                )
                .map(this::toRawLogEvent)
                .map(logEventExtractor::extract);
    }

    private RawLogEvent toRawLogEvent(JsonNode hit) {
        JsonNode source = hit.path("_source");

        return new RawLogEvent(
                hit.path("_index").asText(),
                Instant.parse(source.path("@timestamp").asText()),
                source.path("application_name").asText(),
                source.path("hostname").asText(),
                source.path("message").asText()
        );
    }

    public Flux<CallTimeline> searchAndAggregateByCall(
            String index,
            String applicationName,
            String since
    ) {
        return searchLogs(index, applicationName, since)
                .transform(callTimelineAggregator::aggregate);
    }

    public Flux<RcaResult> analyzeRca(
            String index,
            String applicationName,
            String since
    ) {
        return searchAndAggregateByCall(index, applicationName, since)
                .map(rcaDecisionEngine::analyze);
    }

    public Flux<RcaResult> analyzeRcaWithAi(
            String index,
            String applicationName,
            String since
    ) {
        return searchAndAggregateByCall(index, applicationName, since)
                .flatMap(aiAssistedRcaService::analyze);
    }

}
