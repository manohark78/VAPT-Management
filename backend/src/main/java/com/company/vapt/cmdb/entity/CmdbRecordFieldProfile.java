package com.company.vapt.cmdb.entity;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="cmdb_record_field_profile",uniqueConstraints=@UniqueConstraint(name="uq_cmdb_field_profile_row",columnNames={"version_id","source_row_number","field_name"}))
public class CmdbRecordFieldProfile {
 @Id private UUID id;
 @Column(name="version_id",nullable=false) private UUID versionId;
 @Column(name="source_row_number",nullable=false) private int rowNumber;
 @Column(name="field_name",nullable=false,length=120) private String fieldName;
 @Column(name="value_hash",length=64) private String valueHash;
 @Column(nullable=false) private boolean blank;
 protected CmdbRecordFieldProfile(){}
 public CmdbRecordFieldProfile(UUID versionId,int row,String field,String hash,boolean blank){this.id=UUID.randomUUID();this.versionId=versionId;this.rowNumber=row;this.fieldName=field;this.valueHash=hash;this.blank=blank;}
}
