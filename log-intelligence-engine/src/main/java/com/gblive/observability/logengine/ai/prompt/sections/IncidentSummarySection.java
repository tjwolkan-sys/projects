package com.gblive.observability.logengine.ai.prompt.sections;

import com.gblive.observability.logengine.ai.prompt.PromptContext;
import com.gblive.observability.logengine.ai.prompt.PromptSection;
import com.gblive.observability.logengine.failure.model.FailureIncident;
import org.springframework.stereotype.Component;

@Component
public class IncidentSummarySection implements PromptSection {

    @Override
    public String render(PromptContext ctx) {

        FailureIncident i = ctx.get("incident");

        return String.format("""
        INCIDENT SUMMARY
        ----------------
        Type: %s
        Severity: %s
        Time Window: %s → %s
        Failure Count: %d
        Affected Hosts: %s
        """,
                i.type(),
                i.severity(),
                i.windowStart(),
                i.windowEnd(),
                i.count(),
                String.join(", ", i.hosts())
        );
    }
}
