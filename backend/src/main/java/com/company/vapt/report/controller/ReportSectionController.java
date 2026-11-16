package com.company.vapt.report.controller;

import com.company.vapt.report.repository.ReportSectionRepository;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.report.dto.response.ReportSectionDto;
import com.company.vapt.report.domain.ReportSectionRegistry;
import com.company.vapt.report.entity.ReportSection;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for report section metadata.
 * Report-section catalog metadata remains ADMIN-only until an explicit section grant policy exists.
 */
@RestController
@RequestMapping("/api/reports/sections")
@PreAuthorize("hasRole('ADMIN')")
public class ReportSectionController {

    private final ReportSectionRepository reportSectionRepository;
    private final ReportSectionRegistry reportSectionRegistry;

    public ReportSectionController(ReportSectionRepository reportSectionRepository,
                                   ReportSectionRegistry reportSectionRegistry) {
        this.reportSectionRepository = reportSectionRepository;
        this.reportSectionRegistry = reportSectionRegistry;
    }

    /**
     * GET /api/reports/sections
     * Admin: returns all sections (enabled and disabled).
     * Regular users: returns only enabled sections.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ReportSectionDto>>> listSections(
            org.springframework.security.core.Authentication authentication) {

        String correlationId = correlationId();
        List<ReportSection> sections;

        boolean isAdmin = authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            sections = reportSectionRepository.findAll();
        } else {
            sections = reportSectionRepository.findByEnabledTrue();
        }

        List<ReportSectionDto> dtos = sections.stream()
                .map(ReportSectionDto::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }

    /**
     * GET /api/reports/sections/{sectionId}
     * Returns metadata for a single canonical report section.
     */
    @GetMapping("/{sectionId}")
    public ResponseEntity<ApiResponse<ReportSectionDto>> getSection(@PathVariable String sectionId) {
        String correlationId = correlationId();

        // Validate that the identifier is a known canonical section
        reportSectionRegistry.resolve(sectionId);

        ReportSection section = reportSectionRepository.findById(sectionId.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("ReportSection", sectionId));

        boolean isAdmin = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !section.isEnabled()) throw new ResourceNotFoundException("ReportSection", sectionId);

        return ResponseEntity.ok(ApiResponse.ok(ReportSectionDto.from(section), correlationId));
    }

    private String correlationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "unknown";
    }
}
