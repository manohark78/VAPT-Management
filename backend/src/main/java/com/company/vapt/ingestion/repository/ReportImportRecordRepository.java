package com.company.vapt.ingestion.repository;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

public interface ReportImportRecordRepository extends JpaRepository<ReportImportRecord, UUID> {

    long countByImportBatchId(UUID batchId);

    List<ReportImportRecord> findByImportBatchIdOrderBySourceRowNumber(UUID batchId);

    List<ReportImportRecord> findByImportBatchIdAndSourceRowNumberGreaterThanOrderBySourceRowNumberAsc(UUID batchId, int sourceRowNumber, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select r.sourceIdentity, count(r)
            from ReportImportRecord r
            where r.importBatch.id = :batchId and r.sourceIdentity in :identities
            group by r.sourceIdentity
            """)
    List<Object[]> countIdentities(@Param("batchId") UUID batchId,
                                   @Param("identities") Collection<String> identities);

    boolean existsByImportBatchIdAndSourceIdentityIn(UUID batchId, Collection<String> identities);

    Optional<ReportImportRecord> findFirstByImportBatchIdAndSourceIdentity(UUID batchId, String sourceIdentity);

    @Modifying
    @Query("DELETE FROM ReportImportRecord r WHERE r.importBatch.id = :batchId")
    int deleteAllForBatch(@Param("batchId") UUID batchId);
}
