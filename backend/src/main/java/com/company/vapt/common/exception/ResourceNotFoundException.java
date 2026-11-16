package com.company.vapt.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends VaptException {

    public ResourceNotFoundException(String resource, String identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found with identifier: %s", resource, identifier), HttpStatus.NOT_FOUND);
    }
}
