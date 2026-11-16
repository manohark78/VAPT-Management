package com.company.vapt.ingestion.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing metadata for an immutable uploaded source file.
 * The physical file contents are kept strictly on local disk outside the web root.
 */
@Entity
@Table(name = "source_file", indexes = {
        @Index(name = "idx_source_file_checksum", columnList = "sha256_checksum"),
        @Index(name = "idx_source_file_uploaded_at", columnList = "uploaded_at")
})
public class SourceFile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", length = 500, nullable = false, unique = true)
    private String storagePath;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    @Column(name = "file_extension", length = 20, nullable = false)
    private String fileExtension;

    @Column(name = "sha256_checksum", length = 64, nullable = false)
    private String sha256Checksum;

    @Column(name = "uploaded_by", length = 255, nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SourceFile() {
        // Required by JPA
    }

    public SourceFile(
            UUID id,
            String originalFilename,
            String storagePath,
            long fileSizeBytes,
            String contentType,
            String fileExtension,
            String sha256Checksum,
            String uploadedBy
    ) {
        this.id = id != null ? id : UUID.randomUUID();
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.fileSizeBytes = fileSizeBytes;
        this.contentType = contentType;
        this.fileExtension = fileExtension;
        this.sha256Checksum = sha256Checksum;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
