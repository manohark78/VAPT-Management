-- Permit general report rows with duplicate or missing UIDs to be retained for identity review.
-- V4's per-batch source_identity uniqueness constraint prevented safe row-level reconciliation.
ALTER TABLE report_import_record DROP CONSTRAINT uq_report_import_record_identity;
CREATE INDEX idx_report_import_batch_identity
    ON report_import_record(import_batch_id, source_identity);

CREATE TABLE finding_reconciliation_run (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL,
    report_section_id VARCHAR(50) NOT NULL,
    status VARCHAR(24) NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    new_findings BIGINT NOT NULL DEFAULT 0,
    existing_open BIGINT NOT NULL DEFAULT 0,
    unmatched_identity BIGINT NOT NULL DEFAULT 0,
    diagnostic_code VARCHAR(80),
    diagnostic_message VARCHAR(500),
    CONSTRAINT fk_finding_reconciliation_batch FOREIGN KEY (import_batch_id)
        REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_reconciliation_section FOREIGN KEY (report_section_id)
        REFERENCES report_section(id),
    CONSTRAINT chk_finding_reconciliation_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_finding_reconciliation_batch
    ON finding_reconciliation_run(import_batch_id, started_at);

CREATE TABLE finding (
    id UUID PRIMARY KEY,
    report_section_id VARCHAR(50) NOT NULL,
    source_uid VARCHAR(1000) NOT NULL,
    lifecycle_state VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_finding_section FOREIGN KEY (report_section_id)
        REFERENCES report_section(id),
    CONSTRAINT chk_finding_lifecycle_state CHECK (lifecycle_state IN ('OPEN'))
);
-- Deliberately non-unique until committed source data is profiled and supports that invariant.
CREATE INDEX idx_finding_section_uid
    ON finding(report_section_id, source_uid);

CREATE TABLE finding_observation (
    id UUID PRIMARY KEY,
    finding_id UUID,
    import_batch_id UUID NOT NULL,
    report_section_id VARCHAR(50) NOT NULL,
    report_import_record_id UUID NOT NULL,
    classification VARCHAR(32) NOT NULL,
    identity_issue VARCHAR(48),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_finding_observation_finding FOREIGN KEY (finding_id)
        REFERENCES finding(id) ON DELETE SET NULL,
    CONSTRAINT fk_finding_observation_batch FOREIGN KEY (import_batch_id)
        REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_observation_section FOREIGN KEY (report_section_id)
        REFERENCES report_section(id),
    CONSTRAINT fk_finding_observation_source FOREIGN KEY (report_import_record_id)
        REFERENCES report_import_record(id) ON DELETE CASCADE,
    CONSTRAINT chk_finding_observation_classification
        CHECK (classification IN ('NEW', 'EXISTING_OPEN', 'UNMATCHED_IDENTITY')),
    CONSTRAINT chk_finding_observation_identity_issue
        CHECK (identity_issue IS NULL OR identity_issue IN
            ('MISSING_UID', 'DUPLICATE_UID', 'DUPLICATE_CANONICAL_IDENTITY', 'UNSUPPORTED_LIFECYCLE_STATE'))
);
CREATE INDEX idx_finding_observation_finding_time
    ON finding_observation(finding_id, observed_at);
CREATE INDEX idx_finding_observation_batch_row
    ON finding_observation(import_batch_id, report_import_record_id);

CREATE TABLE finding_event (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL,
    import_batch_id UUID NOT NULL,
    report_import_record_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_finding_event_finding FOREIGN KEY (finding_id)
        REFERENCES finding(id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_event_batch FOREIGN KEY (import_batch_id)
        REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_finding_event_source FOREIGN KEY (report_import_record_id)
        REFERENCES report_import_record(id) ON DELETE CASCADE,
    CONSTRAINT chk_finding_event_type CHECK (event_type IN ('NEW', 'EXISTING_OPEN'))
);
CREATE INDEX idx_finding_event_finding_time
    ON finding_event(finding_id, occurred_at);
