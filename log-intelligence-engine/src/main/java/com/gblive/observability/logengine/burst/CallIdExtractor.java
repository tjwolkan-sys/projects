package com.gblive.observability.logengine.burst;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CallIdExtractor {

    private static final List<Pattern> PATTERNS = List.of(

        // id=dl-xxxx-xxxx-xxxx-xxxx
        Pattern.compile("id=(dl-[a-f0-9\\-]{36})"),

        // Call [... id=dl-xxxx ...]
        Pattern.compile("id=(dl-[a-f0-9\\-]{36})"),

        // generic UUID fallback
        Pattern.compile("(dl-[a-f0-9\\-]{36})")
    );

    private CallIdExtractor() {}

    public static Optional<String> extract(String message) {

        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return Optional.ofNullable(matcher.group(1));
            }
        }

        return Optional.empty();
    }
}
