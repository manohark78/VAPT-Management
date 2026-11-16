package com.company.vapt.ingestion;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.report.controller.ReportSectionController;
import com.company.vapt.ingestion.controller.ImportController;
import com.company.vapt.report.domain.ReconciliationType;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.vapt.audit.domain.AuditEvent;
import com.company.vapt.audit.repository.AuditEventRepository;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.ingestion.service.ImportBatchService;
import com.company.vapt.cmdb.service.CmdbManagementService;
import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;
import com.company.vapt.finding.repository.FindingObservationRepository;
import com.company.vapt.ingestion.repository.ImportBatchStageRepository;
import com.company.vapt.ingestion.service.ReportRecordPersistenceService;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.List;
import java.io.ByteArrayOutputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ImportController and ReportSectionController.
 * Tests run against in-memory H2 with the test profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportControllerIntegrationTest {

    private static final Path TEST_STORAGE_ROOT = createTestStorageRoot();

    private static Path createTestStorageRoot() {
        try {
            return Files.createTempDirectory("vapt-import-controller-test-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void configureTemporaryStorage(DynamicPropertyRegistry registry) {
        registry.add("vapt.storage.root-dir", () -> TEST_STORAGE_ROOT.toString());
    }

    @AfterAll
    static void removeTemporaryStorage() throws IOException {
        if (Files.exists(TEST_STORAGE_ROOT)) {
            try (var paths = Files.walk(TEST_STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to clean test storage path " + path, e);
                    }
                });
            }
        }
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(TEST_STORAGE_ROOT),
                "Temporary upload storage must be removed after the test class");
    }

    @AfterEach
    void cleanGeneratedUploadFiles() throws IOException {
        if (Files.exists(TEST_STORAGE_ROOT)) {
            try (var paths = Files.walk(TEST_STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { throw new RuntimeException("Failed to clean test upload", e); }
                });
            }
        }
    }

    @BeforeEach
    void ensureActiveCmdbVersionsForSuccessfulGeneralReportImports() {
        ensureDataset("HEADCOUNT", "Preferred Name,Associated's Email,CM4,CM5,CM6\nAlice,alice@example.test,Org4,Org5,Org6\n");
        ensureDataset("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID,Name,Public Facing Application\nweb01,10.20.30.40,web01.example.test,TransUnion Portal,Production,Alice,APP-1,Web 01,Yes\n");
        ensureDataset("APP_INVENTORY", "ApplicationId,Plan Application Owner 1,Plan Application Owner 2\nAPP-1,Owner One,Owner Two\n");
    }

    private void ensureDataset(String key, String csv) {
        if (cmdbDatasetRepository.findById(key).orElseThrow().getActiveVersionId() != null) return;
        MockMultipartFile file = new MockMultipartFile("file", key.toLowerCase() + ".csv", "text/csv", csv.getBytes());
        var draft = cmdbManagementService.beginUpload(key, file, "admin@transunion.com");
        @SuppressWarnings("unchecked") List<java.util.Map<String, Object>> candidates = (List<java.util.Map<String, Object>>) draft.get("candidates");
        java.util.Map<String, Object> candidate = candidates.getFirst();
        @SuppressWarnings("unchecked") java.util.Map<String, Integer> mapping = (java.util.Map<String, Integer>) candidate.get("suggestedMapping");
        var completed = cmdbManagementService.confirm(UUID.fromString(draft.get("id").toString()),
                ((Number) candidate.get("sheetIndex")).intValue(), ((Number) candidate.get("headerRow")).intValue(),
                mapping, "admin@transunion.com");
        assertEquals("READY", completed.get("status"));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private SourceFileRepository sourceFileRepository;

    @MockitoSpyBean
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private ReportImportRecordRepository reportImportRecordRepository;

    @Autowired
    private FindingObservationRepository findingObservationRepository;

    @Autowired
    private ImportBatchStageRepository importBatchStageRepository;

    @MockitoSpyBean
    private ReportRecordPersistenceService recordPersistenceService;

    @MockitoSpyBean
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ImportBatchService importBatchService;

    @Autowired private CmdbManagementService cmdbManagementService;
    @Autowired private CmdbDatasetRepository cmdbDatasetRepository;

    // ---------------------------------------------------------------------------
    // Helper: build a valid CSV multipart file
    // ---------------------------------------------------------------------------
    private MockMultipartFile csvFile(String paramName) {
        byte[] content = "uid,hostname,severity\n001,server1,HIGH\n002,server2,MEDIUM\n".getBytes();
        return new MockMultipartFile(paramName, "findings.csv", "text/csv", content);
    }

    // ---------------------------------------------------------------------------
    // Helper: build a fake SourceFile entity (bypasses real storage)
    // ---------------------------------------------------------------------------
    private SourceFile savedSourceFile(String section) {
        SourceFile sf = new SourceFile(
                UUID.randomUUID(),
                "findings.csv",
                section.toLowerCase() + "/" + UUID.randomUUID() + ".dat",
                512L,
                "text/csv",
                ".csv",
                "abc123def456abc123def456abc123def456abc123def456abc123def456abcd",
                "test-user@transunion.com"
        );
        return sourceFileRepository.save(sf);
    }

    // ---------------------------------------------------------------------------
    // Test 1: Unauthenticated access to section list → 403 (session-based auth;
    //         no AuthenticationEntryPoint returns 401 for anonymous JSON requests)
    // ---------------------------------------------------------------------------
    @Test
    void unauthenticatedUserCannotListSections() throws Exception {
        mockMvc.perform(get("/api/reports/sections"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------
    // Test 2: USER cannot list report sections while section-level grants are undefined.
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "user@transunion.com", roles = {"USER"})
    void userCannotListSectionsUntilSectionPermissionsAreDefined() throws Exception {
        mockMvc.perform(get("/api/reports/sections"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user@transunion.com", roles = {"USER"})
    void userCannotReadImportHistoryDetailsOrActivePointerWithoutSectionGrant() throws Exception {
        SourceFile source = savedSourceFile("GDP_VA_REPORT");
        ImportBatch batch = importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, source);

        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports/active"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports/" + batch.getId()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------
    // Test 3: Admin can get a specific section by ID
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void adminCanGetSectionById() throws Exception {
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("GDP_VA_REPORT"))
                .andExpect(jsonPath("$.data.reconciliationType").value("UID_BASED"));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void adminCanListAllSixSections() throws Exception {
        mockMvc.perform(get("/api/reports/sections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(6)));
    }

    // ---------------------------------------------------------------------------
    // Test 4: Invalid section ID → 404
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void invalidSectionIdReturns404() throws Exception {
        mockMvc.perform(get("/api/reports/sections/NONEXISTENT_SECTION"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---------------------------------------------------------------------------
    // Test 5: Non-admin cannot upload (403)
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "user@transunion.com", roles = {"USER"})
    void nonAdminCannotUploadFile() throws Exception {
        MockMultipartFile file = csvFile("file");
        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------
    // Test 6: Admin can upload a valid CSV file → 201 with batch DTO
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = {"ADMIN"})
    void adminCanUploadValidCsvFile() throws Exception {
        MockMultipartFile file = csvFile("file");
        String correlationId = "task6-upload-" + UUID.randomUUID();
        long uploadMetricBefore = metricCount("import.upload", "UPLOAD", "SUCCESS");
        long apiMetricBefore = metricCount("api.request", "HTTP", "SUCCESS");
        var result = mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(file)
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.reportSectionId").value("GDP_VA_REPORT"))
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.acceptedRows").value(2))
                .andExpect(jsonPath("$.data.originalFilename").value("findings.csv"))
                .andReturn();

        UUID batchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id"));
        AppUser admin = appUserRepository.findByEmail("manohar.kaloni@transunion.com").orElseThrow();
        List<AuditEvent> requestEvents = auditEventRepository.findAll().stream()
                .filter(event -> correlationId.equals(event.getCorrelationId()))
                .toList();
        assertEquals(15, requestEvents.size());
        AuditEvent uploaded = requestEvents.stream().filter(e -> e.getAction().equals("SOURCE_FILE_UPLOADED")).findFirst().orElseThrow();
        AuditEvent created = requestEvents.stream().filter(e -> e.getAction().equals("IMPORT_BATCH_CREATED")).findFirst().orElseThrow();
        assertEquals(String.valueOf(admin.getId()), uploaded.getActorId());
        assertEquals("ADMIN", uploaded.getActorType());
        assertEquals("SOURCE_FILE", uploaded.getTargetType());
        assertTrue(uploaded.getDetails().contains("section=GDP_VA_REPORT"));
        assertTrue(uploaded.getDetails().contains("batchId=" + batchId));
        assertNotNull(uploaded.getTimestamp());
        assertEquals(String.valueOf(admin.getId()), created.getActorId());
        assertEquals("IMPORT_BATCH", created.getTargetType());
        assertEquals(batchId.toString(), created.getTargetId());
        assertEquals(correlationId, created.getCorrelationId());
        assertNotNull(created.getTimestamp());
        assertTrue(requestEvents.stream().anyMatch(e -> e.getAction().equals("REPORT_ENRICHMENT_STARTED")));
        assertTrue(requestEvents.stream().anyMatch(e -> e.getAction().equals("REPORT_ENRICHMENT_COMPLETED")));
        assertTrue(requestEvents.stream().anyMatch(e -> e.getAction().equals("FINDING_RECONCILIATION_COMPLETED")));
        assertEquals(uploadMetricBefore + 1, metricCount("import.upload", "UPLOAD", "SUCCESS"));
        assertEquals(apiMetricBefore + 1, metricCount("api.request", "HTTP", "SUCCESS"));
        List<ReportImportRecord> rows = reportImportRecordRepository.findByImportBatchIdOrderBySourceRowNumber(batchId);
        assertEquals(2, rows.size());
        assertEquals("001", rows.get(0).getSourceIdentity());
        assertTrue(rows.get(0).getSourceValues().contains("\"Severity\":\"HIGH\""));
        assertTrue(importBatchStageRepository.findByBatchIdOrderByIdAsc(batchId).stream()
                .anyMatch(stage -> "ENRICHING".equals(stage.getStage()) && stage.getStatus().name().equals("COMPLETED")));
        assertTrue(importBatchStageRepository.findByBatchIdOrderByIdAsc(batchId).stream()
                .anyMatch(stage -> "RECONCILING".equals(stage.getStage()) && stage.getStatus().name().equals("COMPLETED")));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sourceFileMetadataPersistenceFailureDeletesStoredFileAndLeavesNoRows() throws Exception {
        long sourceCountBefore = sourceFileRepository.count();
        long batchCountBefore = importBatchRepository.count();
        long storedFilesBefore = countStoredFiles();
        doThrow(new IllegalStateException("forced source metadata failure"))
                .when(sourceFileRepository).save(any(SourceFile.class));

        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(csvFile("file"))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());

        org.junit.jupiter.api.Assertions.assertEquals(sourceCountBefore, sourceFileRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(batchCountBefore, importBatchRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(storedFilesBefore, countStoredFiles());
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void auditPersistenceFailureIsVisibleAndRollsBackUpload() throws Exception {
        long sourceCountBefore = sourceFileRepository.count();
        long batchCountBefore = importBatchRepository.count();
        long auditCountBefore = auditEventRepository.count();
        long storedFilesBefore = countStoredFiles();
        long uploadFailureBefore = metricCount("import.upload", "UPLOAD", "FAILURE");
        long apiFailureBefore = metricCount("api.request", "HTTP", "SERVER_ERROR");
        String correlationId = "task6-audit-failure-" + UUID.randomUUID();
        doAnswer(invocation -> {
            AuditEvent event = invocation.getArgument(0);
            if ("SOURCE_FILE_UPLOADED".equals(event.getAction())) {
                throw new IllegalStateException("forced second audit persistence failure");
            }
            return invocation.callRealMethod();
        }).when(auditEventRepository).save(any(AuditEvent.class));

        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(csvFile("file"))
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId)
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId));

        assertEquals(sourceCountBefore, sourceFileRepository.count());
        assertEquals(batchCountBefore, importBatchRepository.count());
        assertEquals(auditCountBefore, auditEventRepository.count());
        assertEquals(storedFilesBefore, countStoredFiles());
        assertEquals(uploadFailureBefore + 1, metricCount("import.upload", "UPLOAD", "FAILURE"));
        assertEquals(apiFailureBefore + 1, metricCount("api.request", "HTTP", "SERVER_ERROR"));
        assertFalse(auditEventRepository.findAll().stream().anyMatch(e -> correlationId.equals(e.getCorrelationId())));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void uploadedReportContentsAreNotWrittenToApplicationLogs() throws Exception {
        String secretCell = "SENSITIVE_REPORT_CELL_" + UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "private-findings.csv", "text/csv",
                ("uid,target\n" + secretCell + ",10.20.30.40\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Logger applicationLogger = (Logger) LoggerFactory.getLogger("com.company.vapt");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        applicationLogger.addAppender(appender);
        try {
            mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                            .file(file)
                            .with(csrf()))
                    .andExpect(status().isCreated());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(secretCell)));
        } finally {
            applicationLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void batchPersistenceFailureRollsBackSourceMetadataAndDeletesStoredFile() throws Exception {
        long sourceCountBefore = sourceFileRepository.count();
        long batchCountBefore = importBatchRepository.count();
        long storedFilesBefore = countStoredFiles();
        doThrow(new IllegalStateException("forced batch persistence failure"))
                .when(importBatchRepository).save(any(ImportBatch.class));

        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(csvFile("file"))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());

        org.junit.jupiter.api.Assertions.assertEquals(sourceCountBefore, sourceFileRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(batchCountBefore, importBatchRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(storedFilesBefore, countStoredFiles());
    }

    private long countStoredFiles() throws IOException {
        if (!Files.exists(TEST_STORAGE_ROOT)) {
            return 0;
        }
        try (var paths = Files.walk(TEST_STORAGE_ROOT)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private long metricCount(String name, String stage, String result) {
        Timer timer = meterRegistry.find(name).tag("stage", stage).tag("result", result).timer();
        return timer != null ? timer.count() : 0;
    }

    // ---------------------------------------------------------------------------
    // Test 7: Upload to invalid section → 404
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void uploadToInvalidSectionReturns404() throws Exception {
        MockMultipartFile file = csvFile("file");
        mockMvc.perform(multipart("/api/reports/sections/FAKE_SECTION/imports")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // Test 8: Upload empty file → 400
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void uploadEmptyFileReturns400() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(emptyFile)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Test 9: Upload unsupported file type (e.g. .txt renamed) → 400
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void uploadUnsupportedExtensionReturns400() throws Exception {
        byte[] content = "some text".getBytes();
        MockMultipartFile badFile = new MockMultipartFile("file", "malware.exe", "application/octet-stream", content);
        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(badFile)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void importsRealXlsAndXlsxWorkbooksWithHeaderRowsAndBlankCells() throws Exception {
        for (String extension : List.of("xls", "xlsx")) {
            MockMultipartFile file = workbookFile("file", extension);
            var result = mockMvc.perform(multipart("/api/reports/sections/WIZ/imports")
                            .file(file).with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                    .andExpect(jsonPath("$.data.acceptedRows").value(2))
                    .andReturn();
            UUID batchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                    result.getResponse().getContentAsString(), "$.data.id"));
            List<ReportImportRecord> rows = reportImportRecordRepository.findByImportBatchIdOrderBySourceRowNumber(batchId);
            assertEquals(2, rows.size());
            assertEquals("WIZ-001", rows.get(0).getSourceIdentity());
            assertEquals("000042", rows.get(1).getSourceIdentity());
            assertTrue(rows.get(0).getSourceValues().contains("\"Risk Rating\":\"High\""));
            assertTrue(rows.get(0).getSourceValues().contains("\"AppTag\":\"Weak TLS Cipher\""));
            assertTrue(rows.get(1).getSourceValues().contains("\"Display Name\":\"\""));
        }
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void missingIdentityHeaderFailsWithoutActivatingOrLeavingRows() throws Exception {
        MockMultipartFile invalid = new MockMultipartFile("file", "invalid.csv", "text/csv",
                "hostname,severity\nserver1,HIGH\n".getBytes());
        mockMvc.perform(multipart("/api/reports/sections/INTERNAL_VA_BRINQA/imports")
                        .file(invalid).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.errorMessage").value(containsString("UID column is required")));
        assertTrue(importBatchRepository.findByReportSectionIdAndActiveTrue("INTERNAL_VA_BRINQA").isEmpty());
        assertEquals(0, reportImportRecordRepository.count());
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void appSecUsesFindingIdAndDoesNotSubstituteRecordId() throws Exception {
        MockMultipartFile wrongIdentity = new MockMultipartFile("file", "appsec.csv", "text/csv",
                "Record Id,Title,Severity\nREC-1,Finding,HIGH\n".getBytes());
        var failed = mockMvc.perform(multipart("/api/reports/sections/APPSEC_FINDINGS_PT/imports")
                        .file(wrongIdentity).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn();
        UUID failedBatchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                failed.getResponse().getContentAsString(), "$.data.id"));
        mockMvc.perform(get("/api/reports/sections/APPSEC_FINDINGS_PT/imports/" + failedBatchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorDetails").value(containsString("IDENTITY_HEADER_MISSING")));

        MockMultipartFile correctIdentity = new MockMultipartFile("file", "appsec.csv", "text/csv",
                "Finding ID,Record Id,Title,Severity\nF-100,REC-1,Finding,HIGH\n".getBytes());
        var result = mockMvc.perform(multipart("/api/reports/sections/APPSEC_FINDINGS_PT/imports")
                        .file(correctIdentity).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andReturn();
        UUID batchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id"));
        List<ReportImportRecord> rows = reportImportRecordRepository.findByImportBatchIdOrderBySourceRowNumber(batchId);
        assertEquals("F-100", rows.get(0).getSourceIdentity());
        assertTrue(rows.get(0).getSourceValues().contains("\"Record Id\":\"REC-1\""));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void duplicateIdentityRowsAreCommittedForIdentityResolution() throws Exception {
        MockMultipartFile duplicate = new MockMultipartFile("file", "duplicates.csv", "text/csv",
                "UID,Severity\nDUP-1,HIGH\nDUP-1,LOW\n".getBytes());
        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(duplicate).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.acceptedRows").value(2));
        UUID batchId = importBatchRepository.findByReportSectionIdAndActiveTrue("GDP_VA_REPORT").orElseThrow().getId();
        assertEquals(2, findingObservationRepository.countByImportBatchId(batchId));
        assertTrue(findingObservationRepository.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(batchId)
                .stream().allMatch(row -> row.getClassification().equals("UNMATCHED_IDENTITY")
                        && row.getIdentityIssue().equals("DUPLICATE_UID")));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void largeCsvPersistsInBoundedChunksAndPreservesEveryRecord() throws Exception {
        StringBuilder csv = new StringBuilder("UID,Severity,Display Name\n");
        for (int i = 0; i < 600; i++) csv.append("LARGE-").append(i).append(",High,Finding ").append(i).append('\n');
        MockMultipartFile file = new MockMultipartFile("file", "large.csv", "text/csv", csv.toString().getBytes());
        var result = mockMvc.perform(multipart("/api/reports/sections/WIZ/imports").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.totalRows").value(600))
                .andExpect(jsonPath("$.data.acceptedRows").value(600))
                .andReturn();
        UUID batchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.id"));
        assertEquals(600, reportImportRecordRepository.countByImportBatchId(batchId));
        ArgumentCaptor<List> chunks = ArgumentCaptor.forClass(List.class);
        verify(recordPersistenceService, times(3)).saveChunk(eq(batchId), chunks.capture());
        assertEquals(600, chunks.getAllValues().stream().mapToInt(List::size).sum());
        assertTrue(chunks.getAllValues().stream().allMatch(chunk -> chunk.size() <= 250));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void blankGeneralUidIsRetainedForIdentityResolution() throws Exception {
        MockMultipartFile invalidRow = new MockMultipartFile("file", "invalid-row.csv", "text/csv",
                "UID,Severity\n,HIGH\n".getBytes());
        mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports").file(invalidRow).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.acceptedRows").value(1));
        UUID batchId = importBatchRepository.findByReportSectionIdAndActiveTrue("GDP_VA_REPORT").orElseThrow().getId();
        var observation = findingObservationRepository.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(batchId).getFirst();
        assertEquals("UNMATCHED_IDENTITY", observation.getClassification());
        assertEquals("MISSING_UID", observation.getIdentityIssue());
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void failedReplacementLeavesPreviousActiveImportIntact() throws Exception {
        var successful = mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(csvFile("file")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andReturn();
        UUID activeBatchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                successful.getResponse().getContentAsString(), "$.data.id"));

        MockMultipartFile invalid = new MockMultipartFile("file", "invalid.csv", "text/csv",
                "UID,Severity\nVALID,HIGH,UNMAPPED\n".getBytes());
        var failed = mockMvc.perform(multipart("/api/reports/sections/GDP_VA_REPORT/imports")
                        .file(invalid).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andReturn();
        UUID failedBatchId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
                failed.getResponse().getContentAsString(), "$.data.id"));

        assertEquals(activeBatchId, importBatchRepository.findByReportSectionIdAndActiveTrue("GDP_VA_REPORT").orElseThrow().getId());
        assertEquals(2, reportImportRecordRepository.countByImportBatchId(activeBatchId));
        assertEquals(0, reportImportRecordRepository.countByImportBatchId(failedBatchId));
    }

    private MockMultipartFile workbookFile(String parameter, String extension) throws IOException {
        try (Workbook workbook = extension.equals("xlsx") ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Weekly Findings");
            var heading = sheet.createRow(2);
            heading.createCell(0).setCellValue("UID");
            heading.createCell(1).setCellValue("Risk Rating");
            heading.createCell(2).setCellValue("Display Name");
            var first = sheet.createRow(3);
            first.createCell(0).setCellValue("WIZ-001");
            first.createCell(1).setCellValue("High");
            first.createCell(2).setCellValue("Weak TLS Cipher");
            var second = sheet.createRow(4);
            var numericId = second.createCell(0);
            numericId.setCellValue(42);
            var idStyle = workbook.createCellStyle();
            idStyle.setDataFormat(workbook.createDataFormat().getFormat("000000"));
            numericId.setCellStyle(idStyle);
            second.createCell(1).setCellValue("Medium");
            workbook.write(output);
            return new MockMultipartFile(parameter, "findings." + extension,
                    extension.equals("xlsx") ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/vnd.ms-excel",
                    output.toByteArray());
        }
    }

    // Test 10: Import history is paginated and scoped to a section
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void importHistoryIsReturnedPaginated() throws Exception {
        // Seed two batches for GDP_VA_REPORT
        SourceFile sf1 = savedSourceFile("GDP_VA_REPORT");
        SourceFile sf2 = savedSourceFile("GDP_VA_REPORT");
        SourceFile sf3 = savedSourceFile("GDP_VA_REPORT");
        importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, sf1);
        importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, sf2);
        importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, sf3);

        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.pageNumber").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void importBatchReadEndpointsLoadDtoMetadataOutsideRequestTransaction() throws Exception {
        SourceFile source = savedSourceFile("GDP_VA_REPORT");
        ImportBatch batch = importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, source);
        UUID batchId = batch.getId();
        try {
            importBatchService.transitionTo(batchId, ImportStatus.VALIDATING);
            importBatchService.transitionTo(batchId, ImportStatus.STAGING);
            importBatchService.transitionTo(batchId, ImportStatus.ENRICHING);
            importBatchService.transitionTo(batchId, ImportStatus.RECONCILING);
            importBatchService.commitAndActivate(batchId);

            mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports")
                            .param("status", "COMMITTED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id == '" + batchId + "')].originalFilename")
                            .value(hasItem("findings.csv")));

            mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports/active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(batchId.toString()))
                    .andExpect(jsonPath("$.data.originalFilename").value("findings.csv"));

            mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports/" + batchId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.originalFilename").value("findings.csv"));
        } finally {
            importBatchStageRepository.deleteAll(importBatchStageRepository.findByBatchIdOrderByIdAsc(batchId));
            auditEventRepository.deleteAll(auditEventRepository.findAll().stream()
                    .filter(event -> batchId.toString().equals(event.getTargetId())
                            || source.getId().toString().equals(event.getTargetId()))
                    .toList());
            importBatchRepository.deleteById(batchId);
            sourceFileRepository.deleteById(source.getId());
        }
    }

    // ---------------------------------------------------------------------------
    // Test 11: Cross-section isolation — GDP and Brinqa batches are independent
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void batchHistoryIsSectionIsolated() throws Exception {
        SourceFile gdpFile = savedSourceFile("GDP_VA_REPORT");
        SourceFile brinqaFile = savedSourceFile("INTERNAL_VA_BRINQA");
        importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, gdpFile);
        importBatchService.createBatch(ReportSectionId.INTERNAL_VA_BRINQA, brinqaFile);

        // GDP section should only return its own batch
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reportSectionId").value("GDP_VA_REPORT"));

        // Brinqa section should only return its own batch
        mockMvc.perform(get("/api/reports/sections/INTERNAL_VA_BRINQA/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].reportSectionId").value("INTERNAL_VA_BRINQA"));
    }

    // ---------------------------------------------------------------------------
    // Test 12: Get batch detail by ID
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void getBatchDetailReturnsFullDetail() throws Exception {
        SourceFile sf = savedSourceFile("PT_VERIZON_GCP");
        ImportBatch batch = importBatchService.createBatch(ReportSectionId.PT_VERIZON_GCP, sf);

        mockMvc.perform(get("/api/reports/sections/PT_VERIZON_GCP/imports/" + batch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.data.reportSectionId").value("PT_VERIZON_GCP"))
                .andExpect(jsonPath("$.data.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data.stages").isArray())
                // sha256Checksum included in detail
                .andExpect(jsonPath("$.data.sha256Checksum").isNotEmpty())
                // Physical storage path must NOT be exposed
                .andExpect(jsonPath("$.data.storagePath").doesNotExist());
    }

    // ---------------------------------------------------------------------------
    // Test 13: IDOR protection — batch from different section → 404
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void batchFromDifferentSectionReturns404() throws Exception {
        SourceFile sf = savedSourceFile("GDP_VA_REPORT");
        ImportBatch batch = importBatchService.createBatch(ReportSectionId.GDP_VA_REPORT, sf);

        // Request the GDP batch under INTERNAL_VA_BRINQA section → should be 404
        mockMvc.perform(get("/api/reports/sections/INTERNAL_VA_BRINQA/imports/" + batch.getId()))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------------
    // Test 14: Get active batch when none exists → data is null, 200
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void getActiveBatchWhenNoneExistsReturnsNullData() throws Exception {
        mockMvc.perform(get("/api/reports/sections/GDP_VA_REPORT/imports/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void getActiveBatchReturnsCommittedBatch() throws Exception {
        SourceFile source = savedSourceFile("WIZ");
        ImportBatch batch = importBatchService.createBatch(ReportSectionId.WIZ, source);
        importBatchService.transitionTo(batch.getId(), ImportStatus.VALIDATING);
        importBatchService.transitionTo(batch.getId(), ImportStatus.STAGING);
        importBatchService.transitionTo(batch.getId(), ImportStatus.ENRICHING);
        importBatchService.transitionTo(batch.getId(), ImportStatus.RECONCILING);
        importBatchService.commitAndActivate(batch.getId());

        mockMvc.perform(get("/api/reports/sections/WIZ/imports/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(batch.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("COMMITTED"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    // ---------------------------------------------------------------------------
    // Test 15: Status filter on import history
    // Verifies that the status filter only returns batches of the requested status.
    // Uses an exact fixture count; test transactions roll back between methods.
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void importHistoryFiltersByStatus() throws Exception {
        SourceFile sf = savedSourceFile("VERIZON_26_NETWORK_PT");
        SourceFile validatingFile = savedSourceFile("VERIZON_26_NETWORK_PT");
        importBatchService.createBatch(ReportSectionId.VERIZON_26_NETWORK_PT, sf);
        ImportBatch validating = importBatchService.createBatch(ReportSectionId.VERIZON_26_NETWORK_PT, validatingFile);
        importBatchService.transitionTo(validating.getId(), ImportStatus.VALIDATING);

        // Exact counts make unexpected fixture leakage visible.
        mockMvc.perform(get("/api/reports/sections/VERIZON_26_NETWORK_PT/imports")
                        .param("status", "RECEIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].status").value("RECEIVED"));

        // The other known batch has exactly the requested status.
        mockMvc.perform(get("/api/reports/sections/VERIZON_26_NETWORK_PT/imports")
                        .param("status", "VALIDATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].status").value("VALIDATING"));
    }

    // ---------------------------------------------------------------------------
    // Test 16: Invalid status filter → 400
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void importHistoryWithInvalidStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/sections/WIZ/imports")
                        .param("status", "BOGUS_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Test 17: Response must not expose physical storage path
    // ---------------------------------------------------------------------------
    @Test
    @WithMockUser(username = "admin@transunion.com", roles = {"ADMIN"})
    void importHistoryResponseDoesNotExposeStoragePath() throws Exception {
        SourceFile sf = savedSourceFile("PT_VERIZON_GCP");
        importBatchService.createBatch(ReportSectionId.PT_VERIZON_GCP, sf);

        mockMvc.perform(get("/api/reports/sections/PT_VERIZON_GCP/imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].storagePath").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].originalFilename").value("findings.csv"));
    }
}
