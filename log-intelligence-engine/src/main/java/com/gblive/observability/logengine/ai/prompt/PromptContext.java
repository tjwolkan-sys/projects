package com.gblive.observability.logengine.ai.prompt;

import java.util.Map;

public class PromptContext {

    private final Map<String, Object> attributes;

    public PromptContext(Map<String, Object> attributes) {
        this.attributes = Map.copyOf(attributes);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }
}
