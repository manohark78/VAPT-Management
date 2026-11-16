package com.company.vapt.enrichment.service;

import com.company.vapt.cmdb.entity.CmdbLookupIndexState;
import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.cmdb.entity.CmdbRecordLookup;
import com.company.vapt.cmdb.entity.CmdbVersion;
import com.company.vapt.cmdb.repository.CmdbLookupIndexStateRepository;
import com.company.vapt.cmdb.repository.CmdbRecordLookupRepository;
import com.company.vapt.cmdb.repository.CmdbRecordRepository;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;

import com.company.vapt.cmdb.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the lookup projection for pre-V6 immutable CMDB versions without loading a full dataset. */
@Service
public class CmdbLookupIndexService {
    private final CmdbVersionRepository versions;
    private final CmdbRecordRepository records;
    private final CmdbRecordLookupRepository lookups;
    private final CmdbLookupIndexStateRepository states;
    private final CmdbLookupProjection projection;
    private final EntityManager entityManager;

    public CmdbLookupIndexService(CmdbVersionRepository versions, CmdbRecordRepository records,
                                  CmdbRecordLookupRepository lookups, CmdbLookupIndexStateRepository states,
                                  CmdbLookupProjection projection, EntityManager entityManager) {
        this.versions = versions; this.records = records; this.lookups = lookups;
        this.states = states; this.projection = projection; this.entityManager = entityManager;
    }

    @Transactional
    public void ensureIndexed(UUID versionId) {
        CmdbVersion version = versions.lockById(versionId)
                .orElseThrow(() -> new IllegalStateException("A pinned CMDB version no longer exists"));
        if (!"READY".equals(version.getStatus())) throw new IllegalStateException("A pinned CMDB version is not ready");
        long recordCount = records.countVersionRows(versionId);
        CmdbLookupIndexState state = states.findById(versionId).orElse(null);
        if (state != null && state.getIndexedRecordCount() == recordCount) return;

        lookups.deleteByVersionId(versionId);
        states.deleteById(versionId);
        int lastRow = 0;
        long indexedRecords = 0;
        while (true) {
            List<CmdbRecord> chunk = records.findByVersionIdAndSourceRowNumberGreaterThanOrderBySourceRowNumberAsc(versionId, lastRow, PageRequest.of(0, 250));
            if (chunk.isEmpty()) break;
            List<CmdbRecordLookup> projected = new ArrayList<>();
            for (CmdbRecord record : chunk) {
                projected.addAll(projection.fromRecord(record, version.getDatasetKey()));
                lastRow = record.getSourceRowNumber();
            }
            if (!projected.isEmpty()) lookups.saveAll(projected);
            indexedRecords += chunk.size();
            entityManager.flush();
            entityManager.clear();
        }
        if (indexedRecords != recordCount) throw new IllegalStateException("CMDB lookup indexing did not cover the pinned version");
        states.save(new CmdbLookupIndexState(versionId, indexedRecords));
        entityManager.flush();
    }
}
