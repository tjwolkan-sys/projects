package com.powerdialer.exceptions;

public class InvalidApiTokenException extends RuntimeException {

    public InvalidApiTokenException(String message) {
        super(message);
    }
}
