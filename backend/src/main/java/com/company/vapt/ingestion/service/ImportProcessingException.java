package com.company.vapt.ingestion.service;

/** Safe, user-visible import diagnostic; never contains report cell contents. */
public class ImportProcessingException extends RuntimeException {
    private final String stage;
    private final String diagnosticCode;

    public ImportProcessingException(String stage, String diagnosticCode, String message) {
        super(message);
        this.stage = stage;
        this.diagnosticCode = diagnosticCode;
    }

    public ImportProcessingException(String stage, String diagnosticCode, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.diagnosticCode = diagnosticCode;
    }

    public String getStage() { return stage; }
    public String getDiagnosticCode() { return diagnosticCode; }
}
