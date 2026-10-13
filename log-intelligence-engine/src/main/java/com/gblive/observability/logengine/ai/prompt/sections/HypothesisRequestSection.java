package com.gblive.observability.logengine.ai.prompt.sections;

import com.gblive.observability.logengine.ai.prompt.PromptContext;
import com.gblive.observability.logengine.ai.prompt.PromptSection;
import org.springframework.stereotype.Component;

@Component
public class HypothesisRequestSection implements PromptSection {

    @Override
    public String render(PromptContext ctx) {
        return """
        ANALYSIS REQUEST
        ----------------
        Based on the incident data:

        1. Identify the most probable root cause
        2. Explain causal chain step-by-step
        3. Suggest immediate mitigation
        4. Suggest long-term preventive actions
        """;
    }
}
