package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingReconciliationRun;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FindingReconciliationRunRepository extends JpaRepository<FindingReconciliationRun, UUID> {
    boolean existsByImportBatchId(UUID batchId);
    Optional<FindingReconciliationRun> findFirstByImportBatchIdOrderByStartedAtDesc(UUID batchId);
    List<FindingReconciliationRun> findByReportSectionIdOrderByStartedAtDesc(String sectionId);
}
