package com.company.vapt.ingestion;

import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ImportBatchStage;
import com.company.vapt.ingestion.repository.ImportBatchStageRepository;
import com.company.vapt.ingestion.entity.SourceFile;

import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.StageStatus;
import com.company.vapt.ingestion.repository.*;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.entity.ReportSection;
import com.company.vapt.report.repository.ReportSectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 3 Task 2 - Database Migration V3 & Persistence Tests")
class Phase3Task2PersistenceTest {

    @Autowired
    private ReportSectionRepository reportSectionRepository;

    @Autowired
    private SourceFileRepository sourceFileRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ImportBatchStageRepository importBatchStageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("1 & 2. Flyway V3 seeds exactly the six canonical report sections")
    void testFlywayV3SeededReportSections() {
        List<ReportSection> sections = reportSectionRepository.findAll();
        assertEquals(6, sections.size(), "V3 migration must seed exactly 6 report sections");

        for (ReportSectionId id : ReportSectionId.values()) {
            Optional<ReportSection> sectionOpt = reportSectionRepository.findById(id.name());
            assertTrue(sectionOpt.isPresent(), "Seeded section must exist for canonical ID: " + id.name());
            assertEquals(id.getDisplayName(), sectionOpt.get().getDisplayName());
            assertEquals(id.getReconciliationType(), sectionOpt.get().getReconciliationType());
            assertTrue(sectionOpt.get().isEnabled());
        }
    }

    @Test
    @DisplayName("3. ReportSection entity persists and retrieves correctly")
    void testReportSectionEntityPersistence() {
        ReportSection section = reportSectionRepository.findById(ReportSectionId.GDP_VA_REPORT.name()).orElseThrow();
        section.setDescription("Updated Description for Testing");
        reportSectionRepository.saveAndFlush(section);

        entityManager.clear();
        ReportSection reloaded = reportSectionRepository.findById(ReportSectionId.GDP_VA_REPORT.name()).orElseThrow();
        assertEquals("Updated Description for Testing", reloaded.getDescription());
    }

    @Test
    @DisplayName("4. SourceFile metadata persistence and checksum index lookup")
    void testSourceFilePersistence() {
        UUID fileId = UUID.randomUUID();
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        SourceFile file = new SourceFile(
                fileId,
                "test-vapt-export.xlsx",
                "uploads/GDP_VA_REPORT/" + fileId + ".dat",
                102450L,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ".xlsx",
                sha256,
                "admin@transunion.com"
        );

        sourceFileRepository.saveAndFlush(file);
        entityManager.clear();

        Optional<SourceFile> loaded = sourceFileRepository.findById(fileId);
        assertTrue(loaded.isPresent());
        assertEquals("test-vapt-export.xlsx", loaded.get().getOriginalFilename());
        assertEquals(102450L, loaded.get().getFileSizeBytes());
        assertEquals(sha256, loaded.get().getSha256Checksum());

        Optional<SourceFile> byChecksum = sourceFileRepository.findBySha256Checksum(sha256);
        assertTrue(byChecksum.isPresent());
        assertEquals(fileId, byChecksum.get().getId());
    }

    @Test
    @DisplayName("5. ImportBatch persistence and structured stage durations")
    void testImportBatchAndStagePersistence() {
        ReportSection section = reportSectionRepository.findById(ReportSectionId.WIZ.name()).orElseThrow();
        SourceFile file = createTestFile("wiz-scan.csv");
        sourceFileRepository.saveAndFlush(file);

        ImportBatch batch = new ImportBatch(UUID.randomUUID(), section, file);
        batch.setTotalRows(150);
        batch.setAcceptedRows(148);
        batch.setRejectedRows(2);
        importBatchRepository.saveAndFlush(batch);

        ImportBatchStage stage = new ImportBatchStage(
                batch,
                "VALIDATING",
                StageStatus.COMPLETED,
                245L,
                Instant.now().minusSeconds(1),
                Instant.now(),
                "Validated 150 rows"
        );
        importBatchStageRepository.saveAndFlush(stage);

        entityManager.clear();

        ImportBatch loadedBatch = importBatchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(ImportStatus.RECEIVED, loadedBatch.getStatus());
        assertEquals(150, loadedBatch.getTotalRows());
        assertFalse(loadedBatch.isActive());

        List<ImportBatchStage> stages = importBatchStageRepository.findByBatchIdOrderByStartedAtAsc(batch.getId());
        assertEquals(1, stages.size());
        assertEquals("VALIDATING", stages.get(0).getStage());
        assertEquals(245L, stages.get(0).getDurationMs());
    }

