package com.company.vapt.common.exception;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(VaptException.class)
    public ResponseEntity<ApiResponse<Void>> handleVaptException(VaptException ex) {
        String correlationId = getCorrelationId();
        log.warn("Business exception: code={} message={} correlationId={}", ex.getErrorCode(), ex.getMessage(), correlationId);
        ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage(), correlationId);
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error, correlationId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String correlationId = getCorrelationId();
        List<String> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
        }
        log.warn("Validation error on request: correlationId={} errorCount={}", correlationId, details.size());
        ApiError error = ApiError.of("VALIDATION_ERROR", "Input validation failed", correlationId, details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error, correlationId));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        String correlationId = getCorrelationId();
        log.warn("Access denied: correlationId={}", correlationId);
        ApiError error = ApiError.of("ACCESS_DENIED", "You do not have permission to perform this action", correlationId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(error, correlationId));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        String correlationId = getCorrelationId();
        log.warn("File upload size exceeded: correlationId={}", correlationId);
        ApiError error = ApiError.of("FILE_TOO_LARGE", "Uploaded file exceeds the maximum allowed size", correlationId);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(ApiResponse.error(error, correlationId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String correlationId = getCorrelationId();
        log.error("Unhandled internal exception: correlationId={}", correlationId, ex);
        ApiError error = ApiError.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Reference correlation ID: " + correlationId, correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error, correlationId));
    }

    private String getCorrelationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "unknown";
    }
}
