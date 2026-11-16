package com.company.vapt.cmdb.service;

import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.cmdb.entity.CmdbRecordFieldProfile;
import com.company.vapt.cmdb.entity.CmdbRecordLookup;
import com.company.vapt.cmdb.repository.CmdbRecordFieldProfileRepository;
import com.company.vapt.cmdb.repository.CmdbRecordLookupRepository;
import com.company.vapt.cmdb.repository.CmdbRecordRepository;
import com.company.vapt.cmdb.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class CmdbRecordPersistenceService {
 private final CmdbRecordRepository repository;
 private final CmdbRecordFieldProfileRepository profileRepository;
 private final CmdbRecordLookupRepository lookupRepository;
 public CmdbRecordPersistenceService(CmdbRecordRepository repository,CmdbRecordFieldProfileRepository profileRepository,CmdbRecordLookupRepository lookupRepository){this.repository=repository;this.profileRepository=profileRepository;this.lookupRepository=lookupRepository;}
 @Transactional public void saveChunk(UUID versionId,List<CmdbRecord> rows,List<CmdbRecordFieldProfile> profileRows,List<CmdbRecordLookup> lookupRows){repository.saveAllAndFlush(rows);profileRepository.saveAllAndFlush(profileRows);if(!lookupRows.isEmpty())lookupRepository.saveAllAndFlush(lookupRows);}
 @Transactional public void deleteRows(UUID versionId){profileRepository.deleteVersionProfiles(versionId);lookupRepository.deleteByVersionId(versionId);repository.deleteVersionRows(versionId);}
}
