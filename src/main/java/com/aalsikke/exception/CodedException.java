package com.aalsikke.exception;

public class CodedException extends IllegalArgumentException {

    private final String code;

    public CodedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}