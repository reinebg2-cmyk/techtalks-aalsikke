package com.aalsikke.dto;

/**
 * Small, predictable error shape the frontend already expects
 * (see register.js's mockRegisterUser rejection: { field, message }).
 * "field" is optional — omitted for errors that aren't tied to one input.
 */
public class ApiError {

    private String field;
    private String message;

    public ApiError() {}

    public ApiError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public static ApiError of(String message) {
        return new ApiError(null, message);
    }

    public static ApiError of(String field, String message) {
        return new ApiError(field, message);
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
