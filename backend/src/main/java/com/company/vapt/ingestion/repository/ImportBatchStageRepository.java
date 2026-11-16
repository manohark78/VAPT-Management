package com.company.vapt.ingestion.repository;

import com.company.vapt.ingestion.entity.ImportBatchStage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportBatchStageRepository extends JpaRepository<ImportBatchStage, Long> {

    List<ImportBatchStage> findByBatchIdOrderByIdAsc(UUID batchId);

    List<ImportBatchStage> findByBatchIdOrderByStartedAtAsc(UUID batchId);

    java.util.Optional<ImportBatchStage> findByBatchIdAndStage(UUID batchId, String stage);
}
