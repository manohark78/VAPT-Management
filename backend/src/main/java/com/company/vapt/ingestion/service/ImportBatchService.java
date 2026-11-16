package com.company.vapt.ingestion.service;

import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ImportBatchStage;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.ImportBatchStageRepository;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.report.repository.ReportSectionRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.ingestion.domain.ImportStateMachine;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.StageStatus;
import com.company.vapt.ingestion.repository.*;
import com.company.vapt.ingestion.storage.StoredFile;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.domain.ReportSectionRegistry;
import com.company.vapt.report.entity.ReportSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Application service managing import batch lifecycles, state transitions, stage execution metrics,
 * and atomic active snapshot management.
 */
@Service
public class ImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchService.class);
    private static final Set<String> IMPORT_STAGES = Set.of("VALIDATING", "STAGING", "ENRICHING", "RECONCILING");

    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchStageRepository importBatchStageRepository;
    private final SourceFileRepository sourceFileRepository;
    private final AppUserRepository appUserRepository;
    private final ReportSectionRepository reportSectionRepository;
    private final ReportSectionRegistry reportSectionRegistry;
    private final ImportStateMachine stateMachine;
    private final AuditService auditService;
    private final PerformanceTimer performanceTimer;

    public ImportBatchService(
            ImportBatchRepository importBatchRepository,
            ImportBatchStageRepository importBatchStageRepository,
            SourceFileRepository sourceFileRepository,
            AppUserRepository appUserRepository,
            ReportSectionRepository reportSectionRepository,
            ReportSectionRegistry reportSectionRegistry,
            ImportStateMachine stateMachine,
            AuditService auditService,
            PerformanceTimer performanceTimer
    ) {
        this.importBatchRepository = importBatchRepository;
        this.importBatchStageRepository = importBatchStageRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.appUserRepository = appUserRepository;
        this.reportSectionRepository = reportSectionRepository;
        this.reportSectionRegistry = reportSectionRegistry;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.performanceTimer = performanceTimer;
    }

    /**
     * Initializes a new import batch in RECEIVED status.
     */
    @Transactional
    public ImportBatch createBatch(ReportSectionId sectionId, SourceFile sourceFile) {
        if (sectionId == null) {
            throw new ValidationException("Report section ID must not be null");
        }
        if (sourceFile == null) {
            throw new ValidationException("Source file must not be null");
        }

        ReportSection section = reportSectionRepository.findById(sectionId.name())
                .orElseThrow(() -> new ResourceNotFoundException("ReportSection", sectionId.name()));

        ImportBatch batch = new ImportBatch(UUID.randomUUID(), section, sourceFile);
        ImportBatch saved = importBatchRepository.save(batch);

        recordBatchAudit(saved, "IMPORT_BATCH_CREATED",
                "section=" + sectionId.name() + ";batchId=" + saved.getId() + ";status=RECEIVED");

        log.info("Created import batch: id={}, section={}, status={}", saved.getId(), sectionId.name(), saved.getStatus());
        return saved;
    }

    /**
     * Persists upload metadata and its initial batch in one database transaction.
     * The caller owns the physical file and must delete it if this method fails,
     * including a transaction commit failure when the proxied call returns.
     */
    @Transactional
    public ImportBatch createBatch(ReportSectionId sectionId, StoredFile storedFile) {
        return createBatch(sectionId, storedFile, false);
    }

    @Transactional
    public ImportBatch createBatch(ReportSectionId sectionId, StoredFile storedFile, boolean completeSnapshot) {
        if (storedFile == null) {
            throw new ValidationException("Stored file must not be null");
        }
        SourceFile savedSourceFile = sourceFileRepository.save(storedFile.toEntity());
        ImportBatch batch = createBatch(sectionId, savedSourceFile);
        batch.setCompleteSnapshot(completeSnapshot);
        batch = importBatchRepository.save(batch);
        recordAudit(batch, resolveAuditActor(savedSourceFile.getUploadedBy()),
                "SOURCE_FILE_UPLOADED", "SOURCE_FILE", savedSourceFile.getId().toString(),
                "section=" + sectionId.name() + ";batchId=" + batch.getId()
                        + ";fileSizeBytes=" + storedFile.fileSizeBytes()
                        + ";fileType=" + storedFile.fileExtension());
        return batch;
    }

    /**
     * Advances an import batch to the next permitted status.
     */
    @Transactional
    public ImportBatch transitionTo(UUID batchId, ImportStatus targetStatus) {
        ImportBatch batch = getBatch(batchId);
        stateMachine.validateTransition(batch.getStatus(), targetStatus);

        ImportStatus previousStatus = batch.getStatus();
        batch.setStatus(targetStatus);

        if (batch.getStartedAt() == null && targetStatus != ImportStatus.RECEIVED) {
            batch.setStartedAt(Instant.now());
        }

        ImportBatch updated = importBatchRepository.save(batch);

        if (previousStatus == ImportStatus.RECEIVED && targetStatus == ImportStatus.VALIDATING) {
            recordBatchAudit(updated, "IMPORT_PROCESSING_STARTED",
                    "section=" + updated.getReportSection().getId() + ";batchId=" + batchId
                            + ";status=" + targetStatus.name());
        }

        log.info("Batch transition: id={}, from={}, to={}", batchId, previousStatus, targetStatus);
        return updated;
    }

    @Transactional
    public ImportBatch updateImportCounts(UUID batchId, int totalRows, int acceptedRows, int rejectedRows) {
        if (totalRows < 0 || acceptedRows < 0 || rejectedRows < 0 || acceptedRows + rejectedRows > totalRows) {
            throw new ValidationException("Import row counts are invalid");
        }
        ImportBatch batch = getBatch(batchId);
        batch.setTotalRows(totalRows);
        batch.setAcceptedRows(acceptedRows);
        batch.setRejectedRows(rejectedRows);
        return importBatchRepository.save(batch);
    }

    /**
     * Records stage execution metrics in the structured import_batch_stage table.
     */
    @Transactional
    public ImportBatchStage recordStageExecution(
            UUID batchId,
            String stageName,
            StageStatus status,
            long durationMs,
            Instant startedAt,
            Instant completedAt,
            String details
    ) {
        String canonicalStage = canonicalStage(stageName);
        StageStatus effectiveStatus = status != null ? status : StageStatus.COMPLETED;

        ImportBatch batch = getBatch(batchId);

        // Reject duplicate stage record attempts
        if (importBatchStageRepository.findByBatchIdAndStage(batchId, canonicalStage).isPresent()) {
            throw new ValidationException(String.format("Stage [%s] has already been recorded for batch [%s]", canonicalStage, batchId));
        }

        ImportBatchStage stage = new ImportBatchStage(
                batch,
                canonicalStage,
                effectiveStatus,
                durationMs,
                startedAt != null ? startedAt : Instant.now(),
                completedAt != null ? completedAt : Instant.now(),
                details
        );

        ImportBatchStage saved = importBatchStageRepository.save(stage);
        if (effectiveStatus == StageStatus.COMPLETED) {
            recordBatchAudit(batch, "IMPORT_STAGE_COMPLETED",
                    "section=" + batch.getReportSection().getId() + ";batchId=" + batchId
                            + ";stage=" + canonicalStage + ";durationMs=" + Math.max(0, durationMs));
        }
        performanceTimer.recordDuration("import.stage", canonicalStage, effectiveStatus.name(),
                java.time.Duration.ofMillis(Math.max(0, durationMs)));

        log.info("Recorded import stage: batchId={}, section={}, stage={}, status={}, durationMs={}, correlationId={}",
                batchId, batch.getReportSection().getId(), canonicalStage, effectiveStatus, durationMs,
                currentCorrelationId());
        return saved;
    }

    /**
     * Records stage completion and advances the batch to nextStatus atomically.
     */
    @Transactional
    public ImportBatch completeStage(
            UUID batchId,
            String stageName,
            ImportStatus nextStatus,
            long durationMs,
            String stageDetails
    ) {
        Instant completedAt = Instant.now();
        Instant startedAt = completedAt.minusMillis(Math.max(0, durationMs));

        recordStageExecution(batchId, stageName, StageStatus.COMPLETED, durationMs, startedAt, completedAt, stageDetails);
        return transitionTo(batchId, nextStatus);
    }

    /**
     * Marks an import batch as FAILED, preserving diagnostic error details and recording the failed stage.
     */
    @Transactional
    public ImportBatch failBatch(
            UUID batchId,
            String errorMessage,
            String errorDetails,
            String failedStageName,
            long stageDurationMs
    ) {
        ImportBatch batch = getBatch(batchId);
        String canonicalFailedStage = failedStageName == null || failedStageName.isBlank()
                ? null : canonicalStage(failedStageName);
        stateMachine.validateTransition(batch.getStatus(), ImportStatus.FAILED);

        batch.markFailed(errorMessage, errorDetails);
        ImportBatch saved = importBatchRepository.save(batch);

        if (canonicalFailedStage != null) {
            if (importBatchStageRepository.findByBatchIdAndStage(batchId, canonicalFailedStage).isEmpty()) {
                Instant completedAt = Instant.now();
                Instant startedAt = completedAt.minusMillis(Math.max(0, stageDurationMs));
                ImportBatchStage failedStage = new ImportBatchStage(
                        batch,
                        canonicalFailedStage,
                        StageStatus.FAILED,
                        stageDurationMs,
                        startedAt,
                        completedAt,
                        errorMessage
                );
                importBatchStageRepository.save(failedStage);
            }
        }

        recordBatchAudit(saved, "IMPORT_FAILED",
                "section=" + batch.getReportSection().getId() + ";batchId=" + batchId
                        + ";status=FAILED;stage=" + (canonicalFailedStage != null ? canonicalFailedStage : "IMPORT")
                        + ";diagnostic=PROCESSING_FAILED");

        if (canonicalFailedStage != null) {
            performanceTimer.recordDuration("import.stage", canonicalFailedStage, StageStatus.FAILED.name(),
                    java.time.Duration.ofMillis(Math.max(0, stageDurationMs)));
        }

        Instant operationStarted = batch.getStartedAt() != null ? batch.getStartedAt() : batch.getCreatedAt();
        performanceTimer.recordDuration("import.batch", "IMPORT", "FAILURE",
                java.time.Duration.between(operationStarted, Instant.now()).isNegative()
                        ? java.time.Duration.ZERO : java.time.Duration.between(operationStarted, Instant.now()));
        log.warn("Import batch failed: id={}, section={}, stage={}, correlationId={}", batchId,
                batch.getReportSection().getId(), canonicalFailedStage != null ? canonicalFailedStage : "IMPORT",
                currentCorrelationId());
        return saved;
    }

    /**
     * Atomically commits an import batch and activates it as the authoritative snapshot for its report section.
     * Uses pessimistic write locking on the ReportSection row to prevent concurrent commit conflicts,
     * coupled with database unique constraint uq_import_batch_active_section.
     */
    @Transactional
    public ImportBatch commitAndActivate(UUID batchId) {
        ImportBatch batch = getBatch(batchId);
        String sectionId = batch.getReportSection().getId();

        // 1. Validate transition to COMMITTED
        stateMachine.validateTransition(batch.getStatus(), ImportStatus.COMMITTED);

        // 2. Concurrency Control: Acquire pessimistic write lock on the section row
        ReportSection section = reportSectionRepository.findByIdWithPessimisticLock(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReportSection", sectionId));

        // 3. Atomically deactivate previous active batch for this section
        int deactivated = importBatchRepository.deactivateActiveBatchForSection(section.getId());
        if (deactivated > 0) {
            log.info("Deactivated {} previous active batch(es) for section {}", deactivated, section.getId());
        }

        // 4. Mark the new batch committed and active
        batch.markCommittedAndActive();
        ImportBatch committed = importBatchRepository.saveAndFlush(batch);

        recordBatchAudit(committed, "IMPORT_COMMITTED_AND_ACTIVATED",
                "section=" + section.getId() + ";batchId=" + batchId + ";status=COMMITTED;active=true");

        Instant operationStarted = batch.getStartedAt() != null ? batch.getStartedAt() : batch.getCreatedAt();
        java.time.Duration operationDuration = java.time.Duration.between(operationStarted, Instant.now());
        performanceTimer.recordDuration("import.batch", "IMPORT", "SUCCESS",
                operationDuration.isNegative() ? java.time.Duration.ZERO : operationDuration);

        log.info("Committed and activated batch: id={}, section={}", batchId, section.getId());
        return committed;
    }

    @Transactional(readOnly = true)
    public ImportBatch getBatch(UUID batchId) {
        if (batchId == null) {
            throw new ValidationException("Batch ID must not be null");
        }
        // DTOs are mapped after this service transaction closes (Open Session in View is disabled).
        // Load the to-one metadata associations required by the public batch DTO explicitly.
        return importBatchRepository.findDtoById(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("ImportBatch", batchId.toString()));
    }

    @Transactional(readOnly = true)
    public Optional<ImportBatch> getActiveBatch(ReportSectionId sectionId) {
        if (sectionId == null) {
            throw new ValidationException("Report section ID must not be null");
        }
        return importBatchRepository.findByReportSectionIdAndActiveTrue(sectionId.name());
    }

    @Transactional(readOnly = true)
    public List<ImportBatchStage> getBatchStages(UUID batchId) {
        if (batchId == null) {
            throw new ValidationException("Batch ID must not be null");
        }
        return importBatchStageRepository.findByBatchIdOrderByIdAsc(batchId);
    }

    private String canonicalStage(String stageName) {
        if (stageName == null || stageName.isBlank()) {
            throw new ValidationException("Stage name must not be empty");
        }
        String canonical = stageName.trim().toUpperCase(Locale.ROOT);
        if (!IMPORT_STAGES.contains(canonical)) {
            throw new ValidationException("Unknown import stage");
        }
        return canonical;
    }

    private void recordBatchAudit(ImportBatch batch, String action, String details) {
        AuditActor actor = resolveAuditActor(batch.getSourceFile().getUploadedBy());
        recordAudit(batch, actor, action, "IMPORT_BATCH", batch.getId().toString(), details);
    }

    private void recordAudit(ImportBatch batch, AuditActor actor, String action,
                             String targetType, String targetId, String details) {
        auditService.recordInWorkflowTransaction(actor.id(), actor.type(), action,
                targetType, targetId, details);
    }

    private AuditActor resolveAuditActor(String uploadedBy) {
        if (uploadedBy != null && !uploadedBy.isBlank()) {
            Optional<AppUser> user = appUserRepository.findByEmail(uploadedBy.trim().toLowerCase(Locale.ROOT));
            if (user.isPresent()) {
                return new AuditActor(String.valueOf(user.get().getId()), user.get().getRole());
            }
        }
        return new AuditActor("SYSTEM", "SYSTEM");
    }

    private String currentCorrelationId() {
        String id = org.slf4j.MDC.get(com.company.vapt.common.logging.CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "SYSTEM";
    }

    private record AuditActor(String id, String type) { }
}
