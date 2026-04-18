package com.redlab.auditor.domain.exception;

public class UnauthorizedException extends ApiResponseException {
    public UnauthorizedException(String message, String apiResponseMessage) {
        super(message, apiResponseMessage); }
}