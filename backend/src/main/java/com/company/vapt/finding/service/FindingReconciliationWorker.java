package com.company.vapt.finding.service;

import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.enrichment.repository.ReportRecordEnrichmentRepository;
import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingEvent;
import com.company.vapt.finding.entity.FindingObservation;
import com.company.vapt.finding.entity.FindingReconciliationRun;
import com.company.vapt.finding.repository.FindingEventRepository;
import com.company.vapt.finding.repository.FindingObservationRepository;
import com.company.vapt.finding.repository.FindingReconciliationRunRepository;
import com.company.vapt.finding.repository.FindingRemediationActionRepository;
import com.company.vapt.finding.repository.FindingRepository;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;
import com.company.vapt.report.repository.ReportSectionRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.finding.repository.*;
import com.company.vapt.ingestion.repository.*;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.enrichment.entity.ReportRecordEnrichment;
import com.company.vapt.finding.entity.FindingObservation.SnapshotValues;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** Serializes reconciliation per report section and flushes bounded row chunks in one transaction. */
@Service
public class FindingReconciliationWorker {
    private final FindingReconciliationRunRepository runs;
    private final ImportBatchRepository batches;
    private final ReportSectionRepository sections;
    private final ReportImportRecordRepository reportRecords;
    private final FindingRepository findings;
    private final FindingObservationRepository observations;
    private final FindingEventRepository events;
    private final AuditService audit;
    private final PerformanceTimer performanceTimer;
    private final EntityManager entityManager;
    private final EnrichmentRunRepository enrichmentRuns;
    private final ReportRecordEnrichmentRepository enrichments;
    private final FindingRemediationActionRepository remediationActions;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final int chunkSize;

    public FindingReconciliationWorker(FindingReconciliationRunRepository runs, ImportBatchRepository batches,
            ReportSectionRepository sections, ReportImportRecordRepository reportRecords,
            FindingRepository findings, FindingObservationRepository observations, FindingEventRepository events,
            AuditService audit, PerformanceTimer performanceTimer, EntityManager entityManager,
            EnrichmentRunRepository enrichmentRuns, ReportRecordEnrichmentRepository enrichments,
            FindingRemediationActionRepository remediationActions,
            @Value("${vapt.reconciliation.batch-size:250}") int chunkSize) {
        this.runs = runs;
        this.batches = batches;
        this.sections = sections;
        this.reportRecords = reportRecords;
        this.findings = findings;
        this.observations = observations;
        this.events = events;
        this.audit = audit;
        this.performanceTimer = performanceTimer;
        this.entityManager = entityManager;
        this.enrichmentRuns = enrichmentRuns;
        this.enrichments = enrichments;
        this.remediationActions = remediationActions;
        this.chunkSize = Math.max(1, Math.min(1000, chunkSize));
    }

