package com.company.vapt.common.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends VaptException {

    public ValidationException(String message) {
        super("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
    }
}
