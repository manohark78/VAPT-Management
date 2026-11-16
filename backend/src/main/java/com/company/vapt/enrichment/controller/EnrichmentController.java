package com.company.vapt.enrichment.controller;

import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.enrichment.repository.ReportRecordEnrichmentRepository;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.repository.ImportBatchRepository;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.response.PageResponse;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.enrichment.service.ReportToCmdbEnrichmentService;
import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.enrichment.entity.ReportRecordEnrichment;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.domain.ReportSectionRegistry;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class EnrichmentController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> RESULT_STATUSES = Set.of("MATCHED", "UNMATCHED", "AMBIGUOUS");
    private final ReportToCmdbEnrichmentService enrichmentService;
    private final EnrichmentRunRepository runs;
    private final ReportRecordEnrichmentRepository results;
    private final ImportBatchRepository batches;
    private final ReportSectionRegistry sections;
    private final ObjectMapper json = JsonMapper.builder().build();

    public EnrichmentController(ReportToCmdbEnrichmentService enrichmentService,
            EnrichmentRunRepository runs, ReportRecordEnrichmentRepository results,
            ImportBatchRepository batches, ReportSectionRegistry sections) {
        this.enrichmentService = enrichmentService; this.runs = runs; this.results = results;
        this.batches = batches; this.sections = sections;
    }

    @PostMapping("/reports/sections/{sectionId}/imports/{batchId}/enrichments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enrichCommittedImport(
            @PathVariable String sectionId, @PathVariable UUID batchId,
            @RequestParam(defaultValue = "false") boolean rerun, Authentication authentication) {
        ReportSectionId section = sections.resolve(sectionId);
        EnrichmentRun run = enrichmentService.enrichCommittedImport(batchId, section.name(),
                authentication.getName(), rerun);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(runDto(run), correlationId()));
    }

    @GetMapping("/reports/sections/{sectionId}/imports/{batchId}/enrichments")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> history(
            @PathVariable String sectionId, @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        ReportSectionId section = sections.resolve(sectionId);
        if (!batches.existsByIdAndReportSectionId(batchId, section.name()))
            throw new ResourceNotFoundException("ImportBatch", batchId.toString());
        Page<EnrichmentRun> found = runs.findByImportBatchIdOrderByStartedAtDesc(batchId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)), Sort.by("startedAt").descending()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(found.map(this::runDto)), correlationId()));
    }

    @GetMapping("/enrichments/{runId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(@PathVariable UUID runId) {
        EnrichmentRun run = runs.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("EnrichmentRun", runId.toString()));
        return ResponseEntity.ok(ApiResponse.ok(runDto(run), correlationId()));
    }

    @GetMapping("/enrichments/{runId}/results")
    public ResponseEntity<ApiResponse<PageResponse<Map<String, Object>>>> results(
            @PathVariable UUID runId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status) {
        if (!runs.existsById(runId)) throw new ResourceNotFoundException("EnrichmentRun", runId.toString());
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)),
                Sort.by("sourceRowNumber").ascending());
        Page<ReportRecordEnrichment> found;
        if (status == null || status.isBlank()) found = results.findByEnrichmentRunIdOrderBySourceRowNumberAsc(runId, pageable);
        else {
            String normalized = status.trim().toUpperCase();
            if (!RESULT_STATUSES.contains(normalized)) throw new ValidationException("Unknown enrichment status filter");
            found = results.findByEnrichmentRunIdAndTargetMatchStatusOrderBySourceRowNumberAsc(runId, normalized, pageable);
        }
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(found.map(this::resultDto)), correlationId()));
    }

    private Map<String, Object> runDto(EnrichmentRun run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", run.getId()); out.put("importBatchId", run.getImportBatch().getId());
        out.put("reportSectionId", run.getReportSectionId()); out.put("status", run.getStatus());
        out.put("mappingMethod", run.getMappingMethod()); out.put("jasVersionId", run.getJasVersionId());
        out.put("headcountVersionId", run.getHeadcountVersionId()); out.put("appInventoryVersionId", run.getAppInventoryVersionId());
        out.put("startedAt", run.getStartedAt()); out.put("completedAt", run.getCompletedAt());
        out.put("processedRows", run.getProcessedRows()); out.put("matchedRows", run.getMatchedRows());
        out.put("unmatchedRows", run.getUnmatchedRows()); out.put("ambiguousRows", run.getAmbiguousRows());
        out.put("diagnosticCode", run.getDiagnosticCode()); out.put("diagnosticMessage", run.getDiagnosticMessage());
        out.put("correlationId", run.getCorrelationId());
        return out;
    }

    private Map<String, Object> resultDto(ReportRecordEnrichment result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", result.getId()); out.put("sourceRowNumber", result.getSourceRowNumber());
        out.put("reportImportRecordId", result.getSourceRecord().getId());
        out.put("originalTarget", result.getOriginalTarget()); out.put("targetType", result.getTargetType());
        out.put("targetMatchStatus", result.getTargetMatchStatus()); out.put("matchReason", result.getMatchReason());
        out.put("matchMethod", result.getMatchMethod()); out.put("applicationDisplay", result.getApplicationDisplay());
        out.put("applicationNameStatus", result.getApplicationNameStatus());
        out.put("ownerResolutionStatus", result.getOwnerResolutionStatus());
        out.put("applicationResolutionStatus", result.getApplicationResolutionStatus());
        out.put("enrichedValues", readJson(result.getEnrichedValuesJson()));
        out.put("candidates", readJson(result.getCandidateDetailsJson()));
        return out;
    }

    private Object readJson(String value) {
        try { return json.readValue(value, Object.class); }
        catch (Exception ignored) { return Map.of(); }
    }

    private String correlationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id == null ? "unknown" : id;
    }
}
