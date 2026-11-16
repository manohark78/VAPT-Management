package com.company.vapt.enrichment.repository;

import com.company.vapt.enrichment.entity.EnrichmentRun;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EnrichmentRunRepository extends JpaRepository<EnrichmentRun, UUID> {
    Page<EnrichmentRun> findByImportBatchIdOrderByStartedAtDesc(UUID batchId, Pageable pageable);
    boolean existsByImportBatchIdAndStatus(UUID batchId, String status);
    java.util.Optional<EnrichmentRun> findFirstByImportBatchIdAndStatusOrderByStartedAtDesc(UUID batchId, String status);
}
