package com.company.vapt.finding.service;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.repository.FindingReconciliationRunRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.finding.entity.FindingReconciliationRun;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class FindingReconciliationFinalizer {
    private final FindingReconciliationRunRepository runs;
    private final AuditService audit;
    public FindingReconciliationFinalizer(FindingReconciliationRunRepository runs, AuditService audit) {
        this.runs = runs; this.audit = audit;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, String code, String message) {
        FindingReconciliationRun run = runs.findById(id).orElseThrow();
        run.fail(code, message, Instant.now());
        audit.recordInWorkflowTransaction(run.getActorUserId(), "ADMIN", "FINDING_RECONCILIATION_FAILED",
                "FINDING_RECONCILIATION_RUN", id.toString(),
                "section=" + run.getReportSection().getId() + ";batchId=" + run.getImportBatch().getId()
                        + ";diagnostic=" + code);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedWithoutAudit(UUID id, String code, String message) {
        runs.findById(id).ifPresent(run -> run.fail(code, message, Instant.now()));
    }
}
