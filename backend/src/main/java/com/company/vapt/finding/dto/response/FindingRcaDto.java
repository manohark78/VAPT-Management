package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingRca;
import java.time.Instant;
import java.util.UUID;
public record FindingRcaDto(UUID id, UUID findingId, String narrative, String status,
        String submittedByUserId, Instant submittedAt, String reviewedByUserId, Instant reviewedAt, String reviewNote) {
    public static FindingRcaDto from(FindingRca r) { return new FindingRcaDto(r.getId(),r.getFinding().getId(),r.getNarrative(),r.getStatus(),r.getSubmittedByUserId(),r.getSubmittedAt(),r.getReviewedByUserId(),r.getReviewedAt(),r.getReviewNote()); }
}
