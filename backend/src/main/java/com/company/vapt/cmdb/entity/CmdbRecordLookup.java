package com.company.vapt.cmdb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Search projection for one nonblank field value in an immutable CMDB version. */
@Entity
@Table(name = "cmdb_record_lookup")
public class CmdbRecordLookup {
    @Id private UUID id;
    @Column(name = "version_id", nullable = false) private UUID versionId;
    @Column(name = "record_id", nullable = false) private UUID recordId;
    @Column(name = "source_row_number", nullable = false) private int sourceRowNumber;
    @Column(name = "field_name", nullable = false, length = 120) private String fieldName;
    @Column(name = "value_hash", nullable = false, length = 64) private String valueHash;
    @Column(name = "normalized_value", nullable = false, columnDefinition = "CLOB") private String normalizedValue;

    protected CmdbRecordLookup() { }

    public CmdbRecordLookup(UUID versionId, UUID recordId, int sourceRowNumber,
                            String fieldName, String valueHash, String normalizedValue) {
        this.id = UUID.randomUUID();
        this.versionId = versionId;
        this.recordId = recordId;
        this.sourceRowNumber = sourceRowNumber;
        this.fieldName = fieldName;
        this.valueHash = valueHash;
        this.normalizedValue = normalizedValue;
    }

    public UUID getVersionId() { return versionId; }
    public UUID getRecordId() { return recordId; }
    public int getSourceRowNumber() { return sourceRowNumber; }
    public String getFieldName() { return fieldName; }
    public String getValueHash() { return valueHash; }
    public String getNormalizedValue() { return normalizedValue; }
}
