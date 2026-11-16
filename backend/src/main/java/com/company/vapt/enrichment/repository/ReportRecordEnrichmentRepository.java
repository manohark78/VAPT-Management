package com.company.vapt.enrichment.repository;

import com.company.vapt.enrichment.entity.ReportRecordEnrichment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface ReportRecordEnrichmentRepository extends JpaRepository<ReportRecordEnrichment, UUID> {
    Page<ReportRecordEnrichment> findByEnrichmentRunIdOrderBySourceRowNumberAsc(UUID runId, Pageable pageable);
    Page<ReportRecordEnrichment> findByEnrichmentRunIdAndTargetMatchStatusOrderBySourceRowNumberAsc(
            UUID runId, String status, Pageable pageable);
    List<ReportRecordEnrichment> findByEnrichmentRunIdAndSourceRecordIdIn(UUID runId, Collection<UUID> sourceRecordIds);
}
