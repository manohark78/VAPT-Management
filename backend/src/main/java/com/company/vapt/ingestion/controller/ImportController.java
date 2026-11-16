package com.company.vapt.ingestion.controller;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ImportBatchStage;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.ImportBatchStageRepository;
import com.company.vapt.user.service.UserScopeService;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.ingestion.dto.response.ImportBatchDetailDto;
import com.company.vapt.ingestion.dto.response.ImportBatchDto;
import com.company.vapt.ingestion.service.ImportBatchService;
import com.company.vapt.ingestion.service.ReportIngestionService;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.repository.*;
import com.company.vapt.ingestion.storage.FileStorageService;
import com.company.vapt.ingestion.storage.StorageException;
import com.company.vapt.ingestion.storage.StoredFile;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.domain.ReportSectionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for import batch operations within a report section.
 *
 * Base path: /api/reports/sections/{sectionId}/imports
 *
 * Authorization: ADMIN only. USER access remains closed until a report-section
 * grant model is defined; UserScopeService currently covers assets/applications only.
 */
@RestController
@RequestMapping("/api/reports/sections/{sectionId}/imports")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final FileStorageService fileStorageService;
    private final ImportBatchService importBatchService;
    private final PerformanceTimer performanceTimer;
    private final ImportBatchRepository importBatchRepository;
    private final ImportBatchStageRepository importBatchStageRepository;
    private final ReportSectionRegistry reportSectionRegistry;
    private final ReportIngestionService reportIngestionService;

    public ImportController(
            FileStorageService fileStorageService,
            ImportBatchService importBatchService,
            PerformanceTimer performanceTimer,
            ImportBatchRepository importBatchRepository,
            ImportBatchStageRepository importBatchStageRepository,
            ReportSectionRegistry reportSectionRegistry,
            ReportIngestionService reportIngestionService) {
        this.fileStorageService = fileStorageService;
        this.importBatchService = importBatchService;
        this.performanceTimer = performanceTimer;
        this.importBatchRepository = importBatchRepository;
        this.importBatchStageRepository = importBatchStageRepository;
        this.reportSectionRegistry = reportSectionRegistry;
        this.reportIngestionService = reportIngestionService;
    }

    // -------------------------------------------------------------------------
    // POST /api/reports/sections/{sectionId}/imports
    // Admin only: upload a source file and initialize a new import batch.
    // -------------------------------------------------------------------------

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportBatchDto>> uploadImport(
            @PathVariable String sectionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean completeSnapshot,
            Authentication authentication) {

        Instant startedAt = Instant.now();
        String result = "FAILURE";
        try {
            ResponseEntity<ApiResponse<ImportBatchDto>> response = uploadImportInternal(sectionId, file, completeSnapshot, authentication);
            ImportBatchDto uploadedBatch = response.getBody() != null ? response.getBody().getData() : null;
            result = uploadedBatch != null && "FAILED".equals(uploadedBatch.status()) ? "FAILURE" : "SUCCESS";
            return response;
        } finally {
            Duration duration = Duration.between(startedAt, Instant.now());
            performanceTimer.recordDuration("import.upload", "UPLOAD", result,
                    duration.isNegative() ? Duration.ZERO : duration);
        }
    }

    private ResponseEntity<ApiResponse<ImportBatchDto>> uploadImportInternal(
            String sectionId, MultipartFile file, boolean completeSnapshot, Authentication authentication) {

        String correlationId = correlationId();

        // Validate section identifier
        ReportSectionId resolvedSectionId = reportSectionRegistry.resolve(sectionId);

        // Basic file presence check
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded file must not be empty");
        }

        String uploadedBy = authentication != null ? authentication.getName() : "UNKNOWN";
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";
        String contentType = file.getContentType() != null
                ? file.getContentType() : "application/octet-stream";

        log.info("Upload started: section={}, sizeBytes={}, correlationId={}",
                resolvedSectionId.name(), file.getSize(), correlationId);

        // 1. Stream file to disk storage (validates extension, magic bytes, size)
        StoredFile storedFile;
        try {
            storedFile = fileStorageService.store(
                    resolvedSectionId,
                    file.getInputStream(),
                    originalFilename,
                    contentType,
                    uploadedBy
            );
        } catch (IOException e) {
            log.error("Failed to read uploaded file stream: section={}, correlationId={}",
                    resolvedSectionId.name(), correlationId);
            throw new StorageException("Failed to read uploaded file stream");
        }

        // 2. Persist SourceFile metadata (storage path stored internally, never returned)
        // Persist source-file metadata and the initial batch atomically. The service
        // transaction has completed (including commit) before this call returns.
        ImportBatch batch;
        try {
            batch = importBatchService.createBatch(resolvedSectionId, storedFile, completeSnapshot);
        } catch (Exception e) {
            // Database work rolled back; remove the physical file to avoid an orphan.
            log.error("Batch creation failed after file storage. Cleaning up stored file: {}",
                    storedFile.storagePath(), e);
            try {
                fileStorageService.delete(storedFile.storagePath());
            } catch (RuntimeException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
                log.error("Failed to clean up stored file after upload persistence failure: {}",
                        storedFile.storagePath(), cleanupFailure);
            }
            throw e;
        }

        log.info("Import batch created: id={}, section={}, correlationId={}",
                batch.getId(), resolvedSectionId.name(), correlationId);

        // Parsing and row writes are streamed/chunked; activation happens only after full validation.
        batch = reportIngestionService.process(batch, storedFile);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ImportBatchDto.from(batch), correlationId));
    }

    // -------------------------------------------------------------------------
    // GET /api/reports/sections/{sectionId}/imports
    // Paginated import history for a section.
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ImportBatchDto>>> listImports(
            @PathVariable String sectionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {

        String correlationId = correlationId();

        // Validate section
        ReportSectionId resolvedSectionId = reportSectionRegistry.resolve(sectionId);

        // Clamp page size
        int clampedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(Math.max(0, page), clampedSize);

        Page<ImportBatch> batchPage;
        if (status != null && !status.isBlank()) {
            ImportStatus importStatus = parseStatus(status);
            batchPage = importBatchRepository.findByReportSectionIdAndStatusOrderByCreatedAtDesc(
                    resolvedSectionId.name(), importStatus, pageable);
        } else {
            batchPage = importBatchRepository.findByReportSectionIdOrderByCreatedAtDesc(
                    resolvedSectionId.name(), pageable);
        }

        Page<ImportBatchDto> dtoPage = batchPage.map(ImportBatchDto::from);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(dtoPage), correlationId));
    }

    // -------------------------------------------------------------------------
    // GET /api/reports/sections/{sectionId}/imports/active
    // Returns the current active committed batch for a section, or null.
    // NOTE: This path MUST be declared before /{batchId} to avoid ambiguity.
    // -------------------------------------------------------------------------

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<ImportBatchDto>> getActiveBatch(@PathVariable String sectionId) {
        String correlationId = correlationId();

        ReportSectionId resolvedSectionId = reportSectionRegistry.resolve(sectionId);

        Optional<ImportBatch> activeBatch = importBatchService.getActiveBatch(resolvedSectionId);

        ImportBatchDto dto = activeBatch.map(ImportBatchDto::from).orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(dto, correlationId));
    }

    // -------------------------------------------------------------------------
    // GET /api/reports/sections/{sectionId}/imports/{batchId}
    // Full batch detail including stage breakdown.
    // Verifies that batchId belongs to sectionId (IDOR protection).
    // -------------------------------------------------------------------------

    @GetMapping("/{batchId}")
    public ResponseEntity<ApiResponse<ImportBatchDetailDto>> getBatchDetail(
            @PathVariable String sectionId,
            @PathVariable UUID batchId) {

        String correlationId = correlationId();

        // Validate section
        ReportSectionId resolvedSectionId = reportSectionRegistry.resolve(sectionId);

        // IDOR check: batch must belong to this section
        boolean belongs = importBatchRepository.existsByIdAndReportSectionId(
                batchId, resolvedSectionId.name());
        if (!belongs) {
            throw new ResourceNotFoundException("ImportBatch", batchId.toString());
        }

        ImportBatch batch = importBatchService.getBatch(batchId);
        List<ImportBatchStage> stages = importBatchService.getBatchStages(batchId);

        return ResponseEntity.ok(ApiResponse.ok(ImportBatchDetailDto.from(batch, stages), correlationId));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ImportStatus parseStatus(String status) {
        try {
            return ImportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown import status filter: " + status);
        }
    }

    private String correlationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "unknown";
    }
}
