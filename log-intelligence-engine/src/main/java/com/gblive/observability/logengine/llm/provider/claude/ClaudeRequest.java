package com.gblive.observability.logengine.llm.provider.claude;

import java.util.List;

public record ClaudeRequest(
        String model,
        int max_tokens,
        List<Message> messages
) {
    public ClaudeRequest(String model, String prompt) {
        this(model, 2048, List.of(new Message("user", prompt)));
    }

    public record Message(String role, String content) {}
}
