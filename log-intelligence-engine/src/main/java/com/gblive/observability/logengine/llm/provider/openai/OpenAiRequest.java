package com.gblive.observability.logengine.llm.provider.openai;

import java.util.List;

public record OpenAiRequest(
        String model,
        List<Message> messages,
        double temperature
) {
    public record Message(String role, String content) {}
}
