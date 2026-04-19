package com.redlab.auditor.infrastructure.client;

import com.redlab.auditor.domain.exception.ResourceNotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiResponseExceptionHandlerTest {

    private ApiResponseExceptionHandler handler;
    private Response mockResponse;

    @BeforeEach
    void setUp() {
        handler = new ApiResponseExceptionHandler();
        mockResponse = mock(Response.class);
    }

    @Test
    void shouldHandleStatusesGreaterOrEqualThan400() {
        assertTrue(handler.handles(400, null));
        assertTrue(handler.handles(500, null));
        assertFalse(handler.handles(200, null));
        assertFalse(handler.handles(302, null));
    }

    @ParameterizedTest
    @CsvSource({
            "401, UnauthorizedException",
            "403, UnauthorizedException",
            "404, ResourceNotFoundException",
            "419, TooManyRequestsException",
            "500, ApiResponseException",
            "410, ApiResponseException"
    })
    void shouldMapCorrectExceptionBasedOnStatus(int status, String expectedExceptionClass) {
        String fakeBody = "{\"error\": \"detail\"}";
        when(mockResponse.getStatus()).thenReturn(status);
        when(mockResponse.readEntity(String.class)).thenReturn(fakeBody);

        RuntimeException exception = handler.toThrowable(mockResponse);

        assertEquals(expectedExceptionClass, exception.getClass().getSimpleName());
        assertTrue(exception.getMessage().contains(fakeBody));
        assertTrue(exception.getMessage().contains(String.valueOf(status)));
    }

    @Test
    void shouldIncludeApiResponseBodyInMessage() {
        int status = 404;
        String specificError = "Project X not found";
        when(mockResponse.getStatus()).thenReturn(status);
        when(mockResponse.readEntity(String.class)).thenReturn(specificError);

        RuntimeException exception = handler.toThrowable(mockResponse);

        assertAll(
                () -> assertTrue(exception instanceof ResourceNotFoundException),
                () -> assertTrue(exception.getMessage().contains(specificError)),
                () -> assertTrue(exception.getMessage().contains("Resource not found"))
        );
    }
}