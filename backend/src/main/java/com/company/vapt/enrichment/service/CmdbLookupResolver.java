package com.company.vapt.enrichment.service;

import com.company.vapt.cmdb.repository.CmdbRecordLookupRepository;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.ByteBuffer;
import java.util.stream.Collectors;

/** Batch-resolves hash keys and returns at most twenty candidate references per value. */
@Service
public class CmdbLookupResolver {
    private static final int MAX_CANDIDATE_REFERENCES = 20;
    private final CmdbRecordLookupRepository repository;
    private final CmdbLookupNormalizer normalizer;

    public CmdbLookupResolver(CmdbRecordLookupRepository repository, CmdbLookupNormalizer normalizer) {
        this.repository = repository; this.normalizer = normalizer;
    }

    public Map<String, MatchSet> resolve(UUID versionId, String field, Collection<String> normalizedValues) {
        Set<String> values = normalizedValues.stream().filter(v -> v != null && !v.isBlank()).collect(Collectors.toSet());
        if (values.isEmpty()) return Map.of();
        Map<String, String> valueByHash = new HashMap<>();
        values.forEach(value -> valueByHash.put(normalizer.hash(value), value));
        List<Object[]> counts = repository.countByHashes(versionId, field, valueByHash.keySet());
        Map<String, Long> countByHash = new HashMap<>();
        for (Object[] count : counts) countByHash.put(Objects.toString(count[0]), ((Number) count[1]).longValue());
        List<Object[]> samples = repository.candidateSamples(
                versionId, field, valueByHash.keySet(), MAX_CANDIDATE_REFERENCES);
        Map<String, List<Candidate>> candidatesByHash = new HashMap<>();
        for (Object[] row : samples) {
            String hash = Objects.toString(row[2]);
            String requested = valueByHash.get(hash);
            String normalized = readText(row[4]);
            if (requested == null || !requested.equals(normalized)) continue;
            UUID recordId = asUuid(row[1]);
            candidatesByHash.computeIfAbsent(hash, ignored -> new ArrayList<>())
                    .add(new Candidate(recordId, ((Number) row[3]).intValue()));
        }
        Map<String, MatchSet> result = new HashMap<>();
        for (var entry : valueByHash.entrySet()) {
            long count = countByHash.getOrDefault(entry.getKey(), 0L);
            List<Candidate> candidates = candidatesByHash.getOrDefault(entry.getKey(), List.of());
            if (count == 0 || candidates.isEmpty()) result.put(entry.getValue(), MatchSet.unmatched());
            else if (count == 1 && candidates.size() == 1) result.put(entry.getValue(), MatchSet.matched(candidates.getFirst()));
            else result.put(entry.getValue(), MatchSet.ambiguous(count, candidates));
        }
        return result;
    }

    private String readText(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Clob clob) {
            try {
                long length = clob.length();
                if (length > Integer.MAX_VALUE) return null;
                return clob.getSubString(1, (int) length);
            } catch (java.sql.SQLException e) { throw new IllegalStateException("Could not read a CMDB lookup value"); }
        }
        return value.toString();
    }

    private UUID asUuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(Objects.toString(value));
    }

    public record Candidate(UUID recordId, int sourceRowNumber) { }
    public record MatchSet(String status, long candidateCount, Candidate uniqueCandidate, List<Candidate> candidates) {
        static MatchSet unmatched() { return new MatchSet("UNMATCHED", 0, null, List.of()); }
        static MatchSet matched(Candidate candidate) { return new MatchSet("MATCHED", 1, candidate, List.of(candidate)); }
        static MatchSet ambiguous(long count, List<Candidate> candidates) {
            return new MatchSet("AMBIGUOUS", count, null, List.copyOf(candidates));
        }
    }
}
