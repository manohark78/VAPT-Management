package com.company.vapt.finding.repository;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingRca;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FindingRcaRepository extends JpaRepository<FindingRca, UUID> {
    List<FindingRca> findByFindingIdOrderBySubmittedAtDesc(UUID findingId);
    boolean existsByReopenEventId(UUID eventId);
}
