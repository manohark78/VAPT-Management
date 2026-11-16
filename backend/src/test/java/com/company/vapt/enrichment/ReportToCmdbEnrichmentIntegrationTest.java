package com.company.vapt.enrichment;

import com.company.vapt.finding.entity.Finding;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.company.vapt.cmdb.service.CmdbManagementService;
import com.company.vapt.cmdb.domain.CmdbDatasetType;
import com.company.vapt.cmdb.entity.CmdbDataset;
import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.cmdb.repository.CmdbRecordLookupRepository;
import com.company.vapt.cmdb.entity.CmdbVersion;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;
import com.company.vapt.enrichment.service.EnrichmentRunCoordinator;
import com.company.vapt.enrichment.service.CmdbLookupIndexService;
import com.company.vapt.enrichment.entity.EnrichmentRun;
import com.company.vapt.enrichment.repository.EnrichmentRunRepository;
import com.company.vapt.enrichment.entity.ReportRecordEnrichment;
import com.company.vapt.enrichment.repository.ReportRecordEnrichmentRepository;
import com.company.vapt.finding.repository.FindingRepository;
import com.company.vapt.finding.repository.FindingObservationRepository;
import com.company.vapt.finding.repository.FindingEventRepository;
import com.company.vapt.finding.service.FindingReconciliationWorker;
import com.company.vapt.finding.service.FindingWorkspaceService;
import com.company.vapt.finding.service.ReportExportService;
import com.company.vapt.enrichment.service.EnrichmentResultPersistenceService;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.ingestion.service.ImportBatchService;
import com.company.vapt.ingestion.service.ReportRecordPersistenceService;
import com.company.vapt.ingestion.domain.ImportStatus;
import com.company.vapt.ingestion.domain.StageStatus;
import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.ImportBatchStageRepository;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;
import com.company.vapt.ingestion.entity.SourceFile;
import com.company.vapt.ingestion.repository.SourceFileRepository;
import com.company.vapt.report.domain.ReportSectionId;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportToCmdbEnrichmentIntegrationTest {
    private static final String ADMIN = "admin@transunion.com";
    private static final Path STORAGE_ROOT = createRoot();

    private static Path createRoot() {
        try { return Files.createTempDirectory("vapt-enrichment-it-"); }
        catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vapt.storage.root-dir", () -> STORAGE_ROOT.toString());
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:vapt_enrichment_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("vapt.enrichment.hostname-normalization.suffixes", () -> "com,co.uk");
    }

    @AfterEach
    void cleanStorageAndDatabase() throws IOException {
        jdbc.update("UPDATE cmdb_dataset SET active_version_id = NULL");
        jdbc.update("UPDATE cmdb_version SET active = FALSE, active_dataset_key = NULL");
        jdbc.update("DELETE FROM report_record_enrichment");
        jdbc.update("DELETE FROM enrichment_run");
        jdbc.update("DELETE FROM finding_rca");
        jdbc.update("DELETE FROM finding_event");
        jdbc.update("DELETE FROM finding_observation");
        jdbc.update("DELETE FROM finding_reconciliation_run");
        jdbc.update("DELETE FROM finding");
        jdbc.update("DELETE FROM report_import_record");
        jdbc.update("DELETE FROM import_batch_stage");
        jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM source_file");
        jdbc.update("DELETE FROM cmdb_lookup_index_state");
        jdbc.update("DELETE FROM cmdb_record_lookup");
        jdbc.update("DELETE FROM cmdb_record_field_profile");
        jdbc.update("DELETE FROM cmdb_record");
        jdbc.update("DELETE FROM cmdb_version");
        jdbc.update("DELETE FROM audit_event");
        removeTree(STORAGE_ROOT);
    }

    @AfterAll
    static void cleanRoot() throws IOException { removeTree(STORAGE_ROOT); assertFalse(Files.exists(STORAGE_ROOT)); }

    private static void removeTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException e) { throw new RuntimeException("Could not clean test storage", e); }
            });
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CmdbManagementService cmdb;
    @Autowired CmdbDatasetRepository datasets;
    @Autowired CmdbVersionRepository versions;
    @Autowired ImportBatchRepository batches;
    @Autowired ImportBatchService batchService;
    @Autowired SourceFileRepository sourceFiles;
    @Autowired ReportRecordPersistenceService sourceWriter;
    @Autowired ReportImportRecordRepository reportRecords;
    @Autowired ImportBatchStageRepository stages;
    @Autowired EnrichmentRunRepository runs;
    @Autowired ReportRecordEnrichmentRepository enriched;
    @Autowired FindingRepository findings;
    @Autowired FindingWorkspaceService findingWorkspace;
    @Autowired ReportExportService reportExport;
    @Autowired FindingObservationRepository observations;
    @Autowired FindingEventRepository findingEvents;
    @Autowired EnrichmentRunCoordinator runCoordinator;
    @Autowired MeterRegistry meters;
    @Autowired AppUserRepository users;
    @MockitoSpyBean EnrichmentResultPersistenceService resultWriter;
    @MockitoSpyBean CmdbLookupIndexService lookupIndex;
    @MockitoSpyBean FindingReconciliationWorker reconciliationWorker;

    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = "ADMIN")
    void persistsExactIpAndFqdnEnrichmentWithSourceAndVersionProvenance() throws Exception {
        UUID headcount = importCmdb("HEADCOUNT", "Preferred Name,Associated's Email,CM4,CM5,CM6\nAlice,alice@example.test,Org-4,Org-5,Org-6\n");
        UUID jas = importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID,Name,Public Facing Application\nweb01,10.1.2.3,web01.example.test,Portal,Prod,Alice,APP-1,Web One,External\n");
        UUID app = importCmdb("APP_INVENTORY", "ApplicationId,Plan Application Owner 1,Plan Application Owner 2\nAPP-1,Owner One,Owner Two\n");
        String correlation = "enrichment-" + UUID.randomUUID();

        UUID batch = upload("GDP_VA_REPORT", "UID,Targets,Display Name,Severity\nF-1,10.1.2.3,Finding One,High\nF-2,web01.example.test,Finding Two,Medium\n", correlation);
        EnrichmentRun run = runFor(batch);
        assertEquals("COMPLETED", run.getStatus());
        assertEquals(jas, run.getJasVersionId()); assertEquals(headcount, run.getHeadcountVersionId());
        assertEquals(app, run.getAppInventoryVersionId());
        assertEquals(2, run.getProcessedRows()); assertEquals(2, run.getMatchedRows());
        assertEquals(0, run.getUnmatchedRows()); assertEquals(0, run.getAmbiguousRows());
        assertEquals(correlation, run.getCorrelationId());

        List<ReportRecordEnrichment> rows = results(run.getId());
        assertEquals(List.of("10.1.2.3", "web01.example.test"), rows.stream().map(ReportRecordEnrichment::getOriginalTarget).toList());
        assertTrue(rows.stream().allMatch(row -> row.getTargetMatchStatus().equals("MATCHED")));
        assertTrue(rows.stream().allMatch(row -> row.getOwnerResolutionStatus().equals("MATCHED")));
        assertTrue(rows.stream().allMatch(row -> row.getApplicationResolutionStatus().equals("MATCHED")));
        assertTrue(rows.stream().allMatch(row -> row.getMatchMethod().equals("EXACT_IP") || row.getMatchMethod().equals("EXACT_FQDN")));
        assertTrue(rows.getFirst().getEnrichedValuesJson().contains("Public Facing Application"));
        assertTrue(rows.getFirst().getEnrichedValuesJson().contains("alice@example.test"));
        assertTrue(rows.getFirst().getEnrichedValuesJson().contains("Owner Two"));
        assertTrue(reportRecords.findByImportBatchIdOrderBySourceRowNumber(batch).getFirst().getSourceValues().contains("10.1.2.3"));
        assertTrue(stages.findByBatchIdOrderByIdAsc(batch).stream().anyMatch(s -> s.getStage().equals("ENRICHING") && s.getStatus() == StageStatus.COMPLETED));
        assertTrue(meters.find("import.cmdb_enrichment.stage").tag("stage", "JAS_MATCH").tag("result", "SUCCESS").timer().count() > 0);
        assertTrue(meters.find("import.enrichment.rows").tag("section", "GDP_VA_REPORT").tag("result", "MATCHED").counter().count() >= 2);
        assertEquals(1, runs.findByImportBatchIdOrderByStartedAtDesc(batch, org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        mvc.perform(get("/api/reports/sections/GDP_VA_REPORT/data").param("page", "0").param("size", "25")
                        .param("sort", "riskRating").param("direction", "asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/reports/sections/GDP_VA_REPORT/dashboard"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.openFindings").value(2))
                .andExpect(jsonPath("$.data.severities.High").value(1));
        mvc.perform(get("/api/reports/sections/GDP_VA_REPORT/comments/groups"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void appliesConfiguredFqdnToHostFallbackWithoutChangingOriginalTarget() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nabcd,10.2.0.1,,Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        UUID batch = upload("WIZ", "UID,Targets,Display Name\nW-1,www.abcd.com,Finding\n", "fallback-" + UUID.randomUUID());
        ReportRecordEnrichment row = results(runFor(batch).getId()).getFirst();
        assertEquals("www.abcd.com", row.getOriginalTarget());
        assertEquals("MATCHED", row.getTargetMatchStatus());
        assertEquals("CONFIGURED_FQDN_TO_HOST", row.getMatchMethod());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void userDashboardAndFindingsHonorAssetScopeAndHideOutOfScopeIds() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.20.30.40,web01.test,TransUnion Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        UUID batch=upload("WIZ","UID,Targets,Display Name\nSCOPED-1,10.20.30.40,In scope\nOUTSIDE-1,198.51.100.9,Out of scope\n","scope-"+UUID.randomUUID());
        assertEquals(ImportStatus.COMMITTED,batches.findById(batch).orElseThrow().getStatus());
        UUID outsideFinding=findings.findByReportSectionIdAndSourceUidIn("WIZ",List.of("OUTSIDE-1")).getFirst().getId();
        var user=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("app.owner@transunion.com").roles("USER");
        mvc.perform(get("/api/reports/sections/WIZ/data").with(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].sourceUid").value("SCOPED-1"));
        mvc.perform(get("/api/reports/sections/WIZ/dashboard").with(user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.openFindings").value(1));
        mvc.perform(get("/api/findings/"+outsideFinding+"/history").with(user))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = "ADMIN")
    void completeSnapshotMarksMissingThenRequiresAdminClosureAndSupportsAsOfAndRca() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.20.30.40,web01.test,TransUnion Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        upload("WIZ", "UID,Targets\nLIFE-1,10.20.30.40\n", "life-first-"+UUID.randomUUID());
        UUID snapshot=upload("WIZ", "UID,Targets\nOTHER-1,10.20.30.40\n", "life-snapshot-"+UUID.randomUUID(),true);
        UUID findingId=findings.findByReportSectionIdAndSourceUidIn("WIZ",List.of("LIFE-1")).getFirst().getId();
        var missing=observations.findFirstByFindingIdAndClassificationOrderByObservedAtDesc(findingId,
                "MISSING_FROM_COMPLETE_SNAPSHOT").orElseThrow();
        assertTrue(batches.findById(snapshot).orElseThrow().isCompleteSnapshot());
        assertEquals("OPEN",findings.findById(findingId).orElseThrow().getLifecycleState(),"A missing marker alone must not close a finding");

        Instant beforeClose=Instant.now();
        mvc.perform(post("/api/admin/findings/"+missing.getFinding().getId()+"/close").with(csrf())
                        .contentType("application/json").content("{\"reason\":\"Confirmed absent in complete snapshot\"}"))
                .andExpect(status().isOk());
        Instant afterClose=findings.findById(findingId).orElseThrow().getClosedAt().plusMillis(1);
        var auth=new org.springframework.security.authentication.TestingAuthenticationToken(
                "manohar.kaloni@transunion.com","", "ROLE_ADMIN");
        var before= findingWorkspace.history("WIZ",auth,beforeClose,0,100).content().stream()
                .filter(row->"LIFE-1".equals(row.sourceUid())).findFirst().orElseThrow();
        var after= findingWorkspace.history("WIZ",auth,afterClose,0,100).content().stream()
                .filter(row->"LIFE-1".equals(row.sourceUid())).findFirst().orElseThrow();
        assertEquals("OPEN",before.lifecycleState());
        assertEquals("CLOSED",after.lifecycleState());

        UUID reopenedBatch=upload("WIZ", "UID,Targets\nLIFE-1,10.20.30.40\n", "life-reopen-"+UUID.randomUUID());
        var reopened=observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(reopenedBatch).getFirst();
        assertEquals("REOPENED",reopened.getClassification());
        assertEquals("EXISTING_CLOSED",reopened.getPreviousStateClassification());
        assertEquals("OPEN",findings.findById(findingId).orElseThrow().getLifecycleState());
        mvc.perform(post("/api/findings/"+findingId+"/rca").with(csrf())
                        .contentType("application/json").content("{\"narrative\":\"Patch rollout did not cover the secondary node\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        UUID rcaId=jdbc.queryForObject("select id from finding_rca where finding_id=?",UUID.class,findingId);
        mvc.perform(post("/api/admin/findings/"+findingId+"/rca/"+rcaId+"/review").with(csrf())
                        .contentType("application/json").content("{\"approve\":true,\"note\":\"Reviewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = "ADMIN")
    void excelExportKeepsFormulaLikeReportCellsAsTextAndUsesRequiredSheetOrder() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.20.30.40,web01.test,TransUnion Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        upload("WIZ","UID,Targets,Risk Rating,Results\nXLS-1,10.20.30.40,High, =HYPERLINK(\\\"https://invalid.example\\\")\n","xlsx-safe-"+UUID.randomUUID());
        var auth=new org.springframework.security.authentication.TestingAuthenticationToken(
                "manohar.kaloni@transunion.com","", "ROLE_ADMIN");
        java.nio.file.Path file=Files.createTempFile("vapt-formula-check-",".xlsx");
        try {
            try(var output=Files.newOutputStream(file))
                { reportExport.writeExcel("WIZ",auth,new ReportExportService.Filters(null,null,null,null,null,null,null),output); }
            try(var workbook=new XSSFWorkbook(Files.newInputStream(file))) {
                assertEquals(List.of("Pivot","Comments","Data"),java.util.stream.IntStream.range(0,workbook.getNumberOfSheets()).mapToObj(workbook::getSheetName).toList());
                var value=workbook.getSheet("Data").getRow(1).getCell(7);
                assertEquals(CellType.STRING,value.getCellType());
                assertTrue(value.getStringCellValue().startsWith("' =HYPERLINK("));
            }
        } finally { Files.deleteIfExists(file); }
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void resolvesAgreedMiddleSegmentHostAndEnrichesAllLinkedDatasets() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email,CM4,CM5,CM6\nAlice,alice@example.test,Org-4,Org-5,Org-6\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.13.0.1,,Portal,Production,Alice,APP-13\n");
        importCmdb("APP_INVENTORY", "ApplicationId,Plan Application Owner 1,Plan Application Owner 2\nAPP-13,Owner One,Owner Two\n");

        UUID batch = upload("WIZ", "UID,Targets\nH-1,company.web01.com\n", "middle-host-" + UUID.randomUUID());
        ReportRecordEnrichment row = results(runFor(batch).getId()).getFirst();

        assertEquals("company.web01.com", row.getOriginalTarget());
        assertEquals("MATCHED", row.getTargetMatchStatus());
        assertEquals("CONFIGURED_FQDN_TO_HOST", row.getMatchMethod());
        for (String value : List.of("web01", "10.13.0.1", "Portal", "Production", "Alice", "APP-13",
                "alice@example.test", "Org-4", "Org-5", "Org-6", "Owner One", "Owner Two")) {
            assertTrue(row.getEnrichedValuesJson().contains(value), "Missing enriched value: " + value);
        }
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void recordsUnmatchedBlankAndAmbiguousAssetsAsUnidentified() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nOwner,owner@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.9.0.1,one.example.test,One,Prod,Owner,APP-1\nweb02,10.9.0.1,two.example.test,Two,Prod,Owner,APP-2\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\nAPP-2\n");
        UUID batch = upload("PT_VERIZON_GCP", "UID,Targets,Display Name\nG-1,10.9.0.1,A\nG-2,192.0.2.44,B\nG-3,,C\n", "unmatched-" + UUID.randomUUID());
        EnrichmentRun run = runFor(batch);
        List<ReportRecordEnrichment> rows = results(run.getId());
        assertEquals(1, run.getAmbiguousRows()); assertEquals(2, run.getUnmatchedRows());
        assertEquals("AMBIGUOUS", rows.get(0).getTargetMatchStatus());
        assertEquals("MULTIPLE_ASSET_MATCHES", rows.get(0).getMatchReason());
        assertTrue(rows.get(0).getCandidateDetailsJson().contains("jasCandidateSourceRows"));
        assertEquals("Unidentified", rows.get(0).getApplicationDisplay());
        assertEquals("UNMATCHED", rows.get(1).getTargetMatchStatus());
        assertEquals("NO_ASSET_MATCH", rows.get(1).getMatchReason());
        assertEquals("Unidentified", rows.get(1).getApplicationDisplay());
        assertEquals("MISSING_TARGET", rows.get(2).getMatchReason());
        assertEquals("Unidentified", rows.get(2).getApplicationDisplay());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void keepsOwnerAndApplicationAmbiguitySeparateFromAssetMatch() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email,CM4\nShared,a@example.test,One\nShared,b@example.test,Two\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.3.0.1,web01.test,Portal,Prod,Shared,APP-D\n");
        importCmdb("APP_INVENTORY", "ApplicationId,Plan Application Owner 1\nAPP-D,Owner A\nAPP-D,Owner B\n");
        UUID batch = upload("GDP_VA_REPORT", "UID,Targets\nD-1,10.3.0.1\n", "ambiguous-" + UUID.randomUUID());
        ReportRecordEnrichment row = results(runFor(batch).getId()).getFirst();
        assertEquals("MATCHED", row.getTargetMatchStatus());
        assertEquals("AMBIGUOUS", row.getOwnerResolutionStatus());
        assertEquals("AMBIGUOUS", row.getApplicationResolutionStatus());
        assertFalse(row.getEnrichedValuesJson().contains("a@example.test"));
        assertFalse(row.getEnrichedValuesJson().contains("Owner A"));
        assertTrue(row.getCandidateDetailsJson().contains("ownerCandidateSourceRows"));
        assertTrue(row.getCandidateDetailsJson().contains("applicationCandidateSourceRows"));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void keepsMissingOwnerAndApplicationReferencesExplicit() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nOther,other@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.4.0.1,web01.test,Portal,Prod,Missing Owner,APP-MISSING\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-OTHER\n");
        UUID batch = upload("WIZ", "UID,Targets\nM-1,10.4.0.1\n", "missing-ref-" + UUID.randomUUID());
        ReportRecordEnrichment row = results(runFor(batch).getId()).getFirst();
        assertEquals("MATCHED", row.getTargetMatchStatus());
        assertEquals("UNMATCHED", row.getOwnerResolutionStatus());
        assertEquals("UNMATCHED", row.getApplicationResolutionStatus());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void preservesOldActiveReportWhenAnActiveCmdbVersionIsMissing() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.5.0.1,web01.test,Portal,Prod,Alice,APP-1\n");
        UUID appVersion = importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        UUID activeBatch = upload("GDP_VA_REPORT", "UID,Targets\nACTIVE-1,10.5.0.1\n", "active-" + UUID.randomUUID());
        assertEquals(activeBatch, batches.findByReportSectionIdAndActiveTrue("GDP_VA_REPORT").orElseThrow().getId());

        CmdbDataset app = datasets.findById("APP_INVENTORY").orElseThrow();
        CmdbVersion version = versions.findById(appVersion).orElseThrow();
        version.deactivate(); app.setActiveVersionId(null);
        versions.save(version); datasets.save(app);

        UUID failed = upload("GDP_VA_REPORT", "UID,Targets\nNEW-1,10.5.0.1\n", "missing-cmdb-" + UUID.randomUUID());
        assertEquals(ImportStatus.FAILED, batches.findById(failed).orElseThrow().getStatus());
        assertEquals(activeBatch, batches.findByReportSectionIdAndActiveTrue("GDP_VA_REPORT").orElseThrow().getId());
        EnrichmentRun failure = runs.findByImportBatchIdOrderByStartedAtDesc(failed,
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent().getFirst();
        assertEquals("FAILED", failure.getStatus());
        assertEquals("CMDB_VERSION_MISSING", failure.getDiagnosticCode());
        assertNull(failure.getAppInventoryVersionId());
        assertEquals(0, reportRecords.countByImportBatchId(failed));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = ? AND target_id = ?",
                Integer.class, "REPORT_ENRICHMENT_FAILED", failure.getId().toString()));
        assertTrue(meters.find("import.enrichment.runs").tag("section", "GDP_VA_REPORT").tag("result", "FAILURE").counter().count() > 0);
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void backfillsCommittedBatchAndRerunKeepsPriorRunAndVersion() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.6.0.1,web01.test,Old Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId,Plan Application Owner 1\nAPP-1,Old Owner\n");
        ImportBatch historical = committedBatchWithoutEnrichment();

        UUID firstRunId = startBackfill(historical.getId(), false);
        EnrichmentRun first = runs.findById(firstRunId).orElseThrow();
        assertEquals("COMPLETED", first.getStatus());
        assertTrue(results(firstRunId).getFirst().getEnrichedValuesJson().contains("Old Portal"));

        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.6.0.1,web01.test,New Portal,Prod,Alice,APP-1\n");
        UUID secondRunId = startBackfill(historical.getId(), true);
        EnrichmentRun second = runs.findById(secondRunId).orElseThrow();
        assertNotEquals(firstRunId, secondRunId);
        assertNotEquals(first.getJasVersionId(), second.getJasVersionId());
        assertTrue(results(firstRunId).getFirst().getEnrichedValuesJson().contains("Old Portal"));
        assertTrue(results(secondRunId).getFirst().getEnrichedValuesJson().contains("New Portal"));
        assertEquals(2, runs.findByImportBatchIdOrderByStartedAtDesc(historical.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
        assertEquals(ImportStatus.COMMITTED, batches.findById(historical.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void exposesRunProvenanceAndPaginatedStatusFilteredResults() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        UUID jas = importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.12.0.1,web01.test,Portal,Prod,Alice,APP-1\n");
        UUID app = importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        UUID batch = upload("WIZ", "UID,Targets\nP-1,10.12.0.1\nP-2,192.0.2.12\n", "api-" + UUID.randomUUID());
        EnrichmentRun run = runFor(batch);

        mvc.perform(get("/api/admin/reports/sections/WIZ/imports/" + batch + "/enrichments")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(run.getId().toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mvc.perform(get("/api/admin/enrichments/" + run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.jasVersionId").value(jas.toString()))
                .andExpect(jsonPath("$.data.appInventoryVersionId").value(app.toString()))
                .andExpect(jsonPath("$.data.processedRows").value(2));

        mvc.perform(get("/api/admin/enrichments/" + run.getId() + "/results")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].targetMatchStatus").value("MATCHED"));

        mvc.perform(get("/api/admin/enrichments/" + run.getId() + "/results")
                        .param("status", "unmatched"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].targetMatchStatus").value("UNMATCHED"));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void createsSectionScopedFindingsAndRetainsRepeatedImportObservations() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.14.0.1,web01.test,Portal,Prod,Alice,APP-14\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-14\n");
        UUID first = upload("GDP_VA_REPORT", "UID,Targets\nSHARED-UID,10.14.0.1\n", "identity-1-" + UUID.randomUUID());
        UUID otherSection = upload("INTERNAL_VA_BRINQA", "UID,Targets\nSHARED-UID,10.14.0.1\n", "identity-2-" + UUID.randomUUID());
        UUID repeated = upload("GDP_VA_REPORT", "UID,Targets\nSHARED-UID,10.14.0.1\n", "identity-3-" + UUID.randomUUID());

        assertEquals(1, findings.countByReportSectionId("GDP_VA_REPORT"));
        assertEquals(1, findings.countByReportSectionId("INTERNAL_VA_BRINQA"));
        assertEquals("NEW", observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(first).getFirst().getClassification());
        assertEquals("NEW", observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(otherSection).getFirst().getClassification());
        assertEquals("EXISTING_OPEN", observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(repeated).getFirst().getClassification());
        assertEquals(1, findingEvents.countByImportBatchId(first));
        assertEquals(1, findingEvents.countByImportBatchId(repeated));
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void failedReconciliationKeepsThePreviousActiveReportBatch() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.15.0.1,web01.test,Portal,Prod,Alice,APP-15\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-15\n");
        UUID active = upload("WIZ", "UID,Targets\nACTIVE-UID,10.15.0.1\n", "active-recon-" + UUID.randomUUID());
        doAnswer(call -> { throw new IllegalStateException("forced reconciliation failure"); })
                .when(reconciliationWorker).process(any(UUID.class));
        UUID failed = upload("WIZ", "UID,Targets\nFAILED-UID,10.15.0.1\n", "failed-recon-" + UUID.randomUUID());
        assertEquals(ImportStatus.FAILED, batches.findById(failed).orElseThrow().getStatus());
        assertEquals(active, batches.findByReportSectionIdAndActiveTrue("WIZ").orElseThrow().getId());
    }

    @Test
    void concurrentSameSectionImportsSerializeFindingCreation() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.16.0.1,web01.test,Portal,Prod,Alice,APP-16\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-16\n");
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> uploadConcurrent("WIZ", "C-1", ready, start));
            var second = executor.submit(() -> uploadConcurrent("WIZ", "C-1", ready, start));
            assertTrue(ready.await(20, TimeUnit.SECONDS), "Both concurrent upload workers should be ready");
            start.countDown();
            UUID firstBatch = first.get(60, TimeUnit.SECONDS);
            UUID secondBatch = second.get(60, TimeUnit.SECONDS);
            assertNotEquals(firstBatch, secondBatch);
            assertEquals(1, findings.countByReportSectionId("WIZ"));
            var rows = new ArrayList<>(observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(firstBatch));
            rows.addAll(observations.findByImportBatchIdOrderBySourceRecordSourceRowNumberAsc(secondBatch));
            assertEquals(2, rows.size());
            assertEquals(1, rows.stream().filter(r -> r.getClassification().equals("NEW")).count());
            assertEquals(1, rows.stream().filter(r -> r.getClassification().equals("EXISTING_OPEN")).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void usesCapturedVersionWhenCmdbActivatesDuringRun() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        UUID oldJas = importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.7.0.1,web01.test,Old Snapshot,Prod,Alice,APP-1\n");
        UUID newJas = importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.7.0.1,web01.test,New Snapshot,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        cmdb.activate("JAS", oldJas, ADMIN);
        AtomicBoolean switched = new AtomicBoolean();
        doAnswer(call -> {
            UUID pinned = call.getArgument(0);
            if (oldJas.equals(pinned) && switched.compareAndSet(false, true)) cmdb.activate("JAS", newJas, ADMIN);
            return call.callRealMethod();
        }).when(lookupIndex).ensureIndexed(any(UUID.class));

        UUID batch = upload("INTERNAL_VA_BRINQA", "UID,Targets\nI-1,10.7.0.1\n", "snapshot-" + UUID.randomUUID());
        EnrichmentRun run = runFor(batch);
        assertTrue(switched.get());
        assertEquals(oldJas, run.getJasVersionId());
        assertTrue(results(run.getId()).getFirst().getEnrichedValuesJson().contains("Old Snapshot"));
        assertEquals(newJas, datasets.findById("JAS").orElseThrow().getActiveVersionId());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void keepsGdpAndBrinqaRunsIndependentAndAppSecHasNoEnrichmentRun() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.8.0.1,web01.test,Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        UUID gdp = upload("GDP_VA_REPORT", "UID,Targets\nSAME-UID,10.8.0.1\n", "gdp-" + UUID.randomUUID());
        UUID brinqa = upload("INTERNAL_VA_BRINQA", "UID,Targets\nSAME-UID,10.8.0.1\n", "brinqa-" + UUID.randomUUID());
        assertEquals(1, runFor(gdp).getProcessedRows()); assertEquals(1, runFor(brinqa).getProcessedRows());
        UUID appsec = upload("APPSEC_FINDINGS_PT", "Finding ID,Record Id,Title\nF-1,R-1,Finding\n", "appsec-" + UUID.randomUUID());
        assertEquals(ImportStatus.COMMITTED, batches.findById(appsec).orElseThrow().getStatus());
        assertEquals(0, runs.findByImportBatchIdOrderByStartedAtDesc(appsec,
                org.springframework.data.domain.PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void processesLargeReportInBoundedEnrichmentChunks() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.10.0.1,web01.test,Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        StringBuilder csv = new StringBuilder("UID,Targets\n");
        for (int i = 0; i < 601; i++) csv.append("L-").append(i).append(",10.10.0.1\n");
        clearInvocations(resultWriter);
        UUID batch = upload("WIZ", csv.toString(), "large-" + UUID.randomUUID());
        EnrichmentRun run = runFor(batch);
        assertEquals(601, run.getProcessedRows());
        assertEquals(601, enriched.count());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<ReportRecordEnrichment>> chunks = (ArgumentCaptor<List<ReportRecordEnrichment>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(List.class);
        verify(resultWriter, times(3)).saveChunk(any(), chunks.capture(), any(Long.class), any(Long.class), any(Long.class));
        assertEquals(List.of(250, 250, 101), chunks.getAllValues().stream().map(List::size).toList());
        assertEquals(601, chunks.getAllValues().stream().mapToInt(List::size).sum());
    }

    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = "ADMIN")
    void synthetic100kFindingsExerciseIngestionPaginationDashboardAndExport() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("vapt.run-scale-test"),
                "Run explicitly with -Dvapt.run-scale-test=true; excluded from normal functional suite");
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.20.30.40,web01.test,TransUnion Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        StringBuilder csv=new StringBuilder(12_000_000).append("UID,Targets,Risk Rating,Compliance Status,Severity,Results,AppTag\n");
        for(int i=0;i<100_000;i++) csv.append("SYNTH-").append(i).append(",10.20.30.40,High,Open,High,Synthetic safe test finding,Portal\\API\n");
        var auth=new org.springframework.security.authentication.TestingAuthenticationToken(
                "manohar.kaloni@transunion.com","", "ROLE_ADMIN");
        long started=System.nanoTime();
        UUID batch=upload("WIZ",csv.toString(),"scale-"+UUID.randomUUID());
        long ingestMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        assertEquals(ImportStatus.COMMITTED,batches.findById(batch).orElseThrow().getStatus());
        assertEquals(100_000,findings.countByReportSectionId("WIZ"));

        started=System.nanoTime();
        var page0=findingWorkspace.data("WIZ",auth,0,100,"observedAt","desc",null,null,null,null,null,null,null);
        var pageLast=findingWorkspace.data("WIZ",auth,999,100,"observedAt","desc",null,null,null,null,null,null,null);
        long paginationMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        assertEquals(100_000,page0.totalElements()); assertEquals(100,page0.content().size());
        assertEquals(100_000,pageLast.totalElements()); assertEquals(100,pageLast.content().size());

        started=System.nanoTime(); var dashboard=findingWorkspace.dashboard("WIZ",auth,null,null,null,null,null);
        long dashboardMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
        assertEquals(100_000,dashboard.openFindings()); assertEquals(100_000,dashboard.riskRating().get("High"));

        java.nio.file.Path exported=Files.createTempFile("vapt-synthetic-100k-",".xlsx");
        long exportMs;
        try {
            started=System.nanoTime();
            try(var out=Files.newOutputStream(exported)) {
                reportExport.writeExcel("WIZ",auth,new ReportExportService.Filters(null,null,null,null,null,null,null),out);
            }
            exportMs=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            try(var zip=new java.util.zip.ZipFile(exported.toFile())) {
                String workbookXml=new String(zip.getInputStream(zip.getEntry("xl/workbook.xml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                int pivot=workbookXml.indexOf("name=\"Pivot\""); int comments=workbookXml.indexOf("name=\"Comments\""); int data=workbookXml.indexOf("name=\"Data\"");
                assertTrue(pivot>=0&&comments>pivot&&data>comments,"Export sheet order must be Pivot, Comments, Data");
                assertEquals(100_001,countToken(zip.getInputStream(zip.getEntry("xl/worksheets/sheet3.xml")),"<row "));
            }
            System.out.printf(java.util.Locale.ROOT,"SCALE 100k: ingestion=%dms pagination(first+last 100 rows)=%dms dashboard=%dms xlsxExport=%dms xlsxBytes=%d%n",
                    ingestMs,paginationMs,dashboardMs,exportMs,Files.size(exported));
        } finally { Files.deleteIfExists(exported); }
    }

    private long countToken(java.io.InputStream input,String token) throws IOException {
        byte[] needle=token.getBytes(java.nio.charset.StandardCharsets.UTF_8); byte[] buffer=new byte[64*1024];
        int matched=0; long count=0;
        try(input) { int read; while((read=input.read(buffer))>=0) for(int i=0;i<read;i++) {
            if(buffer[i]==needle[matched]) { if(++matched==needle.length) { count++; matched=0; } }
            else matched=buffer[i]==needle[0]?1:0;
        } }
        return count;
    }

    @Test
    @WithMockUser(username = "ordinary-user", roles = "USER")
    void userCannotReadOrTriggerEnrichmentApis() throws Exception {
        UUID batchId = committedBatchWithoutEnrichment().getId();
        mvc.perform(get("/api/admin/reports/sections/WIZ/imports/" + batchId + "/enrichments"))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/reports/sections/WIZ/imports/" + batchId + "/enrichments").with(csrf()))
                .andExpect(status().isForbidden());
        UUID runId = UUID.randomUUID();
        mvc.perform(get("/api/admin/enrichments/" + runId)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/enrichments/" + runId + "/results")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "ADMIN")
    void doesNotLogReportCellContentsAndPropagatesCorrelationToErrorsAndAudit() throws Exception {
        importCmdb("HEADCOUNT", "Preferred Name,Associated's Email\nAlice,alice@example.test\n");
        importCmdb("JAS", "Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.11.0.1,web01.test,Portal,Prod,Alice,APP-1\n");
        importCmdb("APP_INVENTORY", "ApplicationId\nAPP-1\n");
        String secret = "SENSITIVE_REPORT_CELL_" + UUID.randomUUID();
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        String correlation = "safe-log-" + UUID.randomUUID();
        UUID batch;
        try { batch = upload("WIZ", "UID,Targets,Display Name\nL-1,10.11.0.1," + secret + "\n", correlation); }
        finally { logger.detachAppender(appender); }
        assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(secret)));
        EnrichmentRun run = runFor(batch);
        assertEquals(correlation, run.getCorrelationId());
        assertEquals("COMPLETED", run.getStatus());
    }

    private UUID importCmdb(String dataset, String csv) {
        MockMultipartFile file = new MockMultipartFile("file", dataset.toLowerCase() + ".csv", "text/csv", csv.getBytes());
        Map<String, Object> draft = cmdb.beginUpload(dataset, file, ADMIN);
        @SuppressWarnings("unchecked") List<Map<String, Object>> candidates = (List<Map<String, Object>>) draft.get("candidates");
        Map<String, Object> candidate = candidates.getFirst();
        @SuppressWarnings("unchecked") Map<String, Integer> mapping = (Map<String, Integer>) candidate.get("suggestedMapping");
        Map<String, Object> completed = cmdb.confirm(UUID.fromString(draft.get("id").toString()),
                ((Number) candidate.get("sheetIndex")).intValue(), ((Number) candidate.get("headerRow")).intValue(),
                mapping, ADMIN);
        assertEquals("READY", completed.get("status"));
        return UUID.fromString(completed.get("id").toString());
    }

    private UUID upload(String section, String csv, String correlation) throws Exception {
        return upload(section,csv,correlation,false);
    }

    private UUID upload(String section, String csv, String correlation, boolean completeSnapshot) throws Exception {
        var response = mvc.perform(multipart("/api/reports/sections/" + section + "/imports")
                        .file(new MockMultipartFile("file", "report.csv", "text/csv", csv.getBytes()))
                        .param("completeSnapshot",Boolean.toString(completeSnapshot))
                        .header("X-Correlation-ID", correlation).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.id"));
    }

    private UUID uploadConcurrent(String section, String identity, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        var response = mvc.perform(multipart("/api/reports/sections/" + section + "/imports")
                        .file(new MockMultipartFile("file", "concurrent.csv", "text/csv",
                                ("UID,Targets\n" + identity + ",10.16.0.1\n").getBytes()))
                        .header("X-Correlation-ID", "concurrent-" + UUID.randomUUID())
                        .with(user(ADMIN).roles("ADMIN")).with(csrf()))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.id"));
    }

    private EnrichmentRun runFor(UUID batchId) {
        return runs.findByImportBatchIdOrderByStartedAtDesc(batchId,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent().getFirst();
    }

    private List<ReportRecordEnrichment> results(UUID runId) {
        return enriched.findByEnrichmentRunIdOrderBySourceRowNumberAsc(runId,
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
    }

    private UUID startBackfill(UUID batchId, boolean rerun) throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/admin/reports/sections/WIZ/imports/" + batchId + "/enrichments")
                        .param("rerun", Boolean.toString(rerun)).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("COMPLETED")).andReturn();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.data.id"));
    }

    private ImportBatch committedBatchWithoutEnrichment() {
        SourceFile source = sourceFiles.save(new SourceFile(UUID.randomUUID(), "old-report.csv",
                "historic/" + UUID.randomUUID() + ".dat", 10, "text/csv", ".csv", "a".repeat(64), ADMIN));
        ImportBatch batch = batchService.createBatch(ReportSectionId.WIZ, source);
        batchService.transitionTo(batch.getId(), ImportStatus.VALIDATING);
        batchService.completeStage(batch.getId(), "VALIDATING", ImportStatus.STAGING, 1, "Validated old import");
        sourceWriter.saveChunk(batch.getId(), List.of(new ReportRecordPersistenceService.SourceRow(2, "OLD-1",
                Map.of("UID", "OLD-1", "Targets", "10.6.0.1", "Display Name", "Historic"))));
        batchService.updateImportCounts(batch.getId(), 1, 1, 0);
        batchService.completeStage(batch.getId(), "STAGING", ImportStatus.ENRICHING, 1, "Staged old import");
        batchService.recordStageExecution(batch.getId(), "ENRICHING", StageStatus.SKIPPED, 0, Instant.now(), Instant.now(), "Imported before enrichment");
        batchService.transitionTo(batch.getId(), ImportStatus.RECONCILING);
        batchService.recordStageExecution(batch.getId(), "RECONCILING", StageStatus.SKIPPED, 0, Instant.now(), Instant.now(), "Reconciliation not yet implemented");
        return batchService.commitAndActivate(batch.getId());
    }
}
