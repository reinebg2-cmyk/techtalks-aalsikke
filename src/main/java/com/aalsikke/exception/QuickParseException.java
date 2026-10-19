package com.aalsikke.exception;

/** KAN-372: the quick-add text could not be turned into a task (AI unsure, bad reply, or AI unavailable). */
public class QuickParseException extends RuntimeException {

    public QuickParseException(String message) {
        super(message);
    }

    public QuickParseException(String message, Throwable cause) {
        super(message, cause);
    }
}