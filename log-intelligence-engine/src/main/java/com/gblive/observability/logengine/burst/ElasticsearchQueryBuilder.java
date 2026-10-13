package com.gblive.observability.logengine.burst;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

public class ElasticsearchQueryBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode build(
            Instant from,
            Instant to,
            List<String> callIds
    ) {

        ObjectNode root = MAPPER.createObjectNode();

        ObjectNode bool = root.putObject("query")
                .putObject("bool");

        var must = bool.putArray("must");

        // Time range
        must.addObject()
                .putObject("range")
                .putObject("@timestamp")
                .put("gte", from.toString())
                .put("lte", to.toString());

        // CallId filter
        if (callIds != null && !callIds.isEmpty()) {

            var terms = must.addObject()
                    .putObject("terms")
                    .putArray("message");

            callIds.forEach(terms::add);
        }

        root.put("size", 5000);

        return root;
    }
}
