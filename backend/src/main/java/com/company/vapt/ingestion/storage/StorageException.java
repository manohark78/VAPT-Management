package com.company.vapt.ingestion.storage;

import com.company.vapt.common.exception.VaptException;
import org.springframework.http.HttpStatus;

/**
 * Exception thrown when file storage operations encounter filesystem or I/O failures.
 */
public class StorageException extends VaptException {

    public StorageException(String message) {
        super("STORAGE_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public StorageException(String message, Throwable cause) {
        super("STORAGE_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
