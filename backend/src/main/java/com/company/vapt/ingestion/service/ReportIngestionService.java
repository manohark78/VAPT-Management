package com.company.vapt.ingestion.service;

import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.finding.entity.Finding;
import com.company.vapt.ingestion.entity.SourceFile;

import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.StageStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.storage.FileStorageService;
import com.company.vapt.ingestion.storage.StoredFile;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.enrichment.service.ReportToCmdbEnrichmentService;
import com.company.vapt.finding.service.FindingReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Orchestrates parsing, bounded persistence, and the existing atomic import activation. */
@Service
public class ReportIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ReportIngestionService.class);
    private static final int CHUNK_SIZE = 250;

    private final ImportBatchService importBatchService;
    private final ReportRecordPersistenceService recordPersistenceService;
    private final ReportFileParser parser;
    private final FileStorageService fileStorageService;
    private final ReportToCmdbEnrichmentService enrichmentService;
    private final FindingReconciliationService reconciliationService;

    public ReportIngestionService(ImportBatchService importBatchService,
                                  ReportRecordPersistenceService recordPersistenceService,
                                  ReportFileParser parser,
                                  FileStorageService fileStorageService,
                                  ReportToCmdbEnrichmentService enrichmentService,
                                  FindingReconciliationService reconciliationService) {
        this.importBatchService = importBatchService;
        this.recordPersistenceService = recordPersistenceService;
        this.parser = parser;
        this.fileStorageService = fileStorageService;
        this.enrichmentService = enrichmentService;
        this.reconciliationService = reconciliationService;
    }

    public ImportBatch process(ImportBatch createdBatch, StoredFile sourceFile) {
        UUID batchId = createdBatch.getId();
        ReportSectionId sectionId = ReportSectionId.valueOf(createdBatch.getReportSection().getId());
        java.util.concurrent.atomic.AtomicReference<String> failedStage = new java.util.concurrent.atomic.AtomicReference<>("VALIDATING");
        Instant validationStarted = Instant.now();
        java.util.concurrent.atomic.AtomicReference<Instant> stageStarted = new java.util.concurrent.atomic.AtomicReference<>(validationStarted);
        Counters counters = new Counters();
        List<ReportRecordPersistenceService.SourceRow> chunk = new ArrayList<>(CHUNK_SIZE);
        try {
            importBatchService.transitionTo(batchId, ImportStatus.VALIDATING);
            parser.parse(sectionId, sourceFile.storagePath(), sourceFile.fileExtension(),
                    ignored -> {
                        complete(batchId, "VALIDATING", ImportStatus.STAGING, validationStarted,
                                "File format and report identity header validated");
                        failedStage.set("STAGING");
                        stageStarted.set(Instant.now());
                    },
                    () -> counters.total++,
                    row -> {
                        chunk.add(row);
                        if (chunk.size() == CHUNK_SIZE) flush(batchId, sectionId, chunk, counters);
                    });

            failedStage.set("STAGING");
            stageStarted.set(Instant.now());
            flush(batchId, sectionId, chunk, counters);
            if (counters.total == 0) {
                throw new ImportProcessingException("STAGING", "NO_DATA_ROWS", "The report contains no non-empty data rows.");
            }
            importBatchService.updateImportCounts(batchId, counters.total, counters.accepted, 0);
            complete(batchId, "STAGING", ImportStatus.ENRICHING, stageStarted.get(),
                    "Validated source rows persisted in bounded batches");

            failedStage.set("ENRICHING");
            stageStarted.set(Instant.now());
            if (sectionId == ReportSectionId.APPSEC_FINDINGS_PT) {
                importBatchService.recordStageExecution(batchId, "ENRICHING", StageStatus.SKIPPED,
                        0, stageStarted.get(), stageStarted.get(), "AppSec CMDB mapping is not defined");
            } else {
                var run = enrichmentService.enrichNewImport(batchId, sectionId.name(), createdBatch.getSourceFile().getUploadedBy());
                complete(batchId, "ENRICHING", ImportStatus.RECONCILING, stageStarted.get(),
                        "Enriched " + run.getProcessedRows() + " report records using pinned CMDB versions "
                                + run.getJasVersionId() + "," + run.getHeadcountVersionId() + "," + run.getAppInventoryVersionId());
            }
            if (sectionId == ReportSectionId.APPSEC_FINDINGS_PT) importBatchService.transitionTo(batchId, ImportStatus.RECONCILING);

            failedStage.set("RECONCILING");
            stageStarted.set(Instant.now());
            if (sectionId == ReportSectionId.APPSEC_FINDINGS_PT) {
                importBatchService.recordStageExecution(batchId, "RECONCILING", StageStatus.SKIPPED,
                        0, stageStarted.get(), stageStarted.get(), "AppSec Finding ID reconciliation is outside Phase 6A");
            } else {
                var result = reconciliationService.reconcileNewImport(
                        batchId, sectionId.name(), createdBatch.getSourceFile().getUploadedBy());
                long durationMs = Math.max(0, Duration.between(stageStarted.get(), Instant.now()).toMillis());
                importBatchService.recordStageExecution(batchId, "RECONCILING", StageStatus.COMPLETED,
                        durationMs, stageStarted.get(), Instant.now(),
                        "processed=" + result.processedRows() + ";new=" + result.newFindings()
                                + ";existingOpen=" + result.existingOpen()
                                + ";unmatchedIdentity=" + result.unmatchedIdentity());
            }
            return importBatchService.commitAndActivate(batchId);
        } catch (Exception e) {
            String safeMessage = safeMessage(e);
            String diagnosticCode = e instanceof ImportProcessingException p ? p.getDiagnosticCode() : "PROCESSING_FAILED";
            String stage = e instanceof ImportProcessingException p ? p.getStage() : failedStage.get();
            try {
                recordPersistenceService.deleteBatchRows(batchId);
            } catch (Exception cleanupFailure) {
                log.error("Failed import row cleanup: batchId={}, correlationId={}", batchId, correlationId(), cleanupFailure);
                safeMessage = "The import failed and staged rows could not be fully removed. Contact an administrator with the correlation ID.";
                diagnosticCode = "STAGING_CLEANUP_FAILED";
            }
            importBatchService.updateImportCounts(batchId, counters.total,
                    Math.min(counters.accepted, counters.total), counters.total > counters.accepted ? counters.total - counters.accepted : 0);
            long durationMs = Math.max(0, Duration.between(stageStarted.get(), Instant.now()).toMillis());
            return importBatchService.failBatch(batchId, safeMessage,
                    "code=" + diagnosticCode + ";stage=" + stage,
                    stage, durationMs);
        }
    }

    private void flush(UUID batchId, ReportSectionId sectionId,
                       List<ReportRecordPersistenceService.SourceRow> chunk, Counters counters) {
        if (chunk.isEmpty()) return;
        try {
            if (sectionId == ReportSectionId.APPSEC_FINDINGS_PT) {
                java.util.Set<String> chunkIdentities = new java.util.HashSet<>();
                for (ReportRecordPersistenceService.SourceRow row : chunk) {
                    if (!chunkIdentities.add(row.identity())) {
                        throw new ImportProcessingException("STAGING", "DUPLICATE_IDENTITY",
                                "Row " + row.rowNumber() + " duplicates a Finding ID in this report.");
                    }
                }
                if (recordPersistenceService.appSecIdentityAlreadyExists(
                        batchId, chunkIdentities)) {
                    throw new ImportProcessingException("STAGING", "DUPLICATE_IDENTITY",
                            "A row duplicates a Finding ID from an earlier import chunk.");
                }
            }
            recordPersistenceService.saveChunk(batchId, List.copyOf(chunk));
            counters.accepted += chunk.size();
            chunk.clear();
        } catch (ImportProcessingException e) {
            throw e;
        } catch (DataIntegrityViolationException e) {
            int first = chunk.get(0).rowNumber();
            int last = chunk.get(chunk.size() - 1).rowNumber();
            throw new ImportProcessingException("STAGING", "DUPLICATE_IDENTITY",
                    "Rows " + first + "–" + last + " contain a duplicate UID or Finding ID.", e);
        }
    }

    private void complete(UUID batchId, String stage, ImportStatus next, Instant started, String details) {
        long duration = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        importBatchService.completeStage(batchId, stage, next, duration, details);
    }

    private String safeMessage(Exception e) {
        if (e instanceof ImportProcessingException) return e.getMessage();
        return "The report import failed while processing. No rows were activated.";
    }

    private String correlationId() {
        String id = org.slf4j.MDC.get(com.company.vapt.common.logging.CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "SYSTEM";
    }

    private static final class Counters {
        int total;
        int accepted;
    }
}
