package com.gblive.observability.logengine.ai.prompt.sections;

import com.gblive.observability.logengine.ai.prompt.PromptContext;
import com.gblive.observability.logengine.ai.prompt.PromptSection;
import org.springframework.stereotype.Component;

@Component
public class OutputFormatSection implements PromptSection {

    @Override
    public String render(PromptContext ctx) {
        return """
        RESPONSE FORMAT (STRICT JSON)
        -----------------------------
        {
          "rootCause": "...",
          "confidence": 0.0,
          "explanation": "...",
          "immediateActions": ["..."],
          "preventiveActions": ["..."]
        }

        Do NOT add extra fields.
        """;
    }
}
