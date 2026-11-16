package com.company.vapt.ingestion.storage;

import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.report.domain.ReportSectionId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Production implementation of FileStorageService storing source files on local disk
 * outside the application web root with streaming SHA-256 calculation and immutability guarantees.
 */
@Service
public class LocalDiskFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskFileStorageService.class);
    private static final int BUFFER_SIZE = 8192; // 8 KB bounded buffer

    // File magic byte signatures
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04}; // .xlsx
    private static final byte[] OLE2_MAGIC = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}; // .xls

    private final StorageProperties properties;

    public LocalDiskFileStorageService(StorageProperties properties) {
        this.properties = properties;
        initStorageDirectory();
    }

    private void initStorageDirectory() {
        try {
            Path root = properties.getRootPath();
            if (!Files.exists(root)) {
                Files.createDirectories(root);
                log.info("Initialized secure storage root directory at: {}", root);
            }
        } catch (IOException e) {
            throw new StorageException("Failed to initialize storage root directory", e);
        }
    }

    @Override
    public StoredFile store(
            ReportSectionId sectionId,
            InputStream inputStream,
            String originalFilename,
            String declaredContentType,
            String uploadedBy
    ) {
        if (sectionId == null) {
            throw new ValidationException("Report section ID must not be null");
        }
        if (inputStream == null) {
            throw new ValidationException("Input stream must not be null");
        }
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new ValidationException("Original filename must not be empty");
        }

        // 1. Validate original filename and detect path traversal attempts
        validateFilenameSafety(originalFilename);
        String extension = extractAndValidateExtension(originalFilename);

        // 2. Generate UUID storage identifier and relative path
        UUID fileId = UUID.randomUUID();
        String relativeStoragePath = sectionId.name() + "/" + fileId + ".dat";
        Path targetPath = resolvePath(relativeStoragePath);

        // 3. Ensure section subdirectory exists safely
        try {
            Files.createDirectories(targetPath.getParent());
        } catch (IOException e) {
            throw new StorageException("Failed to create section directory for " + sectionId.name(), e);
        }

        // 4. Stream data to disk using bounded 8KB buffer with concurrent SHA-256 hashing
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algorithm not available in JVM", e);
        }

        long totalBytesWritten = 0;
        byte[] firstChunk = null;

        try (DigestInputStream dis = new DigestInputStream(inputStream, digest);
             OutputStream out = Files.newOutputStream(targetPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = dis.read(buffer)) != -1) {
                if (firstChunk == null && bytesRead > 0) {
                    firstChunk = new byte[bytesRead];
                    System.arraycopy(buffer, 0, firstChunk, 0, bytesRead);
                }

                totalBytesWritten += bytesRead;

                if (totalBytesWritten > properties.getMaxFileSizeBytes()) {
                    throw new ValidationException(String.format("File size exceeds maximum permitted limit (%d MB)", properties.getMaxFileSizeBytes() / (1024 * 1024)));
                }

                out.write(buffer, 0, bytesRead);
            }

            out.flush();
        } catch (Exception ex) {
            // Delete partial file on failure
            deleteQuietly(targetPath);
            if (ex instanceof ValidationException ve) {
                throw ve;
            }
            throw new StorageException("Failed to write uploaded file to disk", ex);
        }

        // 5. Validate non-empty file
        if (totalBytesWritten == 0) {
            deleteQuietly(targetPath);
            throw new ValidationException("Uploaded file is empty");
        }

        // 6. Validate content signature against claimed extension
        if (!validateContentSignature(extension, firstChunk)) {
            deleteQuietly(targetPath);
            throw new ValidationException("File content does not match expected format for extension: " + extension);
        }

        String sha256Hex = HexFormat.of().formatHex(digest.digest());
        String contentType = (declaredContentType != null && !declaredContentType.trim().isEmpty())
                ? declaredContentType.trim()
                : determineDefaultContentType(extension);

        log.info("Stored source file: section={}, fileId={}, size={} bytes, sha256={}",
                sectionId.name(), fileId, totalBytesWritten, sha256Hex);

        return new StoredFile(
                fileId,
                originalFilename.trim(),
                relativeStoragePath,
                totalBytesWritten,
                contentType,
                extension,
                sha256Hex,
                (uploadedBy != null && !uploadedBy.trim().isEmpty()) ? uploadedBy.trim() : "SYSTEM",
                Instant.now()
        );
    }

    @Override
    public InputStream openStream(String storagePath) {
        Path path = resolvePath(storagePath);
        if (!Files.exists(path)) {
            throw new StorageException("Stored file not found: " + storagePath);
        }
        try {
            return Files.newInputStream(path, StandardOpenOption.READ);
        } catch (IOException e) {
            throw new StorageException("Failed to open stream for stored file: " + storagePath, e);
        }
    }

    @Override
    public boolean exists(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            return false;
        }
        try {
            Path path = resolvePath(storagePath);
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void delete(String storagePath) {
        Path path = resolvePath(storagePath);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new StorageException("Failed to delete stored file: " + storagePath, e);
        }
    }

    @Override
    public Path resolvePath(String storagePath) {
        if (storagePath == null || storagePath.trim().isEmpty()) {
            throw new ValidationException("Storage path must not be empty");
        }

        Path root = properties.getRootPath();
        Path resolved = root.resolve(storagePath).normalize();

        // Enforce path traversal protection: resolved path must be within storage root
        if (!resolved.startsWith(root)) {
            throw new ValidationException("Path traversal attempt detected in storage path: " + storagePath);
        }

        return resolved;
    }

    private void validateFilenameSafety(String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new ValidationException("Path traversal or illegal path characters detected in filename: " + filename);
        }
    }

    private String extractAndValidateExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            throw new ValidationException("File must have a valid extension (.csv, .xlsx, or .xls)");
        }

        String extension = filename.substring(dotIndex).toLowerCase();
        if (!properties.getAllowedExtensions().contains(extension)) {
            throw new ValidationException("Unsupported file extension: " + extension + ". Allowed: " + properties.getAllowedExtensions());
        }

        return extension;
    }

    private boolean validateContentSignature(String extension, byte[] header) {
        if (header == null || header.length == 0) {
            return false;
        }

        return switch (extension) {
            case ".xlsx" -> matchesMagic(header, ZIP_MAGIC);
            case ".xls" -> matchesMagic(header, OLE2_MAGIC);
            case ".csv" -> isLikelyTextContent(header);
            default -> false;
        };
    }

    private boolean matchesMagic(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isLikelyTextContent(byte[] data) {
        // Text files must not contain binary null bytes (0x00) in the header
        for (byte b : data) {
            if (b == 0x00) {
                return false;
            }
        }
        return true;
    }

    private String determineDefaultContentType(String extension) {
        return switch (extension) {
            case ".csv" -> "text/csv";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".xls" -> "application/vnd.ms-excel";
            default -> "application/octet-stream";
        };
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best effort cleanup
            }
        }
    }
}
