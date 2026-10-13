package com.gblive.observability.logengine.llm.provider.openai;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenAiResponse(
        JsonNode choices
) {}
