package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;
import java.time.Instant;
import java.util.UUID;
public record FindingHistoryItemDto(String kind, String action, String actorUserId, String detail,
        UUID referenceId,
        Instant at, String status) { }
