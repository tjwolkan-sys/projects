package com.gblive.observability.logengine.burst;

public class FailureClassifier {

    private FailureClassifier() {}

    public static String classify(String message) {

        if (message == null || message.isBlank()) {
            return "UNKNOWN";
        }

        String msg = message.toLowerCase();

        if (msg.contains("asterisk")
                && msg.contains("connection refused")) {
            return "ASTERISK_CONNECTION_REFUSED";
        }

        if (msg.contains("asterisk")
                && (msg.contains("timeout") || msg.contains("trying to login"))) {
            return "ASTERISK_TIMEOUT";
        }

        if (msg.contains("redis")
                && msg.contains("timeout")) {
            return "REDIS_PUBSUB_TIMEOUT";
        }

        if (msg.contains("redis")
                && msg.contains("connection")) {
            return "REDIS_CONNECTION_LOST";
        }

        if (msg.contains("jdbc")
                && msg.contains("timeout")) {
            return "DATABASE_TIMEOUT";
        }

        return "UNKNOWN";
    }
}
