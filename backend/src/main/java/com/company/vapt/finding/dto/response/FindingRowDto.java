package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;

import com.company.vapt.finding.entity.FindingObservation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record FindingRowDto(UUID findingId, String sourceUid, UUID importBatchId, Integer sourceRowNumber,
        String classification, String lifecycleState, Instant observedAt, String type, String port,
        String riskScore, String riskRating, String complianceStatus, String severity, String results,
        String targets, String displayName, String appTag, String ipAddress, String applicationName,
        String environment, String ownedBy, String applicationId, String headcountEmail, String cm4,
        String cm5, String cm6, String planApplicationOwner1, String planApplicationOwner2,
        String remediationAction, LocalDate remediationDate,
        Map<String, String> sourceValues) {
    public static FindingRowDto from(FindingObservation o, String action, LocalDate actionDate,
                                     Map<String,String> sourceValues) {
        return new FindingRowDto(o.getFinding()==null?null:o.getFinding().getId(),
                o.getFinding()==null?(o.getSourceRecord()==null?null:o.getSourceRecord().getSourceIdentity()):o.getFinding().getSourceUid(),
                o.getImportBatch().getId(), o.getSourceRecord()==null?null:o.getSourceRecord().getSourceRowNumber(),
                o.getClassification(), o.getFinding()==null?"UNMATCHED":o.getFinding().getLifecycleState(), o.getObservedAt(),
                o.getFindingType(),o.getPort(),o.getRiskScore(),o.getRiskRating(),o.getComplianceStatus(),o.getSeverity(),o.getResults(),o.getTargets(),
                o.getDisplayName(),o.getAppTag(),o.getIpAddress(),o.getApplicationName(),o.getEnvironment(),o.getOwnedBy(),o.getApplicationId(),
                o.getHeadcountEmail(),o.getCm4(),o.getCm5(),o.getCm6(),o.getPlanApplicationOwner1(),o.getPlanApplicationOwner2(),
                action,actionDate,sourceValues);
    }

    public FindingRowDto withLifecycleState(String state) {
        return new FindingRowDto(findingId,sourceUid,importBatchId,sourceRowNumber,classification,state,observedAt,
                type,port,riskScore,riskRating,complianceStatus,severity,results,targets,displayName,appTag,ipAddress,
                applicationName,environment,ownedBy,applicationId,headcountEmail,cm4,cm5,cm6,planApplicationOwner1,
                planApplicationOwner2,remediationAction,remediationDate,sourceValues);
    }
}
