package com.gblive.observability.logengine.burst;

import com.fasterxml.jackson.databind.JsonNode;
import com.gblive.observability.logengine.model.RawLogEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ElasticsearchFailureEventQueryService
        implements FailureEventQueryService {

    private final WebClient elasticsearchClient;

    public ElasticsearchFailureEventQueryService(
            WebClient elasticsearchClient
    ) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public List<RawLogEvent> query(
            Instant windowStart,
            Instant windowEnd,
            List<String> callIds
    ) {

        JsonNode query = ElasticsearchQueryBuilder.build(
                windowStart,
                windowEnd,
                callIds
        );

        JsonNode response = elasticsearchClient.post()
                .uri("/_search")
                .bodyValue(query)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<RawLogEvent> results = new ArrayList<>();

        if (response == null) {
            return results;
        }

        for (JsonNode hit : response.path("hits").path("hits")) {

            JsonNode src = hit.path("_source");

            results.add(new RawLogEvent(
                    hit.path("_index").asText(),
                    Instant.parse(src.path("@timestamp").asText()),
                    src.path("application_name").asText(),
                    src.path("hostname").asText(),
                    src.path("message").asText()
            ));
        }

        return results;
    }
}
