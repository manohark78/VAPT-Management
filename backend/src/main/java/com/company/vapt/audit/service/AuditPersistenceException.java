package com.company.vapt.audit.service;

/** Raised when a required audit event cannot be persisted. */
public class AuditPersistenceException extends RuntimeException {
    public AuditPersistenceException() {
        super("Required audit event could not be persisted");
    }
}
