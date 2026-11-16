package com.company.vapt.ingestion.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Configuration properties for local report source file storage.
 */
@Component
@ConfigurationProperties(prefix = "vapt.storage")
public class StorageProperties {

    /**
     * Root directory on local disk where source files are stored outside the web root.
     */
    private String rootDir = "./data/storage/uploads";

    /**
     * Allowed file extensions for report uploads (case-insensitive).
     */
    private Set<String> allowedExtensions = Set.of(".csv", ".xlsx", ".xls");

    /**
     * Maximum allowed file size in bytes (default 50 MB).
     */
    private long maxFileSizeBytes = 50 * 1024 * 1024;

    public String getRootDir() {
        return rootDir;
    }

    public void setRootDir(String rootDir) {
        this.rootDir = rootDir;
    }

    public Path getRootPath() {
        return Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
}
