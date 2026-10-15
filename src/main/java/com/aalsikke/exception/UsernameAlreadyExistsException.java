package com.aalsikke.exception;

/** KAN-258: taken username → 409 on the "username" field (same pattern as EmailAlreadyExistsException). */
public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String message) {
        super(message);
    }
}