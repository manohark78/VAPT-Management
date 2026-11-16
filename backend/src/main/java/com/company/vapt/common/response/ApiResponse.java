package com.company.vapt.common.response;

import com.company.vapt.common.exception.ApiError;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ApiError error;
    private final Instant timestamp;
    private final String correlationId;

    private ApiResponse(boolean success, T data, ApiError error, String correlationId) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = Instant.now();
        this.correlationId = correlationId;
    }

    public static <T> ApiResponse<T> ok(T data, String correlationId) {
        return new ApiResponse<>(true, data, null, correlationId);
    }

    public static <T> ApiResponse<T> error(ApiError error, String correlationId) {
        return new ApiResponse<>(false, null, error, correlationId);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public ApiError getError() {
        return error;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
