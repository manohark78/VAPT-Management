package com.company.vapt.ingestion.storage;

import com.company.vapt.ingestion.entity.SourceFile;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable metadata record representing a securely stored file on local disk.
 */
public record StoredFile(
        UUID fileId,
        String originalFilename,
        String storagePath,
        long fileSizeBytes,
        String contentType,
        String fileExtension,
        String sha256Checksum,
        String uploadedBy,
        Instant uploadedAt
) {
    /**
     * Converts this stored file metadata into a JPA entity for persistence.
     */
    public SourceFile toEntity() {
        return new SourceFile(
                fileId,
                originalFilename,
                storagePath,
                fileSizeBytes,
                contentType,
                fileExtension,
                sha256Checksum,
                uploadedBy
        );
    }
}
