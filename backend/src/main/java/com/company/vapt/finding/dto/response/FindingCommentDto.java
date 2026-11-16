package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.finding.entity.FindingComment;
import java.time.Instant;
import java.util.UUID;
public record FindingCommentDto(UUID id, UUID findingId, String ipAddress, String applicationName,
        String appTag, String text, String actorUserId, Instant createdAt) {
    public static FindingCommentDto from(FindingComment c) { return new FindingCommentDto(c.getId(),c.getFinding()==null?null:c.getFinding().getId(),c.getIpAddress(),c.getApplicationName(),c.getAppTag(),c.getCommentText(),c.getActorUserId(),c.getCreatedAt()); }
}
