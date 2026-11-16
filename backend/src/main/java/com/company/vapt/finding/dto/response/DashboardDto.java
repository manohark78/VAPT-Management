package com.company.vapt.finding.dto.response;

import com.company.vapt.finding.entity.Finding;
import com.company.vapt.report.domain.ReportSectionId;

import java.time.Instant;
import java.util.Map;

public record DashboardDto(String reportSectionId, Instant asOf, long openFindings, long closedFindings,
        Map<String,Long> riskRating, Map<String,Long> complianceStatus, Map<String,Long> applications,
        Map<String,Long> appTags, Map<String,Long> environments, Map<String,Long> riskScores,
        Map<String,Long> severities, Map<String,Long> remediation) { }
