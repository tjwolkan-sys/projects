package com.gblive.observability.logengine.llm.client;

import java.util.Map;

public record LlmResponse(
        String rawResponse,
        double confidence,
        Map<String, Object> parsedJson
) {}
