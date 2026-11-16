package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import com.company.vapt.report.entity.ReportSection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FindingRepository extends JpaRepository<Finding, UUID> {
    List<Finding> findByReportSectionIdAndSourceUidIn(String sectionId, Collection<String> sourceUids);
    List<Finding> findByIdIn(Collection<UUID> ids);
    long countByReportSectionId(String sectionId);

    @Query(value = "select f from Finding f where f.reportSection.id=:sectionId and f.lifecycleState='OPEN' and not exists (select r.id from com.company.vapt.ingestion.entity.ReportImportRecord r where r.importBatch.id=:batchId and r.sourceIdentity=f.sourceUid) and not exists (select other.id from Finding other where other.reportSection.id=f.reportSection.id and other.sourceUid=f.sourceUid and other.id<>f.id)",
            countQuery = "select count(f) from Finding f where f.reportSection.id=:sectionId and f.lifecycleState='OPEN' and not exists (select r.id from com.company.vapt.ingestion.entity.ReportImportRecord r where r.importBatch.id=:batchId and r.sourceIdentity=f.sourceUid) and not exists (select other.id from Finding other where other.reportSection.id=f.reportSection.id and other.sourceUid=f.sourceUid and other.id<>f.id)")
    Page<Finding> findOpenMissingFromBatch(@Param("sectionId") String sectionId, @Param("batchId") UUID batchId, Pageable pageable);
}
