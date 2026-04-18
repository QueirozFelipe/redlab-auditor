package com.redlab.auditor.domain.exception;

public class ResourceNotFoundException extends ApiResponseException {
    public ResourceNotFoundException(String message, String apiResponseMessage) {
        super(message, apiResponseMessage);
    }
}