    @Test
    @DisplayName("6. Database foreign-key constraints reject non-existent section reference")
    void testForeignKeyConstraints() {
        SourceFile file = createTestFile("report.csv");
        sourceFileRepository.saveAndFlush(file);

        UUID batchId = UUID.randomUUID();
        Exception ex = assertThrows(Exception.class, () -> {
            entityManager.createNativeQuery(
                    "INSERT INTO import_batch (id, report_section_id, source_file_id, status, is_active, version_number, created_at) " +
                    "VALUES (?, 'NON_EXISTENT_SECTION', ?, 'RECEIVED', false, 0, CURRENT_TIMESTAMP)"
            ).setParameter(1, batchId)
             .setParameter(2, file.getId())
             .executeUpdate();
            entityManager.flush();
        });
        assertTrue(ex.getMessage().contains("FK_IMPORT_BATCH_SECTION") || ex.getCause().getMessage().contains("FK_IMPORT_BATCH_SECTION"));
    }

    @Test
    @DisplayName("7. UUID mapping: Native UUID generation, persistence and retrieval")
    void testUuidMapping() {
        UUID expectedId = UUID.randomUUID();
        ReportSection section = reportSectionRepository.findById(ReportSectionId.VERIZON_26_NETWORK_PT.name()).orElseThrow();
        SourceFile file = createTestFile("verizon26.xlsx");
        sourceFileRepository.saveAndFlush(file);

        ImportBatch batch = new ImportBatch(expectedId, section, file);
        importBatchRepository.saveAndFlush(batch);

        entityManager.clear();
        ImportBatch loaded = importBatchRepository.findById(expectedId).orElseThrow();
        assertEquals(expectedId, loaded.getId());
    }

