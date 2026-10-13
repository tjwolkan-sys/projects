package com.powerdialer.util;

public class BadStatus {

    private final boolean exception;
    private final String localizedMessage;

    public BadStatus(String localizedMessage, boolean exception) {
        this.exception = exception;
        this.localizedMessage = localizedMessage;
    }

    public boolean isException() {
        return exception;
    }

    public String getLocalizedMessage() {
        return localizedMessage;
    }
}

