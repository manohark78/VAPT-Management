package com.company.vapt.enrichment.service;

import com.company.vapt.cmdb.repository.CmdbRecordRepository;
import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;

import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.common.observability.PerformanceTimer;
import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.enrichment.entity.ReportRecordEnrichment;
import com.company.vapt.ingestion.service.ImportProcessingException;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Runs version-pinned matching in bounded report-row chunks. */
@Service
public class ReportToCmdbEnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(ReportToCmdbEnrichmentService.class);
    private static final String UNIDENTIFIED = "Unidentified";
    private final EnrichmentRunCoordinator coordinator;
    private final EnrichmentRunRepository runs;
    private final ReportImportRecordRepository reportRecords;
    private final CmdbRecordRepository cmdbRecords;
    private final CmdbLookupIndexService indexService;
    private final CmdbLookupResolver lookupResolver;
    private final CmdbLookupNormalizer normalizer;
    private final HostnameNormalizer hostnameNormalizer;
    private final TargetClassifier targetClassifier;
    private final EnrichmentResultPersistenceService resultPersistence;
    private final EnrichmentRunFinalizer finalizer;
    private final PerformanceTimer performanceTimer;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper json = JsonMapper.builder().build();
    private final int chunkSize;

    public ReportToCmdbEnrichmentService(EnrichmentRunCoordinator coordinator, EnrichmentRunRepository runs,
            ReportImportRecordRepository reportRecords,
            CmdbRecordRepository cmdbRecords, CmdbLookupIndexService indexService,
            CmdbLookupResolver lookupResolver, CmdbLookupNormalizer normalizer,
            HostnameNormalizer hostnameNormalizer, TargetClassifier targetClassifier,
            EnrichmentResultPersistenceService resultPersistence, EnrichmentRunFinalizer finalizer,
            PerformanceTimer performanceTimer, MeterRegistry meterRegistry,
            @Value("${vapt.enrichment.batch-size:250}") int chunkSize) {
        this.coordinator = coordinator; this.runs = runs; this.reportRecords = reportRecords;
        this.cmdbRecords = cmdbRecords; this.indexService = indexService;
        this.lookupResolver = lookupResolver; this.normalizer = normalizer;
        this.hostnameNormalizer = hostnameNormalizer; this.targetClassifier = targetClassifier;
        this.resultPersistence = resultPersistence; this.finalizer = finalizer;
        this.performanceTimer = performanceTimer; this.meterRegistry = meterRegistry;
        this.chunkSize = Math.max(1, Math.min(1000, chunkSize));
    }

    /** Called by the import state machine while the batch is at ENRICHING. */
    public EnrichmentRun enrichNewImport(UUID batchId, String sectionId, String actor) {
        EnrichmentRun run = coordinator.start(batchId, sectionId, actor, false, false);
        if ("FAILED".equals(run.getStatus())) {
            countRun(sectionId, "FAILURE");
            throw new ImportProcessingException("ENRICHING", run.getDiagnosticCode(), run.getDiagnosticMessage());
        }
        execute(run);
        EnrichmentRun completed = runs.findById(run.getId()).orElseThrow();
        if (!"COMPLETED".equals(completed.getStatus())) {
            throw new ImportProcessingException("ENRICHING", completed.getDiagnosticCode(), completed.getDiagnosticMessage());
        }
        return completed;
    }

    /** Admin-only backfill/rerun entry point; it never changes the report import's active pointer. */
    public EnrichmentRun enrichCommittedImport(UUID batchId, String sectionId, String actor, boolean rerun) {
        EnrichmentRun run = coordinator.start(batchId, sectionId, actor, true, rerun);
        if ("FAILED".equals(run.getStatus())) {
            countRun(sectionId, "FAILURE");
            return run;
        }
        try { execute(run); }
        catch (ImportProcessingException expectedFailure) { /* failure details are persisted on the run */ }
        return runs.findById(run.getId()).orElseThrow();
    }

    private void execute(EnrichmentRun run) {
        String stage = "JAS_MATCH";
        Map<String, Long> durations = new LinkedHashMap<>();
        long[] stageStarted = {System.nanoTime()};
        long overallStarted = System.nanoTime();
        String failedStage = null;
        try {
            indexService.ensureIndexed(run.getJasVersionId());
            indexService.ensureIndexed(run.getHeadcountVersionId());
            indexService.ensureIndexed(run.getAppInventoryVersionId());
            int lastRow = 0;
            while (true) {
                List<ReportImportRecord> sourceChunk = reportRecords
                        .findByImportBatchIdAndSourceRowNumberGreaterThanOrderBySourceRowNumberAsc(run.getImportBatch().getId(), lastRow,
                                org.springframework.data.domain.PageRequest.of(0, chunkSize));
                if (sourceChunk.isEmpty()) break;
                lastRow = sourceChunk.getLast().getSourceRowNumber();
                List<SourceContext> contexts = sourceChunk.stream().map(this::context).toList();

                stage = "JAS_MATCH";
                Map<String, CmdbLookupResolver.MatchSet> ips = resolveTargets(run.getJasVersionId(), "IP Address", contexts, "IP");
                Map<String, CmdbLookupResolver.MatchSet> fqdns = resolveTargets(run.getJasVersionId(), "FQDN", contexts, "FQDN");
                Map<String, CmdbLookupResolver.MatchSet> hosts = resolveFallbackHosts(run.getJasVersionId(), contexts);
                List<Work> work = new ArrayList<>(contexts.size());
                for (SourceContext context : contexts) work.add(resolveJas(context, ips, fqdns, hosts));
                Map<UUID, CmdbRecord> jasData = loadRecords(work, "jas");
                for (Work item : work) if (item.jasMatch.uniqueCandidate() != null) {
                    item.jas = decodeRecord(jasData.get(item.jasMatch.uniqueCandidate().recordId()));
                }
                accumulate(durations, "JAS_MATCH", stageStarted);

                stage = "HEADCOUNT_RESOLUTION";
                Set<String> ownerValues = new HashSet<>();
                for (Work item : work) {
                    String owner = field(item.jas, "OwnedBy");
                    item.ownerReference = normalizer.normalize("Preferred Name", owner);
                    if (item.ownerReference != null) ownerValues.add(item.ownerReference);
                }
                Map<String, CmdbLookupResolver.MatchSet> owners = lookupResolver.resolve(
                        run.getHeadcountVersionId(), "Preferred Name", ownerValues);
                for (Work item : work) item.ownerMatch = item.ownerReference == null
                        ? CmdbLookupResolver.MatchSet.unmatched() : owners.getOrDefault(item.ownerReference, CmdbLookupResolver.MatchSet.unmatched());
                Map<UUID, CmdbRecord> headcountData = loadRecords(work, "owner");
                for (Work item : work) if (item.ownerMatch.uniqueCandidate() != null) {
                    item.headcount = decodeRecord(headcountData.get(item.ownerMatch.uniqueCandidate().recordId()));
                }
                accumulate(durations, "HEADCOUNT_RESOLUTION", stageStarted);

                stage = "APPLICATION_RESOLUTION";
                Set<String> appIds = new HashSet<>();
                for (Work item : work) {
                    String appId = field(item.jas, "Application ID");
                    item.applicationReference = normalizer.normalize("ApplicationId", appId);
                    if (item.applicationReference != null) appIds.add(item.applicationReference);
                }
                Map<String, CmdbLookupResolver.MatchSet> applications = lookupResolver.resolve(
                        run.getAppInventoryVersionId(), "ApplicationId", appIds);
                for (Work item : work) item.applicationMatch = item.applicationReference == null
                        ? CmdbLookupResolver.MatchSet.unmatched() : applications.getOrDefault(item.applicationReference, CmdbLookupResolver.MatchSet.unmatched());
                Map<UUID, CmdbRecord> appData = loadRecords(work, "application");
                for (Work item : work) if (item.applicationMatch.uniqueCandidate() != null) {
                    item.applicationInventory = decodeRecord(appData.get(item.applicationMatch.uniqueCandidate().recordId()));
                }
                accumulate(durations, "APPLICATION_RESOLUTION", stageStarted);

                List<ReportRecordEnrichment> output = work.stream().map(item -> toEntity(run, item)).toList();
                long matched = work.stream().filter(w -> "MATCHED".equals(w.jasMatch.status())).count();
                long ambiguous = work.stream().filter(w -> "AMBIGUOUS".equals(w.jasMatch.status())).count();
                long unmatched = work.size() - matched - ambiguous;
                resultPersistence.saveChunk(run.getId(), output, matched, unmatched, ambiguous);
                countRows(run.getReportSectionId(), matched, unmatched, ambiguous);
                stageStarted[0] = System.nanoTime();
            }
            finalizer.complete(run.getId(), durations);
            countRun(run.getReportSectionId(), "SUCCESS");
            performanceTimer.recordDuration("import.cmdb_enrichment", "TOTAL", "SUCCESS",
                    Duration.ofNanos(Math.max(0, System.nanoTime() - overallStarted)));
        } catch (Exception failure) {
            failedStage = stage;
            long elapsedMs = Math.max(0, (System.nanoTime() - stageStarted[0]) / 1_000_000);
            durations.merge(stage, elapsedMs, Long::sum);
            performanceTimer.recordDuration("import.cmdb_enrichment", stage, "FAILURE", Duration.ofMillis(elapsedMs));
            String code = "ENRICHMENT_PROCESSING_FAILED";
            String safeMessage = "Report-to-CMDB enrichment failed; the previous active report import was retained.";
            try { finalizer.fail(run.getId(), code, safeMessage); }
            catch (Exception auditFailure) {
                try { finalizer.markFailedWithoutAudit(run.getId(), "ENRICHMENT_AUDIT_PERSISTENCE_FAILED", safeMessage); }
                catch (Exception statusFailure) { /* safe error is emitted below; do not mask the workflow failure */ }
                log.error("Enrichment failure audit could not be persisted: runId={} correlationId={}",
                        run.getId(), run.getCorrelationId());
            }
            countRun(run.getReportSectionId(), "FAILURE");
            performanceTimer.recordDuration("import.cmdb_enrichment", "TOTAL", "FAILURE",
                    Duration.ofNanos(Math.max(0, System.nanoTime() - overallStarted)));
            Throwable root = failure;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            String failureFrames = java.util.Arrays.stream(failure.getStackTrace()).limit(8)
                    .map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" <- "));
            log.error("Report enrichment failed: runId={} section={} stage={} diagnostic={} failureType={} rootType={} correlationId={}",
                    run.getId(), run.getReportSectionId(), stage, code, failure.getClass().getSimpleName(),
                    root.getClass().getSimpleName(), run.getCorrelationId());
            log.error("Enrichment failure frames: {}", failureFrames);
            throw new ImportProcessingException("ENRICHING", code, safeMessage, failure);
        } finally {
            for (Map.Entry<String, Long> duration : durations.entrySet()) {
                performanceTimer.recordDuration("import.cmdb_enrichment.stage", duration.getKey(),
                        duration.getKey().equals(failedStage) ? "FAILURE" : "SUCCESS",
                        Duration.ofMillis(duration.getValue()));
            }
        }
    }

    private Map<String, CmdbLookupResolver.MatchSet> resolveTargets(UUID versionId, String field,
            List<SourceContext> contexts, String targetType) {
        Set<String> values = new HashSet<>();
        for (SourceContext context : contexts) if (targetType.equals(context.targetType)) {
            String normalized = normalizer.normalize(field, context.target);
            if (normalized != null) values.add(normalized);
        }
        return lookupResolver.resolve(versionId, field, values);
    }

    private Map<String, CmdbLookupResolver.MatchSet> resolveFallbackHosts(UUID versionId, List<SourceContext> contexts) {
        Set<String> values = new HashSet<>();
        for (SourceContext context : contexts) {
            if ("FQDN".equals(context.targetType)) {
                String normalized = hostnameNormalizer.registrableHost(context.target);
                if (normalized != null) values.add(normalizer.normalize("Host", normalized));
            } else if ("HOSTNAME".equals(context.targetType)) {
                String normalized = normalizer.normalize("Host", context.target);
                if (normalized != null) values.add(normalized);
            }
        }
        return lookupResolver.resolve(versionId, "Host", values);
    }

    private Work resolveJas(SourceContext context, Map<String, CmdbLookupResolver.MatchSet> ips,
                            Map<String, CmdbLookupResolver.MatchSet> fqdns,
                            Map<String, CmdbLookupResolver.MatchSet> hosts) {
        Work work = new Work(context);
        if (context.target == null || context.target.isBlank()) {
            work.jasMatch = CmdbLookupResolver.MatchSet.unmatched();
            work.reason = "MISSING_TARGET";
            return work;
        }
        String key;
        if ("IP".equals(context.targetType)) {
            key = normalizer.normalize("IP Address", context.target);
            work.jasMatch = ips.getOrDefault(key, CmdbLookupResolver.MatchSet.unmatched());
            if (!"UNMATCHED".equals(work.jasMatch.status())) work.matchMethod = "EXACT_IP";
        } else if ("FQDN".equals(context.targetType)) {
            key = normalizer.normalize("FQDN", context.target);
            work.jasMatch = fqdns.getOrDefault(key, CmdbLookupResolver.MatchSet.unmatched());
            if (!"UNMATCHED".equals(work.jasMatch.status())) work.matchMethod = "EXACT_FQDN";
            if ("UNMATCHED".equals(work.jasMatch.status())) {
                String fallback = hostnameNormalizer.registrableHost(context.target);
                if (fallback != null) {
                    work.jasMatch = hosts.getOrDefault(normalizer.normalize("Host", fallback), CmdbLookupResolver.MatchSet.unmatched());
                    if (!"UNMATCHED".equals(work.jasMatch.status())) work.matchMethod = "CONFIGURED_FQDN_TO_HOST";
                }
            }
        } else {
            key = normalizer.normalize("Host", context.target);
            work.jasMatch = hosts.getOrDefault(key, CmdbLookupResolver.MatchSet.unmatched());
            if (!"UNMATCHED".equals(work.jasMatch.status())) work.matchMethod = "EXACT_HOST";
        }
        if ("UNMATCHED".equals(work.jasMatch.status())) work.reason = "NO_ASSET_MATCH";
        else if ("AMBIGUOUS".equals(work.jasMatch.status())) work.reason = "MULTIPLE_ASSET_MATCHES";
        return work;
    }

    private Map<UUID, CmdbRecord> loadRecords(List<Work> work, String which) {
        Set<UUID> ids = new HashSet<>();
        for (Work item : work) {
            CmdbLookupResolver.MatchSet match = switch (which) {
                case "jas" -> item.jasMatch;
                case "owner" -> item.ownerMatch;
                default -> item.applicationMatch;
            };
            if (match != null && match.uniqueCandidate() != null) ids.add(match.uniqueCandidate().recordId());
        }
        Map<UUID, CmdbRecord> result = new HashMap<>();
        if (!ids.isEmpty()) for (CmdbRecord record : cmdbRecords.findAllById(ids)) result.put(record.getId(), record);
        return result;
    }

    private SourceContext context(ReportImportRecord record) {
        Map<String, String> values = readStringMap(record.getSourceValues());
        String target = values.get("Targets");
        return new SourceContext(record, values, target, targetClassifier.classify(target));
    }

    private ReportRecordEnrichment toEntity(EnrichmentRun run, Work item) {
        Map<String, Object> jas = item.jas;
        String appName = field(jas, "Tu_AppName");
        String applicationNameStatus = "MATCHED".equals(item.jasMatch.status())
                ? (appName == null || appName.isBlank() ? "MISSING_APPLICATION_NAME" : "AVAILABLE") : null;
        String display = "MATCHED".equals(item.jasMatch.status()) ? appName : UNIDENTIFIED;
        Map<String, Object> enriched = new LinkedHashMap<>();
        enriched.put("jas", jas == null ? null : jasValues(jas, appName));
        enriched.put("headcount", item.headcount == null ? null : select(item.headcount,
                "Preferred Name", "Associated's Email", "CM4", "CM5", "CM6"));
        enriched.put("applicationInventory", item.applicationInventory == null ? null : select(item.applicationInventory,
                "ApplicationId", "Plan Application Owner 1", "Plan Application Owner 2"));
        enriched.put("applicationDisplay", display);
        enriched.put("applicationNameStatus", applicationNameStatus);
        enriched.put("targetType", item.context.targetType);
        enriched.put("targetMatchStatus", item.jasMatch.status());
        enriched.put("matchReason", item.reason);
        enriched.put("matchMethod", item.matchMethod);
        enriched.put("ownerResolutionStatus", resolutionStatus(item.ownerMatch, item.jasMatch, item.ownerReference));
        enriched.put("applicationResolutionStatus", resolutionStatus(item.applicationMatch, item.jasMatch, item.applicationReference));

        Map<String, Object> candidates = new LinkedHashMap<>();
        candidates.put("jasCandidateCount", item.jasMatch.candidateCount());
        candidates.put("jasCandidateSourceRows", sourceRows(item.jasMatch.candidates()));
        candidates.put("ownerCandidateCount", item.ownerMatch == null ? 0 : item.ownerMatch.candidateCount());
        candidates.put("ownerCandidateSourceRows", item.ownerMatch == null ? List.of() : sourceRows(item.ownerMatch.candidates()));
        candidates.put("applicationCandidateCount", item.applicationMatch == null ? 0 : item.applicationMatch.candidateCount());
        candidates.put("applicationCandidateSourceRows", item.applicationMatch == null ? List.of() : sourceRows(item.applicationMatch.candidates()));
        try {
            return new ReportRecordEnrichment(run, item.context.record, 0, item.context.target,
                    item.context.targetType, item.jasMatch.status(), item.reason, item.matchMethod, display,
                    applicationNameStatus, resolutionStatus(item.ownerMatch, item.jasMatch, item.ownerReference),
                    resolutionStatus(item.applicationMatch, item.jasMatch, item.applicationReference),
                    json.writeValueAsString(enriched), json.writeValueAsString(candidates));
        } catch (Exception e) { throw new IllegalStateException("Could not serialize an enrichment result", e); }
    }

    private String resolutionStatus(CmdbLookupResolver.MatchSet resolution, CmdbLookupResolver.MatchSet jasMatch,
                                   String reference) {
        if (!"MATCHED".equals(jasMatch.status())) return "NOT_ATTEMPTED";
        if (reference == null || reference.isBlank()) return "MISSING_REFERENCE";
        return resolution.status();
    }

    private List<Integer> sourceRows(List<CmdbLookupResolver.Candidate> candidates) {
        return candidates.stream().map(CmdbLookupResolver.Candidate::sourceRowNumber).toList();
    }

    private Map<String, String> readStringMap(String value) {
        try { return json.readValue(value, json.getTypeFactory().constructMapType(Map.class, String.class, String.class)); }
        catch (Exception e) { throw new IllegalStateException("Validated report row could not be read", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeRecord(CmdbRecord record) {
        if (record == null) return null;
        try {
            Map<String, Object> payload = json.readValue(record.getValuesJson(),
                    json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            Object fields = payload.get("fields");
            Object source = payload.get("source");
            Map<String, Object> result = new LinkedHashMap<>();
            if (fields instanceof Map<?, ?> map) map.forEach((k, v) -> result.put(Objects.toString(k), v));
            if (source instanceof Map<?, ?> map) result.put("_source", map);
            result.put("_sourceRowNumber", record.getSourceRowNumber());
            return result;
        } catch (Exception e) { throw new IllegalStateException("Pinned CMDB row could not be read", e); }
    }

    private String field(Map<String, Object> data, String name) {
        if (data == null) return null;
        Object value = data.get(name);
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private String sourceField(Map<String, Object> data, String name) {
        if (data == null) return null;
        Object source = data.get("_source");
        if (!(source instanceof Map<?, ?> values)) return null;
        String expected = normalizeHeader(name);
        for (var entry : values.entrySet()) {
            if (normalizeHeader(Objects.toString(entry.getKey())).equals(expected))
                return entry.getValue() == null ? null : entry.getValue().toString();
        }
        return null;
    }

    private String normalizeHeader(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private Map<String, Object> jasValues(Map<String, Object> data, String appName) {
        Map<String, Object> selected = new LinkedHashMap<>();
        selected.put("Name", sourceField(data, "Name"));
        selected.put("Host", field(data, "Host"));
        selected.put("IP Address", field(data, "IP Address"));
        selected.put("FQDN", field(data, "FQDN"));
        selected.put("Tu_AppName", appName);
        selected.put("Environment", field(data, "Environment"));
        selected.put("OwnedBy", field(data, "OwnedBy"));
        selected.put("Application ID", field(data, "Application ID"));
        selected.put("Public Facing Application", sourceField(data, "Public Facing Application"));
        return selected;
    }

    private Map<String, Object> select(Map<String, Object> source, String... fields) {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String field : fields) selected.put(field, field(source, field));
        return selected;
    }

    private void accumulate(Map<String, Long> durations, String stage, long[] started) {
        long millis = Math.max(0, (System.nanoTime() - started[0]) / 1_000_000);
        durations.merge(stage, millis, Long::sum);
        started[0] = System.nanoTime();
    }

    private void countRows(String section, long matched, long unmatched, long ambiguous) {
        increment("MATCHED", section, matched); increment("UNMATCHED", section, unmatched); increment("AMBIGUOUS", section, ambiguous);
    }
    private void countRun(String section, String outcome) {
        meterRegistry.counter("import.enrichment.runs", "section", lowCardinalitySection(section), "result", outcome).increment();
    }
    private void increment(String result, String section, long amount) {
        if (amount > 0) meterRegistry.counter("import.enrichment.rows", "section", lowCardinalitySection(section), "result", result).increment(amount);
    }
    private String lowCardinalitySection(String section) {
        return Set.of("GDP_VA_REPORT", "INTERNAL_VA_BRINQA", "WIZ", "VERIZON_26_NETWORK_PT", "PT_VERIZON_GCP").contains(section)
                ? section : "OTHER";
    }

    private record SourceContext(ReportImportRecord record, Map<String, String> sourceValues, String target, String targetType) { }
    private static final class Work {
        private final SourceContext context;
        private CmdbLookupResolver.MatchSet jasMatch = CmdbLookupResolver.MatchSet.unmatched();
        private CmdbLookupResolver.MatchSet ownerMatch = CmdbLookupResolver.MatchSet.unmatched();
        private CmdbLookupResolver.MatchSet applicationMatch = CmdbLookupResolver.MatchSet.unmatched();
        private Map<String, Object> jas;
        private Map<String, Object> headcount;
        private Map<String, Object> applicationInventory;
        private String ownerReference;
        private String applicationReference;
        private String reason;
        private String matchMethod;
        private Work(SourceContext context) { this.context = context; }
    }
}
