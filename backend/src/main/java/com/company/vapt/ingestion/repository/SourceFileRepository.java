package com.company.vapt.ingestion.repository;

import com.company.vapt.ingestion.entity.SourceFile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SourceFileRepository extends JpaRepository<SourceFile, UUID> {

    Optional<SourceFile> findBySha256Checksum(String sha256Checksum);

    Optional<SourceFile> findByStoragePath(String storagePath);
}
