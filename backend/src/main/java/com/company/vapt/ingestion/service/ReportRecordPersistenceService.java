package com.company.vapt.ingestion.service;

import com.company.vapt.ingestion.repository.ImportBatchRepository;
import com.company.vapt.ingestion.repository.ReportImportRecordRepository;

import com.company.vapt.ingestion.entity.ImportBatch;
import com.company.vapt.ingestion.entity.ReportImportRecord;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.UUID;

/** Writes bounded groups of validated source rows without holding a transaction for the full import. */
@Service
public class ReportRecordPersistenceService {

    private final ReportImportRecordRepository recordRepository;
    private final ImportBatchRepository batchRepository;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public ReportRecordPersistenceService(ReportImportRecordRepository recordRepository,
                                          ImportBatchRepository batchRepository) {
        this.recordRepository = recordRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional
    public void saveChunk(UUID batchId, List<SourceRow> rows) {
        ImportBatch batch = batchRepository.getReferenceById(batchId);
        List<ReportImportRecord> records = rows.stream()
                .map(row -> new ReportImportRecord(batch, row.rowNumber(), row.identity(), json(row.fields())))
                .toList();
        recordRepository.saveAllAndFlush(records);
    }

    @Transactional(readOnly = true)
    public boolean appSecIdentityAlreadyExists(UUID batchId, Collection<String> identities) {
        return !identities.isEmpty() && recordRepository.existsByImportBatchIdAndSourceIdentityIn(batchId, identities);
    }

    @Transactional
    public void deleteBatchRows(UUID batchId) {
        recordRepository.deleteAllForBatch(batchId);
    }

    private String json(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Could not serialize validated report row", e);
        }
    }

    public record SourceRow(int rowNumber, String identity, Map<String, String> fields) { }
}
