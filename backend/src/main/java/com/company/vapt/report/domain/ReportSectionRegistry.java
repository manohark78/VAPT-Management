package com.company.vapt.report.domain;

import com.company.vapt.report.entity.ReportSection;

import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Central registry and domain service for the six supported VAPT report sections.
 * Guarantees immutability, canonical lookup, unknown identifier rejection, and
 * strict independence between sections (particularly GDP_VA_REPORT and INTERNAL_VA_BRINQA).
 */
@Component
public class ReportSectionRegistry {

    private final Map<String, ReportSectionMetadata> sectionCache;

    public ReportSectionRegistry() {
        Map<String, ReportSectionMetadata> map = new LinkedHashMap<>();
        for (ReportSectionId id : ReportSectionId.values()) {
            map.put(id.name(), ReportSectionMetadata.from(id));
        }
        this.sectionCache = Collections.unmodifiableMap(map);
    }

    /**
     * Returns all six registered canonical report sections in defined display order.
     */
    public List<ReportSectionMetadata> getAllSections() {
        return new ArrayList<>(sectionCache.values());
    }

    /**
     * Returns all currently enabled report sections.
     */
    public List<ReportSectionMetadata> getEnabledSections() {
        return sectionCache.values().stream()
                .filter(ReportSectionMetadata::enabled)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves section metadata by canonical enum identifier.
     */
    public ReportSectionMetadata getMetadata(ReportSectionId sectionId) {
        if (sectionId == null) {
            throw new ValidationException("Report section ID must not be null");
        }
        return sectionCache.get(sectionId.name());
    }

    /**
     * Retrieves section metadata by string identifier, validating its existence.
     */
    public ReportSectionMetadata getMetadata(String identifier) {
        ReportSectionId id = resolve(identifier);
        return sectionCache.get(id.name());
    }

    /**
     * Resolves and validates a string identifier to a canonical ReportSectionId.
     * Throws ResourceNotFoundException if the identifier is unknown.
     * Throws ValidationException if the identifier is empty.
     */
    public ReportSectionId resolve(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new ValidationException("Report section identifier must not be empty");
        }
        String normalized = identifier.trim().toUpperCase();
        try {
            return ReportSectionId.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("ReportSection", identifier);
        }
    }

    /**
     * Checks if a string identifier is a recognized canonical report section.
     */
    public boolean isValid(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return false;
        }
        try {
            ReportSectionId.valueOf(identifier.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Verifies that two section identifiers represent strictly distinct report sections.
     * Specifically validates that GDP_VA_REPORT and INTERNAL_VA_BRINQA are not treated as aliases.
     */
    public boolean areDistinctSections(ReportSectionId a, ReportSectionId b) {
        return a != null && b != null && a != b;
    }
}
