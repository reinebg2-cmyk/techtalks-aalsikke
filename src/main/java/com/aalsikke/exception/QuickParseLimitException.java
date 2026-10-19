package com.aalsikke.exception;

/** KAN-372: the user reached the hourly quick-add limit. */
public class QuickParseLimitException extends RuntimeException {

    public QuickParseLimitException() {
        super("Too many quick-adds. Please try again later or fill in the form manually.");
    }
}