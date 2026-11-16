package com.company.vapt.cmdb.service;

import com.company.vapt.cmdb.entity.CmdbDataset;
import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.cmdb.entity.CmdbRecordFieldProfile;
import com.company.vapt.cmdb.entity.CmdbRecordLookup;
import com.company.vapt.cmdb.entity.CmdbVersion;
import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.cmdb.repository.CmdbRecordFieldProfileRepository;
import com.company.vapt.cmdb.repository.CmdbRecordRepository;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;
import com.company.vapt.enrichment.service.CmdbLookupProjection;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.cmdb.domain.CmdbDatasetType;
import com.company.vapt.cmdb.repository.*;
import com.company.vapt.cmdb.storage.CmdbFileStorageService;
import com.company.vapt.cmdb.storage.CmdbFileStorageService.StoredCmdbFile;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Owns independent CMDB draft, profiling, persistence and version activation workflows. */
@Service
public class CmdbManagementService {
    private static final int SCAN_ROWS=50, PREVIEW_ROWS=25, CHUNK_SIZE=250, MAX_COLUMNS=512;
    private final CmdbDatasetRepository datasets; private final CmdbVersionRepository versions;
    private final CmdbRecordRepository records; private final CmdbRecordPersistenceService recordWriter;
    private final CmdbRecordFieldProfileRepository fieldProfileRepository;
    private final CmdbFileStorageService storage; private final AuditService audit;
    private final CmdbVersionTransactionService versionTransactions;
    private final com.company.vapt.enrichment.service.CmdbLookupProjection lookupProjection;
    private final CmdbWorkbookReader workbookReader=new CmdbWorkbookReader();
    private final tools.jackson.databind.ObjectMapper json=JsonMapper.builder().build();
    public CmdbManagementService(CmdbDatasetRepository datasets,CmdbVersionRepository versions,CmdbRecordRepository records,
            CmdbRecordPersistenceService recordWriter,CmdbRecordFieldProfileRepository fieldProfileRepository,CmdbFileStorageService storage,AuditService audit,CmdbVersionTransactionService versionTransactions,com.company.vapt.enrichment.service.CmdbLookupProjection lookupProjection){
        this.datasets=datasets;this.versions=versions;this.records=records;this.recordWriter=recordWriter;this.fieldProfileRepository=fieldProfileRepository;this.storage=storage;this.audit=audit;this.versionTransactions=versionTransactions;
        this.lookupProjection=lookupProjection;
    }

