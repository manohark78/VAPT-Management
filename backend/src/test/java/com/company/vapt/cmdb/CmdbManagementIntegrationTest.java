package com.company.vapt.cmdb;

import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;
import com.company.vapt.cmdb.entity.CmdbVersion;
import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.cmdb.repository.CmdbRecordRepository;

import com.company.vapt.cmdb.service.CmdbManagementService;
import com.company.vapt.cmdb.service.CmdbRecordPersistenceService;
import com.company.vapt.cmdb.domain.CmdbDatasetType;
import com.company.vapt.cmdb.repository.*;
import com.company.vapt.ingestion.storage.StorageProperties;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CmdbManagementIntegrationTest {
 private static final Path ROOT=createRoot();
 private static Path createRoot(){try{return Files.createTempDirectory("vapt-cmdb-it-");}catch(IOException e){throw new ExceptionInInitializerError(e);}}
 @DynamicPropertySource static void storage(DynamicPropertyRegistry r){r.add("vapt.storage.root-dir",()->ROOT.toString());}
 @Autowired CmdbManagementService service; @MockitoSpyBean CmdbVersionRepository versions; @Autowired CmdbRecordRepository records; @Autowired CmdbDatasetRepository datasets; @Autowired MockMvc mvc;
 @MockitoSpyBean CmdbRecordPersistenceService recordWriter;
 @AfterEach void cleanFiles() throws IOException {if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});}}
 @AfterAll static void removeRoot() throws IOException {if(Files.exists(ROOT))try(var paths=Files.walk(ROOT)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException e){throw new RuntimeException(e);}});}assertFalse(Files.exists(ROOT));}

 @Test @WithMockUser(roles="ADMIN") void importsAllThreeIndependentDatasetsAndPersistsRows(){
  var head=importCsv("HEADCOUNT","Preferred Name,Associated's Email,CM4\nAlice,alice@example.test,Engineering\n");
  var jas=importCsv("JAS","Host,IP Address,FQDN,Tu_AppName,Environment,OwnedBy,Application ID\nweb01,10.1.2.3,web01.example.test,Portal,Prod,Alice,APP-1\n");
  var app=importCsv("APP_INVENTORY","ApplicationId,Plan Application Owner 1,Plan Application Owner 2\nAPP-1,Owner One,Owner Two\n");
  assertEquals("READY",head.get("status"));assertEquals("READY",jas.get("status"));assertEquals("READY",app.get("status"));
  assertNotEquals(datasets.findById("HEADCOUNT").orElseThrow().getActiveVersionId(),datasets.findById("JAS").orElseThrow().getActiveVersionId());
  assertEquals(1,records.countVersionRows(UUID.fromString(head.get("id").toString())));assertEquals(1,records.countVersionRows(UUID.fromString(jas.get("id").toString())));assertEquals(1,records.countVersionRows(UUID.fromString(app.get("id").toString())));
  assertTrue(records.findByVersionIdOrderBySourceRowNumber(UUID.fromString(jas.get("id").toString())).get(0).getValuesJson().contains("web01.example.test"));
 }

 @Test @WithMockUser(roles="ADMIN") void failedReplacementLeavesPriorActiveVersionAndRemovesStagedRows(){
  var active=importCsv("APP_INVENTORY","ApplicationId,Plan Application Owner 1\nAPP-1,A\n");UUID old=UUID.fromString(active.get("id").toString());
  var draft=service.beginUpload("APP_INVENTORY",csv("ApplicationId,Plan Application Owner 1\n,A\n"),"admin");UUID next=UUID.fromString(draft.get("id").toString());
  Map<String,Integer> mapping=Map.of("ApplicationId",0,"Plan Application Owner 1",1);
  var failed=service.confirm(next,0,0,mapping,"admin");
  assertEquals("FAILED",failed.get("status"));assertEquals(old,datasets.findById("APP_INVENTORY").orElseThrow().getActiveVersionId());assertEquals(0,records.countVersionRows(next));
 }

 @Test @WithMockUser(roles="ADMIN") void discoversNonFirstSheetAndHeaderAndAllowsManualFqdnMapping() throws Exception {
  byte[] bytes=excel(true);var draft=service.beginUpload("JAS",new MockMultipartFile("file","inventory.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes),"admin");
  @SuppressWarnings("unchecked") List<Map<String,Object>> candidates=(List<Map<String,Object>>)draft.get("candidates");
  Map<String,Object> candidate=candidates.stream().filter(c->Objects.equals(c.get("sheetName"),"Inventory")&&Objects.equals(c.get("headerRow"),1)).findFirst().orElseThrow();
  assertEquals(1,candidate.get("sheetIndex"));
  assertFalse(((Map<?,?>)candidate.get("suggestedMapping")).containsKey("FQDN"),"Unknown FQDN header must require administrator mapping");
  Map<String,Integer> mapping=new LinkedHashMap<>();mapping.put("Host",0);mapping.put("IP Address",1);mapping.put("FQDN",2);mapping.put("Tu_AppName",3);mapping.put("Environment",4);mapping.put("OwnedBy",5);mapping.put("Application ID",6);
  @SuppressWarnings("unchecked") Map<String,Object> preview=service.preview(UUID.fromString(draft.get("id").toString()),1,1,mapping);@SuppressWarnings("unchecked") Map<String,Map<String,Object>> profile=(Map<String,Map<String,Object>>)preview.get("profile");assertEquals(1L,profile.get("FQDN").get("nonEmpty"));
  var result=service.confirm(UUID.fromString(draft.get("id").toString()),1,1,mapping,"admin");assertEquals("READY",result.get("status"));assertEquals("Inventory",result.get("sheetName"));
 }

 @Test @WithMockUser(roles="ADMIN") void identifiesAmbiguousHeaderRowsForManualSelection(){
  var draft=service.beginUpload("HEADCOUNT",csv("Preferred Name,Associated's Email\nAlice,alice@example.test\nSummary,Weekly extract\nPreferred Name,Associated's Email\nBob,bob@example.test\n"),"admin");
  @SuppressWarnings("unchecked") List<Map<String,Object>> candidates=(List<Map<String,Object>>)draft.get("candidates");assertEquals(2,candidates.size());assertEquals(true,draft.get("ambiguous"));assertEquals(Set.of(0,3),candidates.stream().map(c->((Number)c.get("headerRow")).intValue()).collect(java.util.stream.Collectors.toSet()));
 }

 @Test @WithMockUser(roles="ADMIN") void parsesBothExcelFormatsAndKeepsMappingWithVersion(){
  for(boolean xlsx:List.of(false,true)){String ext=xlsx?"xlsx":"xls";var draft=service.beginUpload("JAS",new MockMultipartFile("file","inventory."+ext,xlsx?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/vnd.ms-excel",excel(xlsx)),"admin");
   @SuppressWarnings("unchecked") List<Map<String,Object>> cs=(List<Map<String,Object>>)draft.get("candidates");Map<String,Object> c=cs.get(0);
   Map<String,Integer> m=Map.of("Host",0,"IP Address",1,"FQDN",2,"Tu_AppName",3,"Environment",4,"OwnedBy",5,"Application ID",6);var result=service.confirm(UUID.fromString(draft.get("id").toString()),((Number)c.get("sheetIndex")).intValue(),((Number)c.get("headerRow")).intValue(),m,"admin");assertEquals("READY",result.get("status"));assertNotNull(versions.findById(UUID.fromString(result.get("id").toString())).orElseThrow().getMappingJson());
  }
 }

 @Test @WithMockUser(roles="ADMIN") void profilesBlankAndDuplicateValuesAndPersistsSourceMapping(){
  var result=importCsv("APP_INVENTORY","ApplicationId,Plan Application Owner 1,Plan Application Owner 2\nAPP-1,A,\nAPP-1,A,\n");
  assertEquals("READY",result.get("status"));assertEquals(2L,result.get("recordCount"));
  String profile=versions.findById(UUID.fromString(result.get("id").toString())).orElseThrow().getProfileJson();
  assertTrue(profile.contains("\"duplicateValues\":1"));assertTrue(profile.contains("Plan Application Owner 2"));assertTrue(profile.contains("\"nullOrBlank\":2"));
  String mapping=versions.findById(UUID.fromString(result.get("id").toString())).orElseThrow().getMappingJson();
  assertTrue(mapping.contains("sourceHeader"));assertTrue(mapping.contains("ApplicationId"));
 }

 @Test @WithMockUser(roles="ADMIN") void largeCsvWritesRowsInBoundedChunks(){
  StringBuilder data=new StringBuilder("ApplicationId,Plan Application Owner 1,Plan Application Owner 2\n");for(int i=0;i<600;i++)data.append("APP-").append(i).append(",Owner,").append("\n");
  clearInvocations(recordWriter);var result=importCsv("APP_INVENTORY",data.toString());assertEquals("READY",result.get("status"));assertEquals(600L,result.get("recordCount"));
  @SuppressWarnings("unchecked") ArgumentCaptor<List<CmdbRecord>> captor=(ArgumentCaptor<List<CmdbRecord>>)(ArgumentCaptor<?>)ArgumentCaptor.forClass(List.class);
  verify(recordWriter,times(3)).saveChunk(any(),captor.capture(),anyList(),anyList());assertTrue(captor.getAllValues().stream().allMatch(chunk->chunk.size()<=250));assertEquals(600,captor.getAllValues().stream().mapToInt(List::size).sum());
 }

 @Test @WithMockUser(roles="ADMIN") void detectsAmbiguousSheetCandidatesForAdministratorSelection(){
  byte[] bytes=twoInventorySheets();var draft=service.beginUpload("JAS",new MockMultipartFile("file","two.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes),"admin");
  @SuppressWarnings("unchecked") List<Map<String,Object>> cs=(List<Map<String,Object>>)draft.get("candidates");assertEquals(2,cs.size());assertEquals(true,draft.get("ambiguous"));
  @SuppressWarnings("unchecked") List<Map<String,Object>> sheets=(List<Map<String,Object>>)draft.get("worksheets");assertEquals(Set.of("Primary","Archive"),sheets.stream().map(s->s.get("sheetName")).collect(java.util.stream.Collectors.toSet()));
 }
 @Test @WithMockUser(roles="ADMIN") void rejectsConfirmWhenRequiredFieldsAreNotMapped(){var draft=service.beginUpload("JAS",csv("Host,IP Address\nweb01,10.1.2.3\n"),"admin");@SuppressWarnings("unchecked") List<Map<String,Object>> cs=(List<Map<String,Object>>)draft.get("candidates");Map<String,Object> c=cs.get(0);assertThrows(com.company.vapt.common.exception.ValidationException.class,()->service.confirm(UUID.fromString(draft.get("id").toString()),((Number)c.get("sheetIndex")).intValue(),((Number)c.get("headerRow")).intValue(),Map.of("Host",0,"IP Address",1),"admin"));}
 @Test @WithMockUser(roles="ADMIN") void databaseMetadataFailureCleansPhysicalUpload(){doThrow(new RuntimeException("simulated database write failure")).when(versions).saveAndFlush(any(CmdbVersion.class));assertThrows(RuntimeException.class,()->service.beginUpload("APP_INVENTORY",csv("ApplicationId\nAPP-1\n"),"admin"));Path folder=ROOT.resolve("cmdb").resolve("APP_INVENTORY");if(Files.exists(folder))try(var paths=Files.list(folder)){assertEquals(0,paths.count());}catch(IOException e){throw new UncheckedIOException(e);}assertEquals(0,versions.count());}

 @Test @WithMockUser(roles="USER") void normalUserCannotAccessCmdbAdministration() throws Exception {mvc.perform(get("/api/admin/cmdb/datasets")).andExpect(status().isForbidden());}
 @Test @WithMockUser(roles="ADMIN") void adminCanListThreeIndependentDatasets() throws Exception {mvc.perform(get("/api/admin/cmdb/datasets")).andExpect(status().isOk());assertEquals(3,datasets.count());}
 @Test @WithMockUser(roles="ADMIN") void uploadInspectionEndpointReturnsDetectedHeaderAndDraftStatus() throws Exception {mvc.perform(multipart("/api/admin/cmdb/datasets/APP_INVENTORY/uploads").file(csv("ApplicationId,Plan Application Owner 1\nAPP-1,Owner\n")).with(csrf())).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("INSPECTING")).andExpect(jsonPath("$.data.candidates[0].sheetName").value("CSV"));}

 @Test @WithMockUser(roles="ADMIN") void canSwitchBetweenHistoricalVersionsWithinSameDataset(){var first=importCsv("APP_INVENTORY","ApplicationId,Plan Application Owner 1\nAPP-1,A\n");var second=importCsv("APP_INVENTORY","ApplicationId,Plan Application Owner 1\nAPP-2,B\n");UUID v1=UUID.fromString(first.get("id").toString()),v2=UUID.fromString(second.get("id").toString());assertEquals(v2,datasets.findById("APP_INVENTORY").orElseThrow().getActiveVersionId());service.activate("APP_INVENTORY",v1,"admin");assertEquals(v1,datasets.findById("APP_INVENTORY").orElseThrow().getActiveVersionId());assertTrue(versions.findById(v1).orElseThrow().isActive());assertFalse(versions.findById(v2).orElseThrow().isActive());}

 private Map<String,Object> importCsv(String dataset,String data){var draft=service.beginUpload(dataset,csv(data),"admin");@SuppressWarnings("unchecked") List<Map<String,Object>> cs=(List<Map<String,Object>>)draft.get("candidates");Map<String,Object> c=cs.get(0);@SuppressWarnings("unchecked") Map<String,Integer> mapping=(Map<String,Integer>)c.get("suggestedMapping");return service.confirm(UUID.fromString(draft.get("id").toString()),((Number)c.get("sheetIndex")).intValue(),((Number)c.get("headerRow")).intValue(),mapping,"admin");}
 private MockMultipartFile csv(String data){return new MockMultipartFile("file","dataset.csv","text/csv",data.getBytes());}
 private byte[] excel(boolean xlsx){try(Workbook wb=xlsx?new XSSFWorkbook():new HSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
  wb.createSheet("Notes").createRow(0).createCell(0).setCellValue("Workbook notes");Sheet s=wb.createSheet("Inventory");s.createRow(0).createCell(0).setCellValue("Inventory export");Row h=s.createRow(1);String[] headers={"Host","IP Address","DNS Canonical Name","Tu_AppName","Environment","OwnedBy","Application ID"};for(int i=0;i<headers.length;i++)h.createCell(i).setCellValue(headers[i]);Row r=s.createRow(2);String[] vals={"web01","10.1.2.3","web01.example.test","Portal","Prod","Alice","APP-1"};for(int i=0;i<vals.length;i++)r.createCell(i).setCellValue(vals[i]);wb.write(out);return out.toByteArray();
 }catch(IOException e){throw new UncheckedIOException(e);}}
 private byte[] twoInventorySheets(){try(Workbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){for(String name:List.of("Primary","Archive")){Sheet s=wb.createSheet(name);Row h=s.createRow(0);String[] headers={"Host","IP Address","FQDN","Tu_AppName","Environment","OwnedBy","Application ID"};for(int i=0;i<headers.length;i++)h.createCell(i).setCellValue(headers[i]);Row r=s.createRow(1);String[] vals={"web01","10.1.2.3","web01.example.test","Portal","Prod","Alice","APP-1"};for(int i=0;i<vals.length;i++)r.createCell(i).setCellValue(vals[i]);}wb.write(out);return out.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}}
}
