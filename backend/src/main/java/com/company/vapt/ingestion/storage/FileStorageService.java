package com.company.vapt.ingestion.storage;

import com.company.vapt.report.domain.ReportSectionId;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Service contract for secure, immutable report source file storage outside the web root.
 */
public interface FileStorageService {

    /**
     * Streams uploaded content to disk under an isolated section directory using a UUID filename,
     * calculating its SHA-256 checksum concurrently and validating content signatures.
     *
     * @param sectionId canonical report section
     * @param inputStream stream containing uploaded data
     * @param originalFilename client-reported filename (stored as metadata only)
     * @param declaredContentType client-reported MIME type
     * @param uploadedBy identity of authenticated uploader
     * @return immutable metadata record of the stored file
     */
    StoredFile store(
            ReportSectionId sectionId,
            InputStream inputStream,
            String originalFilename,
            String declaredContentType,
            String uploadedBy
    );

    /**
     * Opens an input stream for reading a stored file.
     *
     * @param storagePath relative storage path
     * @return readable InputStream
     */
    InputStream openStream(String storagePath);

    /**
     * Checks if a stored file exists on disk.
     */
    boolean exists(String storagePath);

    /**
     * Deletes a stored file from disk (e.g., during compensation/cleanup).
     */
    void delete(String storagePath);

    /**
     * Resolves and verifies that a relative storage path is safely contained within the storage root.
     */
    Path resolvePath(String storagePath);
}
