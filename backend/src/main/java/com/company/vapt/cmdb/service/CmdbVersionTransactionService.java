package com.company.vapt.cmdb.service;

import com.company.vapt.cmdb.entity.CmdbDataset;
import com.company.vapt.cmdb.entity.CmdbLookupIndexState;
import com.company.vapt.cmdb.entity.CmdbVersion;
import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.cmdb.repository.CmdbLookupIndexStateRepository;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.cmdb.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Keeps chunked record writes outside the final all-or-nothing active-pointer transaction. */
@Service
public class CmdbVersionTransactionService {
 private final CmdbVersionRepository versions; private final CmdbDatasetRepository datasets; private final AuditService audit; private final CmdbLookupIndexStateRepository lookupIndexStates;
 public CmdbVersionTransactionService(CmdbVersionRepository versions,CmdbDatasetRepository datasets,AuditService audit,CmdbLookupIndexStateRepository lookupIndexStates){this.versions=versions;this.datasets=datasets;this.audit=audit;this.lookupIndexStates=lookupIndexStates;}
 @Transactional public void markProcessing(UUID id,String actor){CmdbVersion v=versions.findById(id).orElseThrow();v.markProcessing();versions.save(v);audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_IMPORT_PROCESSING_STARTED","CMDB_VERSION",id.toString(),"dataset="+v.getDatasetKey());}
 @Transactional public CmdbVersion completeAndActivate(UUID id,int sheet,String sheetName,int header,String mapping,String profile,long total,long processed,String actor){
  CmdbVersion version=versions.findById(id).orElseThrow();version.complete(sheet,sheetName,header,mapping,profile,total,processed);version.setCounts(total,processed,0);
  CmdbDataset dataset=datasets.lockByKey(version.getDatasetKey()).orElseThrow();
  if(dataset.getActiveVersionId()!=null){CmdbVersion old=versions.findById(dataset.getActiveVersionId()).orElseThrow();old.deactivate();versions.saveAndFlush(old);}
  version.activate();dataset.setActiveVersionId(version.getId());versions.save(version);datasets.save(dataset);
  lookupIndexStates.save(new CmdbLookupIndexState(version.getId(),processed));
  audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_VERSION_ACTIVATED","CMDB_VERSION",id.toString(),"dataset="+version.getDatasetKey());
  return version;
 }
 @Transactional public CmdbVersion fail(UUID id,String diagnostics,long total,long processed,long rejected,String actor){
  CmdbVersion version=versions.findById(id).orElseThrow();version.fail(diagnostics);version.setCounts(total,processed,rejected);versions.save(version);
  audit.recordInWorkflowTransaction(actor,"ADMIN","CMDB_IMPORT_FAILED","CMDB_VERSION",id.toString(),"dataset="+version.getDatasetKey()+"; rejectedRows="+rejected);
  return version;
 }
}
