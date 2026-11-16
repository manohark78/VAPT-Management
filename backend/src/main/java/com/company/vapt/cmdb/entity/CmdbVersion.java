package com.company.vapt.cmdb.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="cmdb_version")
public class CmdbVersion {
    @Id private UUID id;
    @Column(name="dataset_key", nullable=false, length=40) private String datasetKey;
    @Column(name="original_filename", nullable=false) private String originalFilename;
    @Column(name="storage_path", nullable=false, unique=true) private String storagePath;
    @Column(name="sha256_checksum", nullable=false, length=64) private String checksum;
    @Column(name="uploaded_by", nullable=false) private String uploadedBy;
    @Column(nullable=false, length=24) private String status;
    @Column(nullable=false) private boolean active;
    @Column(name="active_dataset_key", length=40) private String activeDatasetKey;
    @Column(name="total_rows", nullable=false) private long totalRows;
    @Column(name="processed_rows", nullable=false) private long processedRows;
    @Column(name="rejected_rows", nullable=false) private long rejectedRows;
    @Column(name="sheet_index") private Integer sheetIndex;
    @Column(name="sheet_name") private String sheetName;
    @Column(name="header_row") private Integer headerRow;
    @Column(name="mapping_json", columnDefinition="CLOB") private String mappingJson;
    @Column(name="inspection_json", columnDefinition="CLOB") private String inspectionJson;
    @Column(name="profile_json", columnDefinition="CLOB") private String profileJson;
    @Column(name="diagnostics_json", columnDefinition="CLOB") private String diagnosticsJson;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="completed_at") private Instant completedAt;
    protected CmdbVersion() { }
    public CmdbVersion(UUID id, String datasetKey, String filename, String storagePath, String checksum, String user) {
        this.id=id; this.datasetKey=datasetKey; this.originalFilename=filename; this.storagePath=storagePath;
        this.checksum=checksum; this.uploadedBy=user; this.status="INSPECTING"; this.createdAt=Instant.now();
    }
    public void complete(int sheet, String name, int header, String mapping, String profile, long total, long processed) {
        sheetIndex=sheet; sheetName=name; headerRow=header; mappingJson=mapping; profileJson=profile;
        totalRows=total; processedRows=processed; rejectedRows=Math.max(0,total-processed); status="READY"; completedAt=Instant.now();
    }
    public void fail(String diagnostics) { status="FAILED"; diagnosticsJson=diagnostics; completedAt=Instant.now(); }
    public void setInspection(String inspection) { inspectionJson=inspection; }
    public void markProcessing() { if(!"INSPECTING".equals(status)) throw new IllegalStateException("Upload is not awaiting confirmation"); status="PROCESSING"; }
    public void setCounts(long total,long processed,long rejected) { totalRows=total; processedRows=processed; rejectedRows=rejected; }
    public String getInspectionJson(){return inspectionJson;}
    public void activate() { if (!"READY".equals(status)) throw new IllegalStateException("Only ready versions can be activated"); active=true; activeDatasetKey=datasetKey; }
    public void deactivate() { active=false; activeDatasetKey=null; }
    public UUID getId(){return id;} public String getDatasetKey(){return datasetKey;} public String getOriginalFilename(){return originalFilename;}
    public String getStoragePath(){return storagePath;} public String getChecksum(){return checksum;} public String getUploadedBy(){return uploadedBy;}
    public String getStatus(){return status;} public boolean isActive(){return active;} public long getTotalRows(){return totalRows;}
    public long getProcessedRows(){return processedRows;} public long getRejectedRows(){return rejectedRows;} public Integer getSheetIndex(){return sheetIndex;}
    public String getSheetName(){return sheetName;} public Integer getHeaderRow(){return headerRow;} public String getMappingJson(){return mappingJson;}
    public String getProfileJson(){return profileJson;} public String getDiagnosticsJson(){return diagnosticsJson;} public Instant getCreatedAt(){return createdAt;}
    public Instant getCompletedAt(){return completedAt;}
}
