package com.company.vapt.ingestion.repository;

import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.entity.ReportSection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    @EntityGraph(attributePaths = {"sourceFile", "reportSection"})
    Optional<ImportBatch> findByReportSectionIdAndActiveTrue(String reportSectionId);

    @EntityGraph(attributePaths = {"sourceFile", "reportSection"})
    Page<ImportBatch> findByReportSectionIdOrderByCreatedAtDesc(String reportSectionId, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceFile", "reportSection"})
    Page<ImportBatch> findByReportSectionIdAndStatusOrderByCreatedAtDesc(
            String reportSectionId, com.company.vapt.ingestion.domain.ImportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"sourceFile", "reportSection"})
    @Query("select b from ImportBatch b where b.id = :id")
    Optional<ImportBatch> findDtoById(@Param("id") UUID id);

    boolean existsByIdAndReportSectionId(UUID id, String reportSectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ImportBatch b where b.id = :id")
    Optional<ImportBatch> lockById(@Param("id") UUID id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ImportBatch b SET b.active = false, b.activeSectionId = null WHERE b.reportSection.id = :sectionId AND b.active = true")
    int deactivateActiveBatchForSection(@Param("sectionId") String sectionId);
}
