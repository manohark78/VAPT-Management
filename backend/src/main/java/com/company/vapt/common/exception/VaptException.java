package com.company.vapt.common.exception;

import org.springframework.http.HttpStatus;

public class VaptException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public VaptException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public VaptException(String errorCode, String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
