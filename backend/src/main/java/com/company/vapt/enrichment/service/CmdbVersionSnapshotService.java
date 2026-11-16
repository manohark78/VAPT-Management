package com.company.vapt.enrichment.service;

import com.company.vapt.cmdb.repository.CmdbDatasetRepository;
import com.company.vapt.cmdb.repository.CmdbVersionRepository;

import com.company.vapt.cmdb.entity.CmdbDataset;
import com.company.vapt.cmdb.entity.CmdbVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Locks dataset pointers in a stable order so all three active versions form one snapshot. */
@Service
public class CmdbVersionSnapshotService {
    private static final List<String> DATASETS = List.of("APP_INVENTORY", "HEADCOUNT", "JAS");
    private final CmdbDatasetRepository datasets;
    private final CmdbVersionRepository versions;

    public CmdbVersionSnapshotService(CmdbDatasetRepository datasets, CmdbVersionRepository versions) {
        this.datasets = datasets; this.versions = versions;
    }

    @Transactional
    public CmdbVersionSnapshot capture() {
        Map<String, CmdbDataset> locked = new LinkedHashMap<>();
        for (String key : DATASETS) datasets.lockByKey(key).ifPresent(dataset -> locked.put(key, dataset));
        List<String> missing = new ArrayList<>();
        Map<String, UUID> ids = new LinkedHashMap<>();
        for (String key : DATASETS) {
            CmdbDataset dataset = locked.get(key);
            if (dataset == null || dataset.getActiveVersionId() == null) {
                missing.add(key);
                continue;
            }
            UUID id = dataset.getActiveVersionId();
            CmdbVersion version = versions.findByIdAndDatasetKey(id, key).orElse(null);
            if (version == null || !"READY".equals(version.getStatus())) {
                missing.add(key);
                continue;
            }
            ids.put(key, id);
        }
        return new CmdbVersionSnapshot(ids.get("JAS"), ids.get("HEADCOUNT"),
                ids.get("APP_INVENTORY"), List.copyOf(missing));
    }
}
