package com.gblive.observability.logengine.parsing;

import com.gblive.observability.logengine.model.RawLogEvent;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LogEventExtractor {

    // --- Regex Patterns ---
    private static final Pattern CALL_ID_PATTERN =
            Pattern.compile("id=(dl-[a-z0-9\\-]+)");

    private static final Pattern CAMPAIGN_PATTERN =
            Pattern.compile("campaign=Campaign \\[id=\\d+, name=([^\\]]+)]");

    public ExtractedEvent extract(RawLogEvent log) {

        String msg = log.message();

        EventType type = resolveEventType(msg);
        String severity = resolveSeverity(msg);
        String callId = extractCallId(msg);
        String campaign = extractCampaign(msg);

        return new ExtractedEvent(
                type,
                callId,
                campaign,
                log.hostname(),
                severity,
                log.timestamp(),
                confidence(type),
                msg
        );
    }

    // ------------------------

    private EventType resolveEventType(String msg) {

        if (msg.contains("Current state: CONNECTED")) {
            return EventType.AST_CONNECTED;
        }
        if (msg.contains("Current state: DISCONNECTED")) {
            return EventType.AST_DISCONNECTED;
        }
        if (msg.contains("Trying to login")) {
            return EventType.AST_LOGIN_ATTEMPT;
        }
        if (msg.contains("Connection refused")) {
            return EventType.AST_CONNECTION_REFUSED;
        }
        if (msg.contains("Got production number")) {
            return EventType.CALL_PRODUCTION_ASSIGNED;
        }
        if (msg.contains("Calls drained to CallConsumer")) {
            return EventType.CALL_DRAINED;
        }
        if (msg.contains("No result code found")) {
            return EventType.CALL_RESULT_MISSING;
        }
        if (msg.contains("Fetched calls")) {
            return EventType.CAMPAIGN_CALLS_FETCHED;
        }
        if (msg.contains("deprecated")) {
            return EventType.DEPRECATION_WARNING;
        }

        return EventType.UNKNOWN;
    }

    private String resolveSeverity(String msg) {
        if (msg.contains(" ERROR ")) return "ERROR";
        if (msg.contains(" WARN ")) return "WARN";
        return "INFO";
    }

    private String extractCallId(String msg) {
        Matcher matcher = CALL_ID_PATTERN.matcher(msg);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractCampaign(String msg) {
        Matcher matcher = CAMPAIGN_PATTERN.matcher(msg);
        return matcher.find() ? matcher.group(1) : null;
    }

    private double confidence(EventType type) {
        return switch (type) {
            case AST_CONNECTION_REFUSED -> 0.95;
            case AST_DISCONNECTED -> 0.90;
            case CALL_RESULT_MISSING -> 0.85;
            case UNKNOWN -> 0.30;
            default -> 0.80;
        };
    }
}