    public List<Map<String,Object>> listDatasets(){
        return datasets.findAll(Sort.by("datasetKey")).stream().map(d->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",d.getDatasetKey());m.put("name",d.getDisplayName());
            m.put("activeVersion",d.getActiveVersionId()==null?null:versionDto(versions.findById(d.getActiveVersionId()).orElse(null)));
            m.put("fields",type(d.getDatasetKey()).fields());m.put("requiredFields",type(d.getDatasetKey()).fields().stream().filter(f->type(d.getDatasetKey()).required(f)).toList());return m;
        }).toList();
    }

    @Transactional
    public Map<String,Object> beginUpload(String datasetKey,MultipartFile file,String actor){
        CmdbDatasetType type=type(datasetKey); if(!datasets.existsById(type.name())) throw new ResourceNotFoundException("CmdbDataset",type.name());
        StoredCmdbFile stored=storage.store(type.name(),file);
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.delete(stored.storagePath());}
        });
        CmdbVersion version=new CmdbVersion(UUID.randomUUID(),type.name(),stored.filename(),stored.storagePath(),stored.checksum(),actor);
        try {
            versions.saveAndFlush(version);
            Inspection inspection=inspect(version, type, stored.extension());
            version.setInspection(write(Map.of("worksheets",inspection.worksheets(),"candidates",inspection.candidates())));
            versions.saveAndFlush(version);
            audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_UPLOAD_RECEIVED","CMDB_VERSION",version.getId().toString(),"dataset="+type.name()+"; candidateCount="+inspection.candidates().size());
            Map<String,Object> result=versionDto(version);result.put("candidates",inspection.candidates());result.put("worksheets",inspection.worksheets());result.put("ambiguous",inspection.candidates().size()!=1);return result;
        } catch(ValidationException inspectionFailure){
            version.fail("{\"code\":\"WORKBOOK_INSPECTION_FAILED\",\"message\":\"Workbook inspection failed. The file was retained for traceability.\"}");
            version.setCounts(0,0,0);versions.saveAndFlush(version);
            audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_IMPORT_FAILED","CMDB_VERSION",version.getId().toString(),"dataset="+type.name()+"; phase=INSPECTION");
            Map<String,Object> result=versionDto(version);result.put("candidates",List.of());result.put("ambiguous",false);return result;
        } catch(RuntimeException e){ storage.delete(stored.storagePath()); throw e; }
    }

    public Map<String,Object> inspectUpload(UUID id){CmdbVersion v=versions.findById(id).orElseThrow(()->new ResourceNotFoundException("CmdbVersion",id.toString()));
        Map<String,Object> result=versionDto(v);result.put("candidates",readInspectionCandidates(v.getInspectionJson()));result.put("worksheets",readWorksheets(v.getInspectionJson()));return result;}

    public Map<String,Object> preview(UUID id,int sheet,int headerRow){
        return preview(id,sheet,headerRow,null);
    }
    public Map<String,Object> preview(UUID id,int sheet,int headerRow,Map<String,Integer> suppliedMapping){
        CmdbVersion v=requireDraft(id); Map<String,Object> candidate=candidate(v,sheet,headerRow);
        List<String> headers=strings(candidate.get("headers")); Map<String,Object> mapped=(Map<String,Object>)candidate.get("suggestedMapping");
        List<Map<String,String>> sample=new ArrayList<>(); final int[] rowCount={0};
        readRows(v, sheet, headerRow, (rowNo,values)->{if(rowNo>headerRow && rowCount[0]<PREVIEW_ROWS && !blank(values)){Map<String,String> item=new LinkedHashMap<>();for(int i=0;i<headers.size();i++)item.put(headers.get(i).isBlank()?"Column "+(i+1):headers.get(i),at(values,i));sample.add(item);rowCount[0]++;}});
        Map<String,Object> profile=profileSample(v, type(v.getDatasetKey()), sheet, headerRow, headers, suppliedMapping==null?mapped:suppliedMapping);
        Map<String,Object> out=new LinkedHashMap<>();out.put("version",versionDto(v));out.put("candidate",candidate);out.put("worksheets",readWorksheets(v.getInspectionJson()));out.put("sampleRows",sample);out.put("profile",profile);out.put("fields",type(v.getDatasetKey()).fields());out.put("requiredFields",type(v.getDatasetKey()).fields().stream().filter(f->type(v.getDatasetKey()).required(f)).toList());return out;
    }

    public Map<String,Object> confirm(UUID id,int sheet,int headerRow,Map<String,Integer> mapping,String actor){
        CmdbVersion version=requireDraft(id); CmdbDatasetType type=type(version.getDatasetKey()); Map<String,Object> candidate=candidate(version,sheet,headerRow);
        List<String> headers=strings(candidate.get("headers")); validateMapping(type,headers,mapping);
        versionTransactions.markProcessing(id,actor);
        List<CmdbRecord> chunk=new ArrayList<>(CHUNK_SIZE); List<CmdbRecordFieldProfile> profileChunk=new ArrayList<>(CHUNK_SIZE* Math.max(1,mapping.size())); List<CmdbRecordLookup> lookupChunk=new ArrayList<>(CHUNK_SIZE*5); long[] counts={0,0,0}; Map<String,ColumnProfile> profiles=new LinkedHashMap<>();
        for(String field:mapping.keySet()) profiles.put(field,new ColumnProfile());
        try {
            readRows(version,sheet,headerRow,(rowNo,values)->{
                if(rowNo<=headerRow || blank(values)) return;
                counts[0]++;
                Map<String,String> source=new LinkedHashMap<>(),fields=new LinkedHashMap<>();
                if(values.size()>headers.size()&&values.subList(headers.size(),values.size()).stream().anyMatch(v->v!=null&&!v.isBlank())){counts[2]++;return;}
                Map<String,Integer> sourceLabels=new HashMap<>();for(int i=0;i<headers.size();i++){String base=headers.get(i).isBlank()?"Column "+(i+1):headers.get(i);int occurrence=sourceLabels.merge(base,1,Integer::sum);source.put(occurrence==1?base:base+" [column "+(i+1)+"]",at(values,i));}
                mapping.forEach((field,index)->fields.put(field,at(values,index)));
                String key=fields.get(type.keyField());
                if(key==null||key.isBlank()||key.length()>1000){counts[2]++;return;}
                mapping.forEach((field,index)->{String value=fields.get(field);profiles.get(field).accept(value);boolean blank=value==null||value.isBlank();profileChunk.add(new CmdbRecordFieldProfile(version.getId(),rowNo+1,field,blank?null:hash(value),blank));});
                Map<String,Object> payload=Map.of("fields",fields,"source",source);
                CmdbRecord record=new CmdbRecord(version.getId(),rowNo+1,key,write(payload));
                chunk.add(record);lookupChunk.addAll(lookupProjection.fromRecord(record,type.name()));counts[1]++;
                if(chunk.size()>=CHUNK_SIZE){recordWriter.saveChunk(version.getId(),List.copyOf(chunk),List.copyOf(profileChunk),List.copyOf(lookupChunk));chunk.clear();profileChunk.clear();lookupChunk.clear();}
            });
            if(!chunk.isEmpty()) recordWriter.saveChunk(version.getId(),List.copyOf(chunk),List.copyOf(profileChunk),List.copyOf(lookupChunk));
            Map<String,Long> distinctByField=new HashMap<>();for(Object[] stat:fieldProfileRepository.countDistinctValues(version.getId()))distinctByField.put((String)stat[0],((Number)stat[1]).longValue());
            Map<String,Object> profile=new LinkedHashMap<>();profiles.forEach((field,p)->{Map<String,Object> fieldData=p.toMap();long distinct=distinctByField.getOrDefault(field,0L);fieldData.put("distinctValues",distinct);fieldData.put("duplicateValues",Math.max(0,p.nonEmpty-distinct));profile.put(field,fieldData);});
            long distinct=records.countDistinctKeys(version.getId()); profile.put("candidateKey",Map.of("field",type.keyField(),"distinctValues",distinct,"duplicateValues",Math.max(0,counts[1]-distinct),"unique",counts[1]==distinct));
            Map<String,Object> diag=new LinkedHashMap<>();diag.put("profile",profile);diag.put("rejectedRows",counts[2]);
            if(counts[2]>0 || counts[1]==0) {
                recordWriter.deleteRows(version.getId());
                return versionDto(versionTransactions.fail(version.getId(),write(diag),counts[0],counts[1],counts[2],actor));
            }
            return versionDto(versionTransactions.completeAndActivate(version.getId(),sheet,Objects.toString(candidate.get("sheetName"),"CSV"),headerRow+1,write(mappingDetails(mapping,headers)),write(profile),counts[0],counts[1],actor));
        } catch(RuntimeException e){
            recordWriter.deleteRows(version.getId());
            return versionDto(versionTransactions.fail(version.getId(),"{\"code\":\"IMPORT_FAILED\",\"message\":\"The dataset could not be validated or persisted.\"}",counts[0],counts[1],Math.max(counts[2],counts[0]-counts[1]),actor));
        }
    }

    @Transactional
    public Map<String,Object> activate(String datasetKey,UUID versionId,String actor){
        CmdbDatasetType type=type(datasetKey); CmdbDataset dataset=datasets.lockByKey(type.name()).orElseThrow(()->new ResourceNotFoundException("CmdbDataset",type.name()));
        CmdbVersion next=versions.findByIdAndDatasetKey(versionId,type.name()).orElseThrow(()->new ResourceNotFoundException("CmdbVersion",versionId.toString()));
        if(!"READY".equals(next.getStatus()))throw new ValidationException("Only successfully processed versions can be activated");
        if(dataset.getActiveVersionId()!=null){CmdbVersion old=versions.findById(dataset.getActiveVersionId()).orElseThrow();old.deactivate();versions.saveAndFlush(old);}
        next.activate(); dataset.setActiveVersionId(next.getId()); versions.save(next);datasets.save(dataset);
        audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_VERSION_ACTIVATED","CMDB_VERSION",versionId.toString(),"dataset="+type.name());
        return versionDto(next);
    }

    @Transactional(readOnly=true)
    public Page<Map<String,Object>> history(String datasetKey,int page,int size){CmdbDatasetType type=type(datasetKey);return versions.findByDatasetKeyOrderByCreatedAtDesc(type.name(),PageRequest.of(Math.max(0,page),Math.max(1,Math.min(100,size)))).map(this::versionDto);}
    public Map<String,Object> active(String datasetKey){CmdbDatasetType type=type(datasetKey);CmdbDataset d=datasets.findById(type.name()).orElseThrow(()->new ResourceNotFoundException("CmdbDataset",type.name()));return d.getActiveVersionId()==null?null:versionDto(versions.findById(d.getActiveVersionId()).orElse(null));}
    public Map<String,Object> status(UUID id){return versionDto(versions.findById(id).orElseThrow(()->new ResourceNotFoundException("CmdbVersion",id.toString())));}

    private Inspection inspect(CmdbVersion v,CmdbDatasetType type,String ext){
        List<Map<String,Object>> candidates=new ArrayList<>(),fallbacks=new ArrayList<>();Map<Integer,String> sheets=new TreeMap<>();
        try { workbookReader.read(storage.resolve(v.getStoragePath()),ext,r->{if(r.rowNumber()<0){sheets.put(r.sheetIndex(),r.sheetName());return;}if(r.sheetIndex()==0)sheets.putIfAbsent(0,r.sheetName());if(r.rowNumber()>=SCAN_ROWS)return;Map<String,Object> suggestion=suggest(type,r.values());if(!suggestion.isEmpty())candidates.add(candidate(r.sheetIndex(),r.sheetName(),r.rowNumber(),r.values(),suggestion));else if(r.values().stream().filter(x->!x.isBlank()).count()>=2)fallbacks.add(candidate(r.sheetIndex(),r.sheetName(),r.rowNumber(),r.values(),Map.of()));}); }
        catch(RuntimeException e){throw new ValidationException("Workbook could not be inspected. Verify the file is readable and not encrypted.");}
        List<Map<String,Object>> worksheetList=sheets.entrySet().stream().map(e->Map.<String,Object>of("sheetIndex",e.getKey(),"sheetName",e.getValue())).toList();
        return new Inspection(worksheetList,candidates.isEmpty()?fallbacks:candidates);
    }
    private Map<String,Object> candidate(int sheet,String name,int row,List<String> headers,Map<String,Object> mapping){Map<String,Object> c=new LinkedHashMap<>();c.put("sheetIndex",sheet);c.put("sheetName",name);c.put("headerRow",row);c.put("headers",headers);c.put("suggestedMapping",mapping);c.put("matchedFields",mapping.size());return c;}
    private Map<String,Object> suggest(CmdbDatasetType type,List<String> headers){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<headers.size();i++){String normalized=normalize(headers.get(i));for(String field:type.fields())if(normalized.equals(normalize(field)) && !map.containsKey(field)){map.put(field,i);break;}}return map;}
    private void validateMapping(CmdbDatasetType type,List<String> headers,Map<String,Integer> mapping){if(mapping==null)throw new ValidationException("Column mapping is required");Set<Integer> used=new HashSet<>();for(var e:mapping.entrySet()){if(!type.fields().contains(e.getKey())||e.getValue()==null||e.getValue()<0||e.getValue()>=headers.size()||!used.add(e.getValue()))throw new ValidationException("Mapping contains an unknown field, invalid column, or duplicate source column");}for(String field:type.fields())if(type.required(field)&&!mapping.containsKey(field))throw new ValidationException("Required field is not mapped: "+field);}
    private Map<String,Object> profileSample(CmdbVersion v,CmdbDatasetType type,int sheet,int header,List<String> headers,Map<String,?> suggested){Map<String,Integer> m=new LinkedHashMap<>();suggested.forEach((k,val)->{if(val instanceof Number n)m.put(k,n.intValue());});for(var e:m.entrySet())if(!type.fields().contains(e.getKey())||e.getValue()<0||e.getValue()>=headers.size())throw new ValidationException("Preview mapping contains an invalid source column");Map<String,ColumnProfile> p=new LinkedHashMap<>();m.keySet().forEach(k->p.put(k,new ColumnProfile()));final int[] seen={0};readRows(v,sheet,header,(r,c)->{if(r>header&&seen[0]<500&&!blank(c)){for(var e:m.entrySet())p.get(e.getKey()).accept(at(c,e.getValue()));seen[0]++;}});Map<String,Object> out=new LinkedHashMap<>();p.forEach((k,val)->out.put(k,val.toMap()));out.put("sampleRows",seen[0]);return out;}
    private void readRows(CmdbVersion v,int sheet,int header,RowConsumer consumer){try{workbookReader.read(storage.resolve(v.getStoragePath()),extension(v.getOriginalFilename()),r->{if(r.sheetIndex()==sheet)consumer.accept(r.rowNumber(),r.values());});}catch(RuntimeException e){throw new ValidationException("Unable to stream selected worksheet");}}
    private Map<String,Object> candidate(CmdbVersion v,int sheet,int row){return readInspectionCandidates(v.getInspectionJson()).stream().filter(c->Objects.equals(((Number)c.get("sheetIndex")).intValue(),sheet)&&Objects.equals(((Number)c.get("headerRow")).intValue(),row)).findFirst().orElseThrow(()->new ValidationException("Selected sheet/header is not a detected candidate"));}
    private CmdbVersion requireDraft(UUID id){CmdbVersion v=versions.findById(id).orElseThrow(()->new ResourceNotFoundException("CmdbVersion",id.toString()));if(!"INSPECTING".equals(v.getStatus()))throw new ValidationException("This upload is no longer awaiting confirmation");return v;}
    private Map<String,Object> readInspection(String value){try{return value==null?Map.of():json.readValue(value,json.getTypeFactory().constructMapType(Map.class,String.class,Object.class));}catch(Exception e){return Map.of();}}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> readInspectionCandidates(String value){Object rows=readInspection(value).get("candidates");return rows instanceof List<?> list?(List<Map<String,Object>>)list:List.of();}
    private List<Map<String,Object>> readWorksheets(String value){Object rows=readInspection(value).get("worksheets");if(!(rows instanceof List<?> list))return List.of();return list.stream().filter(Map.class::isInstance).map(x->(Map<String,Object>)x).toList();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("Could not serialize CMDB metadata");}}
    private Map<String,Object> versionDto(CmdbVersion v){if(v==null)return null;Map<String,Object> m=new LinkedHashMap<>();m.put("id",v.getId());m.put("datasetId",v.getDatasetKey());m.put("fileName",v.getOriginalFilename());m.put("status",v.getStatus());m.put("active",v.isActive());m.put("recordCount",v.getProcessedRows());m.put("totalRows",v.getTotalRows());m.put("rejectedRows",v.getRejectedRows());m.put("sheetName",v.getSheetName());m.put("headerRow",v.getHeaderRow());m.put("createdAt",v.getCreatedAt());m.put("completedAt",v.getCompletedAt());m.put("mapping",v.getMappingJson());m.put("profile",v.getProfileJson());m.put("diagnostics",v.getDiagnosticsJson());return m;}
    private CmdbDatasetType type(String key){try{return CmdbDatasetType.valueOf(key.trim().toUpperCase(Locale.ROOT));}catch(Exception e){throw new ValidationException("Unknown CMDB dataset");}}
    private String extension(String filename){return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);}
    private List<String> strings(Object obj){if(obj instanceof List<?> l)return l.stream().map(Objects::toString).toList();return List.of();}
    private String at(List<String> values,int index){return index>=0&&index<values.size()&&values.get(index)!=null?values.get(index):"";}
    private boolean blank(List<String> values){return values.stream().allMatch(v->v==null||v.isBlank());}
    private String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private String hash(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}}
    private Map<String,Object> mappingDetails(Map<String,Integer> mapping,List<String> headers){Map<String,Object> details=new LinkedHashMap<>();mapping.forEach((field,index)->details.put(field,Map.of("sourceColumnIndex",index,"sourceHeader",headers.get(index))));return details;}
    private interface RowConsumer{void accept(int rowNumber,List<String> cells);}
    private record Inspection(List<Map<String,Object>> worksheets,List<Map<String,Object>> candidates){}
    private static class ColumnProfile {long nulls,nonEmpty,integer,decimal,date,other;void accept(String v){if(v==null||v.isBlank()){nulls++;return;}nonEmpty++;String x=v.trim();if(x.matches("[-+]?\\d+"))integer++;else if(x.matches("[-+]?(\\d+\\.\\d*|\\d*\\.\\d+)"))decimal++;else if(x.matches("\\d{1,4}[-/]\\d{1,2}[-/]\\d{1,4}"))date++;else other++;}Map<String,Object> toMap(){int categories=(integer>0?1:0)+(decimal>0?1:0)+(date>0?1:0)+(other>0?1:0);Map<String,Object> result=new LinkedHashMap<>();result.put("nullOrBlank",nulls);result.put("nonEmpty",nonEmpty);result.put("integerLike",integer);result.put("decimalLike",decimal);result.put("dateLike",date);result.put("other",other);result.put("typeConsistency",categories>1?"MIXED":"CONSISTENT");return result;}}
}
