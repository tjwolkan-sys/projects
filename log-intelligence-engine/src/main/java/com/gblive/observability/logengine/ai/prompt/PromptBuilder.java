package com.gblive.observability.logengine.ai.prompt;

public interface PromptBuilder<T> {
    String build(T input);
}
