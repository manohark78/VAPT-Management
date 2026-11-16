package com.company.vapt.cmdb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "cmdb_dataset")
public class CmdbDataset {
    @Id @Column(name="dataset_key", length=40) private String datasetKey;
    @Column(name="display_name", nullable=false, length=120) private String displayName;
    @Column(name="active_version_id") private UUID activeVersionId;
    protected CmdbDataset() { }
    public String getDatasetKey() { return datasetKey; }
    public String getDisplayName() { return displayName; }
    public UUID getActiveVersionId() { return activeVersionId; }
    public void setActiveVersionId(UUID id) { this.activeVersionId = id; }
}
