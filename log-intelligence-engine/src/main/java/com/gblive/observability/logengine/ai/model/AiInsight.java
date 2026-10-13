package com.gblive.observability.logengine.ai.model;

public record AiInsight(
        String probableCause,
        String explanation,
        double confidence
) {}
