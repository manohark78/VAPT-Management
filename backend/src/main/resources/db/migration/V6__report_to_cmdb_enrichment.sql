-- Indexed, version-specific lookup projection over immutable CMDB records.
CREATE TABLE cmdb_record_lookup (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    record_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    value_hash VARCHAR(64) NOT NULL,
    normalized_value CLOB NOT NULL,
    CONSTRAINT fk_cmdb_lookup_version FOREIGN KEY (version_id)
        REFERENCES cmdb_version(id) ON DELETE CASCADE,
    CONSTRAINT fk_cmdb_lookup_record FOREIGN KEY (record_id)
        REFERENCES cmdb_record(id) ON DELETE CASCADE
);
CREATE INDEX idx_cmdb_lookup_value
    ON cmdb_record_lookup(version_id, field_name, value_hash);

CREATE TABLE cmdb_lookup_index_state (
    version_id UUID PRIMARY KEY,
    indexed_record_count BIGINT NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_cmdb_lookup_state_version FOREIGN KEY (version_id)
        REFERENCES cmdb_version(id) ON DELETE CASCADE
);

CREATE TABLE enrichment_run (
    id UUID PRIMARY KEY,
    import_batch_id UUID NOT NULL,
    report_section_id VARCHAR(50) NOT NULL,
    jas_version_id UUID,
    headcount_version_id UUID,
    app_inventory_version_id UUID,
    status VARCHAR(24) NOT NULL,
    mapping_method VARCHAR(80) NOT NULL,
    actor_user_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    matched_rows BIGINT NOT NULL DEFAULT 0,
    unmatched_rows BIGINT NOT NULL DEFAULT 0,
    ambiguous_rows BIGINT NOT NULL DEFAULT 0,
    diagnostic_code VARCHAR(80),
    diagnostic_message VARCHAR(500),
    CONSTRAINT fk_enrichment_run_batch FOREIGN KEY (import_batch_id)
        REFERENCES import_batch(id) ON DELETE CASCADE,
    CONSTRAINT fk_enrichment_run_section FOREIGN KEY (report_section_id)
        REFERENCES report_section(id),
    CONSTRAINT fk_enrichment_run_jas FOREIGN KEY (jas_version_id)
        REFERENCES cmdb_version(id),
    CONSTRAINT fk_enrichment_run_headcount FOREIGN KEY (headcount_version_id)
        REFERENCES cmdb_version(id),
    CONSTRAINT fk_enrichment_run_app_inventory FOREIGN KEY (app_inventory_version_id)
        REFERENCES cmdb_version(id),
    CONSTRAINT chk_enrichment_run_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);
CREATE INDEX idx_enrichment_run_batch_started
    ON enrichment_run(import_batch_id, started_at DESC);

CREATE TABLE report_record_enrichment (
    id UUID PRIMARY KEY,
    enrichment_run_id UUID NOT NULL,
    report_import_record_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    target_ordinal INT NOT NULL DEFAULT 0,
    original_target CLOB,
    target_type VARCHAR(24) NOT NULL,
    target_match_status VARCHAR(24) NOT NULL,
    match_reason VARCHAR(80),
    match_method VARCHAR(80),
    application_display VARCHAR(1000),
    application_name_status VARCHAR(40),
    owner_resolution_status VARCHAR(24) NOT NULL,
    application_resolution_status VARCHAR(24) NOT NULL,
    enriched_values_json CLOB NOT NULL,
    candidate_details_json CLOB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_report_enrichment_run FOREIGN KEY (enrichment_run_id)
        REFERENCES enrichment_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_enrichment_source FOREIGN KEY (report_import_record_id)
        REFERENCES report_import_record(id) ON DELETE CASCADE,
    CONSTRAINT uq_report_enrichment_target UNIQUE(enrichment_run_id, report_import_record_id, target_ordinal)
);
CREATE INDEX idx_report_enrichment_run_row
    ON report_record_enrichment(enrichment_run_id, source_row_number);
CREATE INDEX idx_report_enrichment_run_status
    ON report_record_enrichment(enrichment_run_id, target_match_status, source_row_number);
