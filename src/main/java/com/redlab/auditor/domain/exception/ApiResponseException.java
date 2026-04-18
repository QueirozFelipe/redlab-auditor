package com.redlab.auditor.domain.exception;

public class ApiResponseException extends RuntimeException {
    public ApiResponseException(String message, String apiResponseMessage) {
        super(message + " API response: " + apiResponseMessage);
    }
}
