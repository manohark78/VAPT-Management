package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingEvent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface FindingEventRepository extends JpaRepository<FindingEvent, UUID> {
    List<FindingEvent> findByFindingIdOrderByOccurredAtAsc(UUID findingId);
    long countByImportBatchId(UUID batchId);
    List<FindingEvent> findByFindingIdInAndEventTypeIn(Collection<UUID> findingIds, Collection<String> types);
    List<FindingEvent> findByFindingIdAndEventTypeOrderByOccurredAtDesc(UUID findingId, String type);
}
