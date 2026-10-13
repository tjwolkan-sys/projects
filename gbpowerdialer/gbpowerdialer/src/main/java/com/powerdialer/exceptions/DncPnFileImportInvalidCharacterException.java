package com.powerdialer.exceptions;

public class DncPnFileImportInvalidCharacterException extends RuntimeException {
    public DncPnFileImportInvalidCharacterException() {
        super("File with DNC phone numbers to import contains non-numeric phone numbers.");
    }
}