    @Test
    @DisplayName("8. Optimistic locking: Stale update throws ObjectOptimisticLockingFailureException")
    void testOptimisticLocking() {
        ReportSection section = reportSectionRepository.findById(ReportSectionId.PT_VERIZON_GCP.name()).orElseThrow();
        SourceFile file = createTestFile("pt-gcp.csv");
        sourceFileRepository.saveAndFlush(file);

        ImportBatch batch = new ImportBatch(UUID.randomUUID(), section, file);
        importBatchRepository.saveAndFlush(batch);

        // Fetch first copy and detach it so it holds version 0
        ImportBatch copy1 = importBatchRepository.findById(batch.getId()).orElseThrow();
        entityManager.detach(copy1);

        // Fetch second copy in persistence context and update it -> increments version to 1
        ImportBatch copy2 = importBatchRepository.findById(batch.getId()).orElseThrow();
        copy2.setStatus(ImportStatus.VALIDATING);
        importBatchRepository.saveAndFlush(copy2);

        // Now save copy1 (which still has version 0) -> must trigger optimistic locking failure
        copy1.setStatus(ImportStatus.FAILED);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            importBatchRepository.saveAndFlush(copy1);
        });
    }

    @Test
    @DisplayName("9. Only one active batch per report section enforced at DB level")
    void testSingleActiveBatchPerSectionEnforced() {
        ReportSection section = reportSectionRepository.findById(ReportSectionId.GDP_VA_REPORT.name()).orElseThrow();
        SourceFile file1 = createTestFile("gdp-1.xlsx");
        SourceFile file2 = createTestFile("gdp-2.xlsx");
        sourceFileRepository.saveAndFlush(file1);
        sourceFileRepository.saveAndFlush(file2);

        ImportBatch batch1 = new ImportBatch(UUID.randomUUID(), section, file1);
        batch1.markCommittedAndActive();
        importBatchRepository.saveAndFlush(batch1);

        // Attempting to activate a second batch for GDP_VA_REPORT without deactivating batch1 must fail
        ImportBatch batch2 = new ImportBatch(UUID.randomUUID(), section, file2);
        batch2.markCommittedAndActive();

        assertThrows(DataIntegrityViolationException.class, () -> {
            importBatchRepository.saveAndFlush(batch2);
        });
    }

    @Test
    @DisplayName("10. GDP VA and Internal VA Brinqa active batches remain strictly independent")
    void testGdpAndInternalBrinqaActiveBatchesIndependent() {
        ReportSection gdpSection = reportSectionRepository.findById(ReportSectionId.GDP_VA_REPORT.name()).orElseThrow();
        ReportSection brinqaSection = reportSectionRepository.findById(ReportSectionId.INTERNAL_VA_BRINQA.name()).orElseThrow();

        SourceFile gdpFile = createTestFile("gdp-active.xlsx");
        SourceFile brinqaFile = createTestFile("brinqa-active.xlsx");
        sourceFileRepository.saveAndFlush(gdpFile);
        sourceFileRepository.saveAndFlush(brinqaFile);

        ImportBatch gdpBatch = new ImportBatch(UUID.randomUUID(), gdpSection, gdpFile);
        gdpBatch.markCommittedAndActive();
        importBatchRepository.saveAndFlush(gdpBatch);

        ImportBatch brinqaBatch = new ImportBatch(UUID.randomUUID(), brinqaSection, brinqaFile);
        brinqaBatch.markCommittedAndActive();
        importBatchRepository.saveAndFlush(brinqaBatch);

        // Both sections can have their own active batch concurrently without conflict
        entityManager.clear();
        Optional<ImportBatch> activeGdp = importBatchRepository.findByReportSectionIdAndActiveTrue(ReportSectionId.GDP_VA_REPORT.name());
        Optional<ImportBatch> activeBrinqa = importBatchRepository.findByReportSectionIdAndActiveTrue(ReportSectionId.INTERNAL_VA_BRINQA.name());

        assertTrue(activeGdp.isPresent());
        assertTrue(activeBrinqa.isPresent());
        assertNotEquals(activeGdp.get().getId(), activeBrinqa.get().getId());
        assertEquals(ReportSectionId.GDP_VA_REPORT.name(), activeGdp.get().getActiveSectionId());
        assertEquals(ReportSectionId.INTERNAL_VA_BRINQA.name(), activeBrinqa.get().getActiveSectionId());
    }

    @Test
    @DisplayName("11. Failed batches cannot become active (enforced at DB level)")
    void testFailedBatchesCannotBecomeActive() {
        ReportSection section = reportSectionRepository.findById(ReportSectionId.APPSEC_FINDINGS_PT.name()).orElseThrow();
        SourceFile file = createTestFile("appsec-findings.csv");
        sourceFileRepository.saveAndFlush(file);

        ImportBatch batch = new ImportBatch(UUID.randomUUID(), section, file);
        batch.markFailed("Parsing failed", "Column missing");
        importBatchRepository.saveAndFlush(batch);

        assertFalse(batch.isActive());
        assertNull(batch.getActiveSectionId());

        // Attempting to force is_active = true on a FAILED batch via native query violates CHECK constraint
        assertThrows(Exception.class, () -> {
            entityManager.createNativeQuery(
                    "UPDATE import_batch SET is_active = true, active_section_id = ? WHERE id = ?"
            ).setParameter(1, section.getId())
             .setParameter(2, batch.getId())
             .executeUpdate();
            entityManager.flush();
        });
    }

    private SourceFile createTestFile(String filename) {
        UUID id = UUID.randomUUID();
        return new SourceFile(
                id,
                filename,
                "uploads/" + id + ".dat",
                50000L,
                "text/csv",
                ".csv",
                UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                "tester@transunion.com"
        );
    }
}