    @Transactional
    public ReconciliationResult process(UUID runId) {
        FindingReconciliationRun run = runs.findById(runId).orElseThrow();
        UUID batchId = run.getImportBatch().getId();
        String sectionId = run.getReportSection().getId();
        ImportBatch batch = batches.lockById(batchId).orElseThrow();
        if (!"RUNNING".equals(run.getStatus()) || batch.getStatus() != com.company.vapt.ingestion.domain.ImportStatus.RECONCILING) {
            throw new IllegalStateException("Reconciliation run is not ready");
        }
        sections.findByIdWithPessimisticLock(sectionId).orElseThrow();
        long started = System.nanoTime();
        long processed = 0;
        long newRows = 0;
        long existingRows = 0;
        long unmatchedRows = 0;
        long closedRows = 0, reopenedRows = 0, returnedRows = 0, missingRows = 0;
        int lastRow = 0;
        UUID enrichmentRunId = enrichmentRuns.findFirstByImportBatchIdAndStatusOrderByStartedAtDesc(batchId, "COMPLETED")
                .map(enrichmentRun -> enrichmentRun.getId()).orElse(null);

        while (true) {
            List<ReportImportRecord> sourceChunk = reportRecords
                    .findByImportBatchIdAndSourceRowNumberGreaterThanOrderBySourceRowNumberAsc(
                            batchId, lastRow, PageRequest.of(0, chunkSize));
            if (sourceChunk.isEmpty()) break;
            lastRow = sourceChunk.getLast().getSourceRowNumber();
            Instant observedAt = Instant.now();
            List<UUID> sourceIds = sourceChunk.stream().map(ReportImportRecord::getId).toList();
            Map<UUID, ReportRecordEnrichment> enrichmentBySource = enrichmentRunId == null ? Map.of()
                    : enrichments.findByEnrichmentRunIdAndSourceRecordIdIn(enrichmentRunId, sourceIds).stream()
                        .collect(java.util.stream.Collectors.toMap(e -> e.getSourceRecord().getId(), e -> e, (a, b) -> a));

            Set<String> identities = new HashSet<>();
            sourceChunk.stream().map(ReportImportRecord::getSourceIdentity)
                    .filter(value -> value != null && !value.isBlank()).forEach(identities::add);
            Map<String, Long> countByIdentity = new HashMap<>();
            if (!identities.isEmpty()) {
                for (Object[] row : reportRecords.countIdentities(batchId, identities)) {
                    countByIdentity.put(Objects.toString(row[0]), ((Number) row[1]).longValue());
                }
            }
            Set<String> validIdentities = new HashSet<>();
            for (String identity : identities) {
                if (countByIdentity.getOrDefault(identity, 0L) == 1L) validIdentities.add(identity);
            }

            Map<String, List<Finding>> candidatesByIdentity = new HashMap<>();
            if (!validIdentities.isEmpty()) {
                for (Finding finding : findings.findByReportSectionIdAndSourceUidIn(sectionId, validIdentities)) {
                    candidatesByIdentity.computeIfAbsent(finding.getSourceUid(), ignored -> new ArrayList<>())
                            .add(finding);
                }
            }
            Map<String, Finding> newByIdentity = new LinkedHashMap<>();
            for (String identity : validIdentities) {
                if (candidatesByIdentity.getOrDefault(identity, List.of()).isEmpty()) {
                    newByIdentity.put(identity, new Finding(UUID.randomUUID(),
                            entityManager.getReference(ReportSection.class, sectionId), identity, observedAt));
                }
            }
            if (!newByIdentity.isEmpty()) {
                findings.saveAllAndFlush(newByIdentity.values());
                newByIdentity.forEach((identity, finding) ->
                        candidatesByIdentity.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(finding));
            }

            ImportBatch batchRef = entityManager.getReference(ImportBatch.class, batchId);
            ReportSection sectionRef = entityManager.getReference(ReportSection.class, sectionId);
            Set<UUID> knownFindingIds = candidatesByIdentity.values().stream().flatMap(List::stream)
                    .map(Finding::getId).collect(java.util.stream.Collectors.toSet());
            Set<UUID> priorActionIds = knownFindingIds.isEmpty() ? Set.of()
                    : remediationActions.findByFindingIdIn(knownFindingIds).stream()
                        .map(action -> action.getFinding().getId()).collect(java.util.stream.Collectors.toSet());
            List<FindingObservation> observationChunk = new ArrayList<>(sourceChunk.size());
            List<FindingEvent> eventChunk = new ArrayList<>(sourceChunk.size());
            for (ReportImportRecord source : sourceChunk) {
                processed++;
                String identity = source.getSourceIdentity();
                String classification;
                String issue = null;
                Finding selected = null;
                if (identity == null || identity.isBlank()) {
                    classification = "UNMATCHED_IDENTITY";
                    issue = "MISSING_UID";
                    unmatchedRows++;
                } else if (countByIdentity.getOrDefault(identity, 0L) > 1L) {
                    classification = "UNMATCHED_IDENTITY";
                    issue = "DUPLICATE_UID";
                    unmatchedRows++;
                } else {
                    List<Finding> candidates = candidatesByIdentity.getOrDefault(identity, List.of());
                    if (candidates.size() > 1) {
                        classification = "UNMATCHED_IDENTITY";
                        issue = "DUPLICATE_CANONICAL_IDENTITY";
                        unmatchedRows++;
                    } else {
                        selected = candidates.getFirst();
                        if (newByIdentity.containsKey(identity)) { classification = "NEW"; newRows++; }
                        else if ("CLOSED".equals(selected.getLifecycleState())) {
                            classification = "REOPENED"; closedRows++; reopenedRows++;
                            eventChunk.add(new FindingEvent(UUID.randomUUID(), selected, batchRef, source,
                                    "EXISTING_CLOSED", observedAt, run.getActorUserId(), "Previously closed identity reappeared"));
                            selected.reopen(observedAt);
                        } else if (priorActionIds.contains(selected.getId())) {
                            classification = "RETURNED_UNRESOLVED"; returnedRows++;
                        } else { classification = "EXISTING_OPEN"; existingRows++; }
                    }
                }
                FindingObservation observation=new FindingObservation(UUID.randomUUID(), selected, batchRef, sectionRef,
                        source, classification, issue, observedAt, snapshot(source, enrichmentBySource.get(source.getId()), selected));
                if("REOPENED".equals(classification)) observation.setPreviousStateClassification("EXISTING_CLOSED");
                observationChunk.add(observation);
                if (selected != null) eventChunk.add(new FindingEvent(UUID.randomUUID(), selected, batchRef,
                        source, classification, observedAt, run.getActorUserId(), null));
            }
            observations.saveAll(observationChunk);
            events.saveAll(eventChunk);
            entityManager.flush();
            entityManager.clear();
        }

        if (batch.isCompleteSnapshot()) {
            int page = 0;
            org.springframework.data.domain.Page<Finding> missing;
            do {
                missing = findings.findOpenMissingFromBatch(sectionId, batchId, PageRequest.of(page++, chunkSize,
                        org.springframework.data.domain.Sort.by("sourceUid").ascending().and(org.springframework.data.domain.Sort.by("id").ascending())));
                List<FindingObservation> missingObservations = new ArrayList<>(missing.getNumberOfElements());
                List<FindingEvent> missingEvents = new ArrayList<>(missing.getNumberOfElements());
                Map<UUID,FindingObservation> priorByFinding=missing.isEmpty()?Map.of():observations.findLatestForFindings(
                        missing.getContent().stream().map(Finding::getId).toList()).stream().collect(java.util.stream.Collectors.toMap(o->o.getFinding().getId(),o->o));
                Instant at = Instant.now();
                for (Finding finding : missing.getContent()) {
                    ImportBatch batchRef = entityManager.getReference(ImportBatch.class, batchId);
                    ReportSection sectionRef = entityManager.getReference(ReportSection.class, sectionId);
                    missingObservations.add(new FindingObservation(UUID.randomUUID(), finding, batchRef, sectionRef,
                            null, "MISSING_FROM_COMPLETE_SNAPSHOT", null, at,
                            priorByFinding.containsKey(finding.getId())?priorByFinding.get(finding.getId()).snapshotValues():null));
                    missingEvents.add(new FindingEvent(UUID.randomUUID(), finding, batchRef, null,
                            "MISSING_FROM_COMPLETE_SNAPSHOT", at, run.getActorUserId(),
                            "Complete snapshot indicates absence; closure requires administrator review"));
                }
                observations.saveAll(missingObservations); events.saveAll(missingEvents);
                missingRows += missing.getNumberOfElements();
                entityManager.flush(); entityManager.clear();
            } while (missing.hasNext());
        }

        FindingReconciliationRun completed = runs.findById(runId).orElseThrow();
        completed.complete(processed, newRows, existingRows, unmatchedRows, closedRows, reopenedRows,
                returnedRows, missingRows, Instant.now());
        audit.recordInWorkflowTransaction(completed.getActorUserId(), "ADMIN",
                "FINDING_RECONCILIATION_COMPLETED", "FINDING_RECONCILIATION_RUN", runId.toString(),
                "section=" + sectionId + ";batchId=" + batchId + ";processed=" + processed
                        + ";new=" + newRows + ";existingOpen=" + existingRows
                        + ";unmatchedIdentity=" + unmatchedRows + ";existingClosed=" + closedRows
                        + ";reopened=" + reopenedRows + ";returnedUnresolved=" + returnedRows
                        + ";missingFromCompleteSnapshot=" + missingRows);
        performanceTimer.recordDuration("finding.reconciliation", "RECONCILING", "SUCCESS",
                java.time.Duration.ofNanos(Math.max(0, System.nanoTime() - started)));
        return new ReconciliationResult(processed, newRows, existingRows, unmatchedRows,
                closedRows, reopenedRows, returnedRows, missingRows);
    }

