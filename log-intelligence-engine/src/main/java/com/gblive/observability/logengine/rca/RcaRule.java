package com.gblive.observability.logengine.rca;

import com.gblive.observability.logengine.parsing.EventType;

import java.util.List;

public enum RcaRule {

    ASTERISK_CONNECTION_REFUSED(
            "ASTERISK_CONNECTION_REFUSED",
            "Asterisk server refused connection during call lifecycle",
            List.of(EventType.AST_CONNECTION_REFUSED),
            0.95
    ),

    ASTERISK_DISCONNECTED(
            "ASTERISK_DISCONNECTED",
            "Asterisk disconnected before call could be completed",
            List.of(EventType.AST_DISCONNECTED),
            0.90
    ),

    CALL_RESULT_MISSING(
            "CALL_RESULT_MISSING",
            "Call ended without a result code; retry logic could not be determined",
            List.of(EventType.CALL_RESULT_MISSING),
            0.85
    ),

    UNKNOWN(
            "UNKNOWN",
            "No clear root cause detected",
            List.of(),
            0.30
    );

    public final String code;
    public final String description;
    public final List<EventType> requiredEvents;
    public final double confidence;

    RcaRule(String code, String description,
            List<EventType> requiredEvents,
            double confidence) {
        this.code = code;
        this.description = description;
        this.requiredEvents = requiredEvents;
        this.confidence = confidence;
    }
}
