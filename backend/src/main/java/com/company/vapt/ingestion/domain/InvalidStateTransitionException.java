package com.company.vapt.ingestion.domain;

import com.company.vapt.common.exception.VaptException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when an illegal state transition is attempted on an import batch.
 */
public class InvalidStateTransitionException extends VaptException {

    public InvalidStateTransitionException(ImportStatus current, ImportStatus target) {
        super(
                "INVALID_STATE_TRANSITION",
                String.format("Invalid state transition from [%s] to [%s]", current, target),
                HttpStatus.CONFLICT
        );
    }

    public InvalidStateTransitionException(String message) {
        super("INVALID_STATE_TRANSITION", message, HttpStatus.CONFLICT);
    }
}
