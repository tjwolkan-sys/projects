package com.gblive.observability.logengine.ai.prompt.sections;

import com.gblive.observability.logengine.ai.prompt.PromptContext;
import com.gblive.observability.logengine.ai.prompt.PromptSection;
import com.gblive.observability.logengine.failure.model.RcaEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TimelineSection implements PromptSection {

    @Override
    public String render(PromptContext ctx) {

        List<RcaEvent> events = ctx.get("events");

        if (events == null || events.isEmpty()) {
            return "";
        }

        String timeline = events.stream()
                .limit(50)
                .map(e -> String.format(
                        "%s | %s | %s | %s",
                        e.timestamp(),
                        e.host(),
                        e.type(),
                        e.message()
                ))
                .collect(Collectors.joining("\n"));

        return """
        INCIDENT TIMELINE
        ----------------
        """ + timeline;
    }
}
