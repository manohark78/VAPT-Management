CREATE TABLE cmdb_dataset (
    dataset_key VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    active_version_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cmdb_version (
    id UUID PRIMARY KEY,
    dataset_key VARCHAR(40) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL UNIQUE,
    sha256_checksum VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    active_dataset_key VARCHAR(40),
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    rejected_rows BIGINT NOT NULL DEFAULT 0,
    sheet_index INT,
    sheet_name VARCHAR(255),
    header_row INT,
    mapping_json CLOB,
    inspection_json CLOB,
    profile_json CLOB,
    diagnostics_json CLOB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_cmdb_version_dataset FOREIGN KEY (dataset_key) REFERENCES cmdb_dataset(dataset_key),
    CONSTRAINT uq_cmdb_version_active_dataset UNIQUE (active_dataset_key),
    CONSTRAINT chk_cmdb_version_status CHECK (status IN ('INSPECTING','PROCESSING','READY','FAILED')),
    CONSTRAINT chk_cmdb_version_active CHECK ((active = FALSE AND active_dataset_key IS NULL) OR
        (active = TRUE AND active_dataset_key = dataset_key AND status = 'READY'))
);

ALTER TABLE cmdb_version ADD CONSTRAINT uq_cmdb_version_id_dataset UNIQUE (id, dataset_key);
ALTER TABLE cmdb_dataset ADD CONSTRAINT fk_cmdb_dataset_active_version
    FOREIGN KEY (active_version_id, dataset_key) REFERENCES cmdb_version(id, dataset_key);

CREATE INDEX idx_cmdb_version_history ON cmdb_version(dataset_key, created_at DESC);

CREATE TABLE cmdb_record (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    record_key VARCHAR(1000) NOT NULL,
    values_json CLOB NOT NULL,
    CONSTRAINT fk_cmdb_record_version FOREIGN KEY (version_id) REFERENCES cmdb_version(id) ON DELETE CASCADE,
    CONSTRAINT uq_cmdb_record_row UNIQUE(version_id, source_row_number)
);

CREATE TABLE cmdb_record_field_profile (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL,
    source_row_number INT NOT NULL,
    field_name VARCHAR(120) NOT NULL,
    value_hash VARCHAR(64),
    blank BOOLEAN NOT NULL,
    CONSTRAINT fk_cmdb_field_profile_version FOREIGN KEY (version_id) REFERENCES cmdb_version(id) ON DELETE CASCADE,
    CONSTRAINT uq_cmdb_field_profile_row UNIQUE(version_id, source_row_number, field_name)
);

INSERT INTO cmdb_dataset(dataset_key, display_name, created_at) VALUES
('HEADCOUNT', 'TuHeadCount EE & CWR', CURRENT_TIMESTAMP),
('JAS', 'JAS Inventory', CURRENT_TIMESTAMP),
('APP_INVENTORY', 'ApplicationInventory', CURRENT_TIMESTAMP);