    @SuppressWarnings("unchecked")
    private SnapshotValues snapshot(ReportImportRecord source, ReportRecordEnrichment enrichment, Finding finding) {
        try {
            Map<String, String> fields = json.readValue(source.getSourceValues(),
                    json.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            Map<String, Object> enriched = enrichment == null ? Map.of() : json.readValue(enrichment.getEnrichedValuesJson(),
                    json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            Map<String, Object> jas = enriched.get("jas") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Map<String, Object> headcount = enriched.get("headcount") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Map<String, Object> inventory = enriched.get("applicationInventory") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            String rating = fields.get("Risk Rating");
            // No approved source date is defined for SLA calculations yet. Persist no fabricated due date.
            Instant dueAt = null;
            return new SnapshotValues(source.getSourceValues(), rating, fields.get("Risk Score"),
                    fields.get("Compliance Status"), fields.get("Severity"), fields.get("Type"), fields.get("Port"),
                    fields.get("Results"), fields.get("Targets"), fields.get("Display Name"), fields.get("AppTag"),
                    string(jas.get("IP Address")), string(jas.get("Tu_AppName")), string(jas.get("Environment")),
                    string(jas.get("OwnedBy")), string(jas.get("Application ID")), null, dueAt,
                    string(headcount.get("Associated's Email")), string(headcount.get("CM4")), string(headcount.get("CM5")),
                    string(headcount.get("CM6")), string(inventory.get("Plan Application Owner 1")),
                    string(inventory.get("Plan Application Owner 2")));
        } catch (Exception e) { throw new IllegalStateException("Validated finding snapshot could not be mapped"); }
    }

    private String string(Object value) { return value == null ? null : value.toString(); }

    public record ReconciliationResult(long processedRows, long newFindings, long existingOpen,
            long unmatchedIdentity, long existingClosed, long reopened, long returnedUnresolved, long missingFromCompleteSnapshot) { }
}
