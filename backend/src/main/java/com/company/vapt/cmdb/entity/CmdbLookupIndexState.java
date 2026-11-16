package com.company.vapt.cmdb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cmdb_lookup_index_state")
public class CmdbLookupIndexState {
    @Id @Column(name = "version_id", nullable = false) private UUID versionId;
    @Column(name = "indexed_record_count", nullable = false) private long indexedRecordCount;
    @Column(name = "completed_at", nullable = false) private Instant completedAt;

    protected CmdbLookupIndexState() { }

    public CmdbLookupIndexState(UUID versionId, long indexedRecordCount) {
        this.versionId = versionId;
        this.indexedRecordCount = indexedRecordCount;
        this.completedAt = Instant.now();
    }

    public UUID getVersionId() { return versionId; }
    public long getIndexedRecordCount() { return indexedRecordCount; }
    public Instant getCompletedAt() { return completedAt; }
}
