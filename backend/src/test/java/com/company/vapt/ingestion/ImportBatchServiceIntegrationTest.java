package com.company.vapt.ingestion;

import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.audit.domain.AuditEvent;
import com.company.vapt.audit.repository.AuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.company.vapt.ingestion.service.ImportBatchService;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.InvalidStateTransitionException;
import com.company.vapt.ingestion.domain.StageStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.entity.ImportBatchStage;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.report.domain.ReportSectionId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("ImportBatchService - Application Service & Concurrency Tests")
class ImportBatchServiceIntegrationTest {

    @Autowired
    private ImportBatchService batchService;

    @Autowired
    private SourceFileRepository sourceFileRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private EntityManager entityManager;

    private SourceFile createSourceFile(String name) {
        UUID id = UUID.randomUUID();
        SourceFile file = new SourceFile(
                id,
                name,
                "uploads/test/" + id + ".dat",
                1024L,
                "text/csv",
                ".csv",
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                "tester@transunion.com"
        );
        return sourceFileRepository.saveAndFlush(file);
    }

    private long metricCount(String name, String stage, String result) {
        Timer timer = meterRegistry.find(name).tag("stage", stage).tag("result", result).timer();
        return timer != null ? timer.count() : 0;
    }

    @Test
    @Transactional
    @DisplayName("1. Batch starts in correct initial state (RECEIVED, inactive)")
    void testInitialBatchState() {
        SourceFile file = createSourceFile("initial.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.GDP_VA_REPORT, file);

        assertNotNull(batch.getId());
        assertEquals(ImportStatus.RECEIVED, batch.getStatus());
        assertFalse(batch.isActive());
        assertNull(batch.getActiveSectionId());
        assertEquals(0, batch.getTotalRows());
        assertEquals(ReportSectionId.GDP_VA_REPORT.name(), batch.getReportSection().getId());
    }

    @Test
    @Transactional
    @DisplayName("2 & 3. Successful lifecycle completion through all stages with timing persistence")
    void testSuccessfulLifecycleCompletion() {
        SourceFile file = createSourceFile("full-lifecycle.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.WIZ, file);
        UUID batchId = batch.getId();
        long importSuccessMetricBefore = metricCount("import.batch", "IMPORT", "SUCCESS");

        // 1. Start VALIDATING, then complete VALIDATING and advance to STAGING
        long validatingMetricBefore = metricCount("import.stage", "VALIDATING", "COMPLETED");
        batchService.transitionTo(batchId, ImportStatus.VALIDATING);
        batch = batchService.completeStage(batchId, "VALIDATING", ImportStatus.STAGING, 120L, "Validated headers");
        assertEquals(ImportStatus.STAGING, batch.getStatus());
        assertEquals(validatingMetricBefore + 1, metricCount("import.stage", "VALIDATING", "COMPLETED"));

        // 2. Complete STAGING and advance to ENRICHING
        batch = batchService.completeStage(batchId, "STAGING", ImportStatus.ENRICHING, 350L, "Staged 500 rows");
        assertEquals(ImportStatus.ENRICHING, batch.getStatus());

        // 3. Complete ENRICHING and advance to RECONCILING
        batch = batchService.completeStage(batchId, "ENRICHING", ImportStatus.RECONCILING, 410L, "CMDB matched");
        assertEquals(ImportStatus.RECONCILING, batch.getStatus());

        // 4. Record RECONCILING stage and commit/activate
        batchService.recordStageExecution(batchId, "RECONCILING", StageStatus.COMPLETED, 280L, null, null, "Reconciled UIDs");
        ImportBatch committed = batchService.commitAndActivate(batchId);
        assertEquals(ImportStatus.COMMITTED, committed.getStatus());
        assertTrue(committed.isActive());
        assertEquals(ReportSectionId.WIZ.name(), committed.getActiveSectionId());
        assertEquals(importSuccessMetricBefore + 1, metricCount("import.batch", "IMPORT", "SUCCESS"));

        // Verify stage timings
        List<ImportBatchStage> stages = batchService.getBatchStages(batchId);
        assertEquals(4, stages.size());
        assertEquals("VALIDATING", stages.get(0).getStage());
        assertEquals(120L, stages.get(0).getDurationMs());
        assertEquals(StageStatus.COMPLETED, stages.get(0).getStatus());

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> batchId.toString().equals(event.getTargetId()))
                .toList();
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("IMPORT_BATCH_CREATED")));
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("IMPORT_PROCESSING_STARTED")));
        assertEquals(4, events.stream().filter(e -> e.getAction().equals("IMPORT_STAGE_COMPLETED")).count());
        assertTrue(events.stream().anyMatch(e -> e.getAction().equals("IMPORT_COMMITTED_AND_ACTIVATED")));
        assertTrue(events.stream().allMatch(e -> e.getCorrelationId() != null && !e.getCorrelationId().isBlank()));
        assertTrue(events.stream().filter(e -> e.getAction().equals("IMPORT_STAGE_COMPLETED"))
                .allMatch(e -> e.getDetails().contains("section=WIZ") && e.getDetails().contains("durationMs=")));
    }

    @Test
    @Transactional
    @DisplayName("4. Failure handling, diagnostics and failed stage recording")
    void testFailureHandling() {
        SourceFile file = createSourceFile("fail-scan.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.PT_VERIZON_GCP, file);
        batchService.transitionTo(batch.getId(), ImportStatus.VALIDATING);
        long failedStageMetricBefore = metricCount("import.stage", "VALIDATING", "FAILED");
        long failedImportMetricBefore = metricCount("import.batch", "IMPORT", "FAILURE");

        ImportBatch failed = batchService.failBatch(
                batch.getId(),
                "Required header missing: 'Record Id'",
                "Column check failed on sheet 1",
                "VALIDATING",
                95L
        );

        assertEquals(ImportStatus.FAILED, failed.getStatus());
        assertFalse(failed.isActive());
        assertNull(failed.getActiveSectionId());
        assertEquals("Required header missing: 'Record Id'", failed.getErrorMessage());
        assertNotNull(failed.getCompletedAt());

        AuditEvent failedEvent = auditEventRepository.findByActionOrderByTimestampDesc("IMPORT_FAILED").stream()
                .filter(event -> batch.getId().toString().equals(event.getTargetId()))
                .findFirst().orElseThrow();
        assertEquals("SYSTEM", failedEvent.getActorId());
        assertEquals("IMPORT_BATCH", failedEvent.getTargetType());
        assertTrue(failedEvent.getDetails().contains("section=PT_VERIZON_GCP"));
        assertFalse(failedEvent.getDetails().contains("Required header missing"));
        assertFalse(failedEvent.getDetails().contains("Column check failed"));
        assertNotNull(failedEvent.getTimestamp());
        assertEquals(failedStageMetricBefore + 1, metricCount("import.stage", "VALIDATING", "FAILED"));
        assertEquals(failedImportMetricBefore + 1, metricCount("import.batch", "IMPORT", "FAILURE"));

        List<ImportBatchStage> stages = batchService.getBatchStages(batch.getId());
        assertEquals(1, stages.size());
        assertEquals("VALIDATING", stages.get(0).getStage());
        assertEquals(StageStatus.FAILED, stages.get(0).getStatus());
        assertEquals(95L, stages.get(0).getDurationMs());
    }

    @Test
    @Transactional
    @DisplayName("5. Rejection of duplicate stage transitions")
    void testDuplicateStageTransitionRejection() {
        SourceFile file = createSourceFile("dup-stage.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.VERIZON_26_NETWORK_PT, file);
        batchService.transitionTo(batch.getId(), ImportStatus.VALIDATING);

        batchService.recordStageExecution(
                batch.getId(), "VALIDATING", StageStatus.COMPLETED, 100L, null, null, "First validation"
        );

        // Attempting to record the same stage name again must throw ValidationException
        assertThrows(ValidationException.class, () -> {
            batchService.recordStageExecution(
                    batch.getId(), "VALIDATING", StageStatus.COMPLETED, 100L, null, null, "Duplicate validation"
            );
        });
    }

    @Test
    @Transactional
    @DisplayName("Stage names are allowlisted before becoming metric tags")
    void testUnknownStageCannotCreateHighCardinalityMetricTag() {
        SourceFile file = createSourceFile("stage-tag.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.WIZ, file);
        String untrustedStageName = "USER_SUPPLIED_STAGE_" + UUID.randomUUID();

        assertThrows(ValidationException.class, () -> batchService.recordStageExecution(
                batch.getId(), untrustedStageName, StageStatus.COMPLETED,
                10L, null, null, "details"));
        assertNull(meterRegistry.find("import.stage")
                .tag("stage", untrustedStageName)
                .timer());
    }

    @Test
    @Transactional
    @DisplayName("6 & 7. Previous active batch remains active while new batch processes, then atomically switches on commit")
    void testAtomicActiveBatchSwitching() {
        SourceFile file1 = createSourceFile("batch1.xlsx");
        SourceFile file2 = createSourceFile("batch2.xlsx");

        // 1. First batch completes and commits -> becomes active
        ImportBatch batch1 = batchService.createBatch(ReportSectionId.GDP_VA_REPORT, file1);
        batchService.transitionTo(batch1.getId(), ImportStatus.VALIDATING);
        batchService.transitionTo(batch1.getId(), ImportStatus.STAGING);
        batchService.transitionTo(batch1.getId(), ImportStatus.ENRICHING);
        batchService.transitionTo(batch1.getId(), ImportStatus.RECONCILING);
        batchService.commitAndActivate(batch1.getId());

        Optional<ImportBatch> activeInitial = batchService.getActiveBatch(ReportSectionId.GDP_VA_REPORT);
        assertTrue(activeInitial.isPresent());
        assertEquals(batch1.getId(), activeInitial.get().getId());

        // 2. Second batch is created and starts processing -> batch1 MUST REMAIN active
        ImportBatch batch2 = batchService.createBatch(ReportSectionId.GDP_VA_REPORT, file2);
        batchService.transitionTo(batch2.getId(), ImportStatus.VALIDATING);
        batchService.transitionTo(batch2.getId(), ImportStatus.STAGING);

        Optional<ImportBatch> activeWhileProcessing = batchService.getActiveBatch(ReportSectionId.GDP_VA_REPORT);
        assertTrue(activeWhileProcessing.isPresent());
        assertEquals(batch1.getId(), activeWhileProcessing.get().getId(), "Batch 1 must remain active while Batch 2 processes");

        // 3. Second batch advances and commits -> batch1 is deactivated, batch2 is activated
        batchService.transitionTo(batch2.getId(), ImportStatus.ENRICHING);
        batchService.transitionTo(batch2.getId(), ImportStatus.RECONCILING);
        batchService.commitAndActivate(batch2.getId());

        ImportBatch reloadedBatch1 = batchService.getBatch(batch1.getId());
        ImportBatch reloadedBatch2 = batchService.getBatch(batch2.getId());

        assertFalse(reloadedBatch1.isActive());
        assertNull(reloadedBatch1.getActiveSectionId());
        assertTrue(reloadedBatch2.isActive());
        assertEquals(ReportSectionId.GDP_VA_REPORT.name(), reloadedBatch2.getActiveSectionId());

        Optional<ImportBatch> finalActive = batchService.getActiveBatch(ReportSectionId.GDP_VA_REPORT);
        assertTrue(finalActive.isPresent());
        assertEquals(batch2.getId(), finalActive.get().getId());
    }

    @Test
    @Transactional
    @DisplayName("8. Failed batch cannot become active")
    void testFailedBatchCannotBecomeActive() {
        SourceFile file = createSourceFile("failed-test.csv");
        ImportBatch batch = batchService.createBatch(ReportSectionId.APPSEC_FINDINGS_PT, file);
        batchService.transitionTo(batch.getId(), ImportStatus.VALIDATING);
        batchService.failBatch(batch.getId(), "Corrupted row", null, "VALIDATING", 50L);

        // Attempting to commit/activate a FAILED batch throws InvalidStateTransitionException
        assertThrows(InvalidStateTransitionException.class, () -> {
            batchService.commitAndActivate(batch.getId());
        });

        assertFalse(batchService.getBatch(batch.getId()).isActive());
        assertTrue(batchService.getActiveBatch(ReportSectionId.APPSEC_FINDINGS_PT).isEmpty());
    }

    @Test
    @Transactional
    @DisplayName("9. GDP VA Report and Internal VA Brinqa maintain strictly independent active batches")
    void testGdpAndInternalBrinqaIndependentActiveBatches() {
        SourceFile gdpFile = createSourceFile("gdp.csv");
        SourceFile brinqaFile = createSourceFile("brinqa.csv");

        // Commit GDP batch
        ImportBatch gdpBatch = batchService.createBatch(ReportSectionId.GDP_VA_REPORT, gdpFile);
        batchService.transitionTo(gdpBatch.getId(), ImportStatus.VALIDATING);
        batchService.transitionTo(gdpBatch.getId(), ImportStatus.STAGING);
        batchService.transitionTo(gdpBatch.getId(), ImportStatus.ENRICHING);
        batchService.transitionTo(gdpBatch.getId(), ImportStatus.RECONCILING);
        batchService.commitAndActivate(gdpBatch.getId());

        // Commit Brinqa batch
        ImportBatch brinqaBatch = batchService.createBatch(ReportSectionId.INTERNAL_VA_BRINQA, brinqaFile);
        batchService.transitionTo(brinqaBatch.getId(), ImportStatus.VALIDATING);
        batchService.transitionTo(brinqaBatch.getId(), ImportStatus.STAGING);
        batchService.transitionTo(brinqaBatch.getId(), ImportStatus.ENRICHING);
        batchService.transitionTo(brinqaBatch.getId(), ImportStatus.RECONCILING);
        batchService.commitAndActivate(brinqaBatch.getId());

        // Both sections have independent active snapshots
        Optional<ImportBatch> gdpActive = batchService.getActiveBatch(ReportSectionId.GDP_VA_REPORT);
        Optional<ImportBatch> brinqaActive = batchService.getActiveBatch(ReportSectionId.INTERNAL_VA_BRINQA);

        assertTrue(gdpActive.isPresent());
        assertTrue(brinqaActive.isPresent());
        assertNotEquals(gdpActive.get().getId(), brinqaActive.get().getId());
        assertEquals(ReportSectionId.GDP_VA_REPORT.name(), gdpActive.get().getActiveSectionId());
        assertEquals(ReportSectionId.INTERNAL_VA_BRINQA.name(), brinqaActive.get().getActiveSectionId());
    }

    @Test
    @DisplayName("10 & 11. Concurrent activation attempts for same and different sections")
    void testConcurrentActivationBehavior() throws InterruptedException {
        // Prepare batches for the same section (WIZ)
        SourceFile fileA = createSourceFile("concurrentA.csv");
        SourceFile fileB = createSourceFile("concurrentB.csv");
        ImportBatch batchA = null;
        ImportBatch batchB = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            batchA = batchService.createBatch(ReportSectionId.WIZ, fileA);
            batchService.transitionTo(batchA.getId(), ImportStatus.VALIDATING);
            batchService.transitionTo(batchA.getId(), ImportStatus.STAGING);
            batchService.transitionTo(batchA.getId(), ImportStatus.ENRICHING);
            batchService.transitionTo(batchA.getId(), ImportStatus.RECONCILING);
            UUID batchAId = batchA.getId();

            batchB = batchService.createBatch(ReportSectionId.WIZ, fileB);
            batchService.transitionTo(batchB.getId(), ImportStatus.VALIDATING);
            batchService.transitionTo(batchB.getId(), ImportStatus.STAGING);
            batchService.transitionTo(batchB.getId(), ImportStatus.ENRICHING);
            batchService.transitionTo(batchB.getId(), ImportStatus.RECONCILING);
            UUID batchBId = batchB.getId();

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    batchService.commitAndActivate(batchAId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
            executor.submit(() -> {
                try {
                    startLatch.await();
                    batchService.commitAndActivate(batchBId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS));

            // Exactly ONE batch must be active for WIZ
            Optional<ImportBatch> activeWiz = batchService.getActiveBatch(ReportSectionId.WIZ);
            assertTrue(activeWiz.isPresent(), "One active batch must exist for WIZ");
            assertTrue(activeWiz.get().getId().equals(batchA.getId()) || activeWiz.get().getId().equals(batchB.getId()));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
            if (batchA != null) importBatchRepository.deleteById(batchA.getId());
            if (batchB != null) importBatchRepository.deleteById(batchB.getId());
            sourceFileRepository.deleteById(fileA.getId());
            sourceFileRepository.deleteById(fileB.getId());
        }

        assertFalse(sourceFileRepository.existsById(fileA.getId()), "Concurrent test must remove source file A metadata");
        assertFalse(sourceFileRepository.existsById(fileB.getId()), "Concurrent test must remove source file B metadata");
        if (batchA != null) assertFalse(importBatchRepository.existsById(batchA.getId()), "Concurrent test must remove batch A");
        if (batchB != null) assertFalse(importBatchRepository.existsById(batchB.getId()), "Concurrent test must remove batch B");
    }
}
