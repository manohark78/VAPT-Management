package com.company.vapt.finding.service;

import com.company.vapt.finding.entity.Finding;

import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.finding.entity.FindingReconciliationRun;
import com.company.vapt.ingestion.service.ImportProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class FindingReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(FindingReconciliationService.class);
    private final FindingReconciliationCoordinator coordinator;
    private final FindingReconciliationWorker worker;
    private final FindingReconciliationFinalizer finalizer;
    private final PerformanceTimer performanceTimer;
    public FindingReconciliationService(FindingReconciliationCoordinator coordinator,
            FindingReconciliationWorker worker, FindingReconciliationFinalizer finalizer,
            PerformanceTimer performanceTimer) {
        this.coordinator = coordinator; this.worker = worker;
        this.finalizer = finalizer; this.performanceTimer = performanceTimer;
    }
    public FindingReconciliationWorker.ReconciliationResult reconcileNewImport(
            UUID batchId, String sectionId, String actor) {
        FindingReconciliationRun run = coordinator.start(batchId, sectionId, actor);
        try {
            return worker.process(run.getId());
        } catch (Exception failure) {
            String code = "RECONCILIATION_FAILED";
            String safeMessage = "Finding reconciliation failed. The previous active report import was retained.";
            try {
                finalizer.fail(run.getId(), code, safeMessage);
            } catch (Exception auditFailure) {
                try {
                    finalizer.markFailedWithoutAudit(run.getId(),
                            "RECONCILIATION_AUDIT_PERSISTENCE_FAILED", safeMessage);
                } catch (Exception ignored) { }
                log.error("Finding reconciliation failure audit could not be persisted: runId={}, correlationId={}",
                        run.getId(), run.getCorrelationId());
            }
            performanceTimer.recordDuration("finding.reconciliation", "RECONCILING", "FAILURE",
                    java.time.Duration.ZERO);
            throw new ImportProcessingException("RECONCILING", code, safeMessage);
        }
    }
}
