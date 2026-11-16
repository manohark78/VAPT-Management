-- Persist recognised source fields for successfully validated report rows.
-- The immutable source upload remains the complete provenance artifact.
CREATE TABLE report_import_record (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    source_identity VARCHAR(1000) NOT NULL,
    source_values CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_report_import_record_batch FOREIGN KEY (import_batch_id)
        REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT uq_report_import_record_row UNIQUE (import_batch_id, source_row_number),
    CONSTRAINT uq_report_import_record_identity UNIQUE (import_batch_id, source_identity)
);

