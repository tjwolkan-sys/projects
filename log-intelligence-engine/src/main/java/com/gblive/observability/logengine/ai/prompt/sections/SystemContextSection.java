package com.gblive.observability.logengine.ai.prompt.sections;

import com.gblive.observability.logengine.ai.prompt.PromptContext;
import com.gblive.observability.logengine.ai.prompt.PromptSection;
import org.springframework.stereotype.Component;

@Component
public class SystemContextSection implements PromptSection {

    @Override
    public String render(PromptContext ctx) {
        return """
        You are an SRE-grade Root Cause Analysis assistant specialized in:
        - Distributed call center systems
        - Java / Spring / Redis / Asterisk architectures
        - High availability telephony platforms

        Respond with technical precision.
        """;
    }
}
