package com.company.vapt.enrichment.service;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.enrichment.repository.ReportRecordEnrichmentRepository;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;

import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.enrichment.entity.ReportRecordEnrichment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EnrichmentResultPersistenceService {
    private final EnrichmentRunRepository runs;
    private final ReportRecordEnrichmentRepository results;
    private final ReportImportRecordRepository reportRecords;

    public EnrichmentResultPersistenceService(EnrichmentRunRepository runs, ReportRecordEnrichmentRepository results,
                                              ReportImportRecordRepository reportRecords) {
        this.runs = runs; this.results = results; this.reportRecords = reportRecords;
    }

    @Transactional
    public void saveChunk(UUID runId, List<ReportRecordEnrichment> chunk,
                          long matched, long unmatched, long ambiguous) {
        EnrichmentRun run = runs.findById(runId).orElseThrow();
        for (ReportRecordEnrichment result : chunk) {
            result.setEnrichmentRun(run);
            result.setSourceRecord(reportRecords.getReferenceById(result.getSourceRecord().getId()));
        }
        results.saveAll(chunk);
        run.addCounts(chunk.size(), matched, unmatched, ambiguous);
    }

    @Transactional
    public void complete(UUID runId) {
        EnrichmentRun run = runs.findById(runId).orElseThrow();
        run.complete();
    }

    /** Failure status and failure audit commit independently of report activation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID runId, String code, String message, com.company.vapt.audit.service.AuditService audit) {
        EnrichmentRun run = runs.findById(runId).orElseThrow();
        run.fail(code, message);
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "REPORT_ENRICHMENT_FAILED",
                "ENRICHMENT_RUN", runId.toString(), "batchId=" + run.getImportBatch().getId()
                        + ";diagnostic=" + code + ";processed=" + run.getProcessedRows());
    }
}
