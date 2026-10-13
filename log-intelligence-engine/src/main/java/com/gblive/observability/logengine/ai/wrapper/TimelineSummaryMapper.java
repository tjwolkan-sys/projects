package com.gblive.observability.logengine.ai.wrapper;

import com.gblive.observability.logengine.aggregation.CallTimeline;
import com.gblive.observability.logengine.ai.model.TimelineSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimelineSummaryMapper {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    public static TimelineSummary from(CallTimeline timeline) {
        return new TimelineSummary(
                timeline.callId(),
                "Powerdialer",
                "prod",
                timeline.events().stream()
                        .map(e -> new TimelineSummary.TimelineStep(
                                e.type().name(),
                                TIME_FMT.format(e.timestamp()
                                        .atZone(ZoneId.systemDefault()))
                        ))
                        .toList()
        );
    }
}
