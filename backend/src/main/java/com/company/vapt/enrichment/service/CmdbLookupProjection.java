package com.company.vapt.enrichment.service;

import com.company.vapt.cmdb.entity.CmdbRecord;
import com.company.vapt.cmdb.entity.CmdbRecordLookup;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CmdbLookupProjection {
    private static final int MAX_LOOKUP_VALUE_LENGTH = 4096;
    private static final Map<String, List<String>> INDEXED_FIELDS = Map.of(
            "JAS", List.of("IP Address", "FQDN", "Host", "OwnedBy", "Application ID"),
            "HEADCOUNT", List.of("Preferred Name"),
            "APP_INVENTORY", List.of("ApplicationId"));
    private final ObjectMapper json = JsonMapper.builder().build();
    private final CmdbLookupNormalizer normalizer;

    public CmdbLookupProjection(CmdbLookupNormalizer normalizer) { this.normalizer = normalizer; }

    @SuppressWarnings("unchecked")
    public List<CmdbRecordLookup> fromRecord(CmdbRecord record, String datasetKey) {
        try {
            Map<String, Object> payload = json.readValue(record.getValuesJson(),
                    json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            Object fieldObject = payload.get("fields");
            if (!(fieldObject instanceof Map<?, ?> fields)) return List.of();
            List<CmdbRecordLookup> rows = new ArrayList<>();
            for (String field : INDEXED_FIELDS.getOrDefault(datasetKey, List.of())) {
                Object raw = fields.get(field);
                String value = raw == null ? null : raw.toString();
                String normalized = normalizer.normalize(field, value);
                if (normalized != null && normalized.length() <= MAX_LOOKUP_VALUE_LENGTH) rows.add(new CmdbRecordLookup(record.getVersionId(), record.getId(),
                        record.getSourceRowNumber(), field, normalizer.hash(normalized), normalized));
            }
            return rows;
        } catch (Exception e) {
            throw new IllegalStateException("Could not project a validated CMDB record", e);
        }
    }
}
