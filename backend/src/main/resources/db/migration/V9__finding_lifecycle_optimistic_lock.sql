-- Prevent concurrent administrative close/review actions from duplicating lifecycle transitions.
ALTER TABLE finding ADD COLUMN lifecycle_version BIGINT NOT NULL DEFAULT 0;
