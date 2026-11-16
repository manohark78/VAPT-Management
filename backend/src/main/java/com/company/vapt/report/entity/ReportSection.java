package com.company.vapt.report.entity;

import com.company.vapt.report.domain.ReconciliationType;
import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity representing a canonical report section in the database.
 */
@Entity
@Table(name = "report_section")
public class ReportSection {

    @Id
    @Column(name = "id", length = 50, nullable = false)
    private String id;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_type", length = 50, nullable = false)
    private ReconciliationType reconciliationType;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReportSection() {
        // Required by JPA
    }

    public ReportSection(String id, String displayName, String description, ReconciliationType reconciliationType, boolean enabled) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.reconciliationType = reconciliationType;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ReconciliationType getReconciliationType() {
        return reconciliationType;
    }

    public void setReconciliationType(ReconciliationType reconciliationType) {
        this.reconciliationType = reconciliationType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
