package com.company.vapt.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final String code;
    private final String message;
    private final Instant timestamp;
    private final String correlationId;
    private final List<String> details;

    public ApiError(String code, String message, String correlationId, List<String> details) {
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now();
        this.correlationId = correlationId;
        this.details = details;
    }

    public static ApiError of(String code, String message, String correlationId) {
        return new ApiError(code, message, correlationId, null);
    }

    public static ApiError of(String code, String message, String correlationId, List<String> details) {
        return new ApiError(code, message, correlationId, details);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public List<String> getDetails() {
        return details;
    }
}
