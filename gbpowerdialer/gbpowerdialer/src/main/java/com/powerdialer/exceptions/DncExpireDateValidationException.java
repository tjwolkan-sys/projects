package com.powerdialer.exceptions;

public class DncExpireDateValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public DncExpireDateValidationException() { super("Phone number expire date cannot be later than DNC list expire date."); }
}