/*
 * Copyright 2026 Felipe Queiroz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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