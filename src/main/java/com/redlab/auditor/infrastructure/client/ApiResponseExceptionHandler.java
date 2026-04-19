package com.redlab.auditor.infrastructure.client;

import com.redlab.auditor.domain.exception.ApiResponseException;
import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import com.redlab.auditor.domain.exception.TooManyRequestsException;
import com.redlab.auditor.domain.exception.UnauthorizedException;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import jakarta.ws.rs.core.Response;

public class ApiResponseExceptionHandler implements ResponseExceptionMapper<RuntimeException> {

    @Override
    public RuntimeException toThrowable(Response response) {
        int status = response.getStatus();
        String errorBody = response.readEntity(String.class);

        return switch (status) {
            case 401, 403 -> new UnauthorizedException("Error code " + status + ". Access denied by API. Verify your token.", errorBody);
            case 404 -> new ResourceNotFoundException("Error code " + status + ". Resource not found on the remote server.", errorBody);
            case 419 -> new TooManyRequestsException("Error code " + status + ". API could not handle that many requests. Try a lower rate limit.", errorBody);
            default -> new ApiResponseException("Error code " + status + ". ", errorBody);
        };
    }

    @Override
    public boolean handles(int status, jakarta.ws.rs.core.MultivaluedMap<String, Object> headers) {
        return status >= 400;
    }
}