package com.gblive.observability.logengine.ai.prompt;

import com.gblive.observability.logengine.failure.model.FailureIncident;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class IncidentPromptBuilder
        implements PromptBuilder<FailureIncident> {

    private final List<PromptSection> sections;

    public IncidentPromptBuilder(List<PromptSection> sections) {
        this.sections = sections;
    }

    @Override
    public String build(FailureIncident incident) {

        PromptContext context = new PromptContext(
                Map.of(
                        "incident", incident,
                        "severity", incident.severity().name(),
                        "hosts", incident.hosts(),
                        "durationSeconds",
                        Duration.between(
                                incident.windowStart(),
                                incident.windowEnd()
                        ).getSeconds()
                )
        );

        return sections.stream()
                .map(section -> section.render(context))
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining("\n\n"));
    }
}
