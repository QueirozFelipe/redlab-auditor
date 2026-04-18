package com.redlab.auditor.domain.exception;

public class TooManyRequestsException extends ApiResponseException {
  public TooManyRequestsException(String message, String apiResponseMessage) {
    super(message, apiResponseMessage);
  }
}
