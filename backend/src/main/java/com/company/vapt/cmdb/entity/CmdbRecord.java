package com.company.vapt.cmdb.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="cmdb_record", uniqueConstraints=@UniqueConstraint(name="uq_cmdb_record_row", columnNames={"version_id","source_row_number"}))
public class CmdbRecord {
    @Id private UUID id;
    @Column(name="version_id", nullable=false) private UUID versionId;
    @Column(name="source_row_number", nullable=false) private int sourceRowNumber;
    @Column(name="record_key", nullable=false, length=1000) private String recordKey;
    @Column(name="values_json", nullable=false, columnDefinition="CLOB") private String valuesJson;
    protected CmdbRecord() { }
    public CmdbRecord(UUID versionId, int row, String key, String values) { this.id=UUID.randomUUID(); this.versionId=versionId; this.sourceRowNumber=row; this.recordKey=key; this.valuesJson=values; }
    public UUID getId(){return id;} public UUID getVersionId(){return versionId;} public String getRecordKey(){return recordKey;} public String getValuesJson(){return valuesJson;} public int getSourceRowNumber(){return sourceRowNumber;}
}
