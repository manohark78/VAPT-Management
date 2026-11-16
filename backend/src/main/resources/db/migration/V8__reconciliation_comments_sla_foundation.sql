-- Snapshot completeness is explicit and safe by default. V1-V7 remain immutable.
CREATE INDEX idx_audit_timestamp ON audit_event(timestamp DESC);
ALTER TABLE import_batch ADD COLUMN complete_snapshot BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE finding_reconciliation_run ADD COLUMN existing_closed BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finding_reconciliation_run ADD COLUMN reopened_findings BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finding_reconciliation_run ADD COLUMN returned_unresolved BIGINT NOT NULL DEFAULT 0;
ALTER TABLE finding_reconciliation_run ADD COLUMN missing_snapshot BIGINT NOT NULL DEFAULT 0;

ALTER TABLE finding DROP CONSTRAINT chk_finding_lifecycle_state;
ALTER TABLE finding ADD CONSTRAINT chk_finding_lifecycle_state CHECK (lifecycle_state IN ('OPEN', 'CLOSED'));
ALTER TABLE finding ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE finding ADD COLUMN closed_by_user_id VARCHAR(255);
ALTER TABLE finding ADD COLUMN closed_reason VARCHAR(500);

ALTER TABLE finding_observation ALTER COLUMN report_import_record_id DROP NOT NULL;
ALTER TABLE finding_observation DROP CONSTRAINT chk_finding_observation_classification;
ALTER TABLE finding_observation ADD CONSTRAINT chk_finding_observation_classification
    CHECK (classification IN ('NEW', 'EXISTING_OPEN', 'EXISTING_CLOSED', 'REOPENED',
        'RETURNED_UNRESOLVED', 'UNMATCHED_IDENTITY', 'MISSING_FROM_COMPLETE_SNAPSHOT'));
ALTER TABLE finding_observation ADD COLUMN previous_state_classification VARCHAR(32);
ALTER TABLE finding_observation ADD COLUMN source_values CLOB;
ALTER TABLE finding_observation ADD COLUMN risk_rating VARCHAR(120);
ALTER TABLE finding_observation ADD COLUMN risk_score VARCHAR(120);
ALTER TABLE finding_observation ADD COLUMN compliance_status VARCHAR(120);
ALTER TABLE finding_observation ADD COLUMN severity VARCHAR(120);
ALTER TABLE finding_observation ADD COLUMN finding_type VARCHAR(500);
ALTER TABLE finding_observation ADD COLUMN port VARCHAR(120);
ALTER TABLE finding_observation ADD COLUMN results CLOB;
ALTER TABLE finding_observation ADD COLUMN targets CLOB;
ALTER TABLE finding_observation ADD COLUMN display_name VARCHAR(2000);
ALTER TABLE finding_observation ADD COLUMN app_tag VARCHAR(500);
ALTER TABLE finding_observation ADD COLUMN ip_address VARCHAR(255);
ALTER TABLE finding_observation ADD COLUMN application_name VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN environment VARCHAR(500);
ALTER TABLE finding_observation ADD COLUMN owned_by VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN application_id VARCHAR(500);
ALTER TABLE finding_observation ADD COLUMN remediation_date DATE;
ALTER TABLE finding_observation ADD COLUMN sla_due_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE finding_observation ADD COLUMN headcount_email VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN cm4 VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN cm5 VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN cm6 VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN plan_application_owner_1 VARCHAR(1000);
ALTER TABLE finding_observation ADD COLUMN plan_application_owner_2 VARCHAR(1000);
CREATE INDEX idx_finding_observation_section_batch ON finding_observation(report_section_id, import_batch_id, classification);
CREATE INDEX idx_finding_observation_scope ON finding_observation(report_section_id, ip_address, application_name, app_tag);

ALTER TABLE finding_event ALTER COLUMN report_import_record_id DROP NOT NULL;
ALTER TABLE finding_event DROP CONSTRAINT chk_finding_event_type;
ALTER TABLE finding_event ADD CONSTRAINT chk_finding_event_type CHECK (event_type IN
    ('NEW', 'EXISTING_OPEN', 'EXISTING_CLOSED', 'REOPENED', 'RETURNED_UNRESOLVED',
     'MISSING_FROM_COMPLETE_SNAPSHOT', 'CLOSED', 'RCA_SUBMITTED', 'RCA_APPROVED', 'RCA_REJECTED'));
ALTER TABLE finding_event ADD COLUMN actor_user_id VARCHAR(255);
ALTER TABLE finding_event ADD COLUMN details VARCHAR(1000);

CREATE TABLE finding_comment (
    id UUID PRIMARY KEY,
    report_section_id VARCHAR(50) NOT NULL,
    finding_id UUID,
    ip_address VARCHAR(255) NOT NULL,
    application_name VARCHAR(1000) NOT NULL,
    app_tag VARCHAR(500) NOT NULL,
    comment_text CLOB NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_finding_comment_section FOREIGN KEY (report_section_id) REFERENCES report_section(id),
    CONSTRAINT fk_finding_comment_finding FOREIGN KEY (finding_id) REFERENCES finding(id)
);
CREATE INDEX idx_finding_comment_group ON finding_comment(report_section_id, ip_address, application_name, app_tag, created_at);
CREATE INDEX idx_finding_comment_finding ON finding_comment(finding_id, created_at);

CREATE TABLE finding_remediation_action (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    selected_date DATE,
    actor_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_finding_remediation_finding FOREIGN KEY (finding_id) REFERENCES finding(id),
    CONSTRAINT chk_finding_remediation_type CHECK (action_type IN
        ('FALSE_POSITIVE_RAISED', 'FALSE_POSITIVE_APPROVED', 'REMEDIATION_DATE', 'ALREADY_REMEDIATED')),
    CONSTRAINT chk_finding_remediation_date CHECK
        ((action_type = 'REMEDIATION_DATE' AND selected_date IS NOT NULL) OR
         (action_type <> 'REMEDIATION_DATE' AND selected_date IS NULL))
);
CREATE INDEX idx_finding_remediation_finding_time ON finding_remediation_action(finding_id, created_at);

CREATE TABLE finding_rca (
    id UUID PRIMARY KEY,
    finding_id UUID NOT NULL,
    reopen_event_id UUID NOT NULL,
    narrative CLOB NOT NULL,
    status VARCHAR(24) NOT NULL,
    submitted_by_user_id VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_by_user_id VARCHAR(255),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_note VARCHAR(1000),
    CONSTRAINT fk_finding_rca_finding FOREIGN KEY (finding_id) REFERENCES finding(id),
    CONSTRAINT fk_finding_rca_event FOREIGN KEY (reopen_event_id) REFERENCES finding_event(id),
    CONSTRAINT chk_finding_rca_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);
CREATE INDEX idx_finding_rca_finding_time ON finding_rca(finding_id, submitted_at);
