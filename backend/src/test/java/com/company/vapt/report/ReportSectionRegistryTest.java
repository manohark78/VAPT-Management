package com.company.vapt.report;

import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.report.domain.ReconciliationType;
import com.company.vapt.report.domain.ReportSectionId;
import com.company.vapt.report.domain.ReportSectionMetadata;
import com.company.vapt.report.domain.ReportSectionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReportSectionRegistry - Domain & Registry Unit Tests")
class ReportSectionRegistryTest {

    private ReportSectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ReportSectionRegistry();
    }

    @Test
    @DisplayName("Should register exactly the six canonical report sections")
    void testAllSixReportSectionsRegistered() {
        List<ReportSectionMetadata> sections = registry.getAllSections();
        assertEquals(6, sections.size(), "Registry must contain exactly 6 report sections");

        List<ReportSectionId> expected = List.of(
                ReportSectionId.GDP_VA_REPORT,
                ReportSectionId.INTERNAL_VA_BRINQA,
                ReportSectionId.WIZ,
                ReportSectionId.VERIZON_26_NETWORK_PT,
                ReportSectionId.PT_VERIZON_GCP,
                ReportSectionId.APPSEC_FINDINGS_PT
        );

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), sections.get(i).id(), "Section ordering must match canonical specification");
            assertTrue(sections.get(i).enabled(), "All sections should be enabled by default");
        }
    }

    @Test
    @DisplayName("GDP_VA_REPORT and INTERNAL_VA_BRINQA must remain strictly independent")
    void testGdpAndInternalBrinqaIndependence() {
        ReportSectionMetadata gdp = registry.getMetadata(ReportSectionId.GDP_VA_REPORT);
        ReportSectionMetadata brinqa = registry.getMetadata(ReportSectionId.INTERNAL_VA_BRINQA);

        assertNotNull(gdp);
        assertNotNull(brinqa);
        assertNotEquals(gdp.id(), brinqa.id());
        assertNotEquals(gdp.displayName(), brinqa.displayName());
        assertTrue(registry.areDistinctSections(gdp.id(), brinqa.id()));
        assertFalse(registry.areDistinctSections(gdp.id(), gdp.id()));
    }

    @Test
    @DisplayName("Should correctly reflect reconciliation types for general VA/PT vs AppSec")
    void testReconciliationTypeMapping() {
        // First 5 general VA/PT must be UID_BASED
        assertEquals(ReconciliationType.UID_BASED, registry.getMetadata(ReportSectionId.GDP_VA_REPORT).reconciliationType());
        assertEquals(ReconciliationType.UID_BASED, registry.getMetadata(ReportSectionId.INTERNAL_VA_BRINQA).reconciliationType());
        assertEquals(ReconciliationType.UID_BASED, registry.getMetadata(ReportSectionId.WIZ).reconciliationType());
        assertEquals(ReconciliationType.UID_BASED, registry.getMetadata(ReportSectionId.VERIZON_26_NETWORK_PT).reconciliationType());
        assertEquals(ReconciliationType.UID_BASED, registry.getMetadata(ReportSectionId.PT_VERIZON_GCP).reconciliationType());

        // AppSec must be FINDING_ID_BASED
        assertEquals(ReconciliationType.FINDING_ID_BASED, registry.getMetadata(ReportSectionId.APPSEC_FINDINGS_PT).reconciliationType());
    }

    @Test
    @DisplayName("Should resolve case-insensitively and reject unknown identifiers")
    void testSectionResolutionAndRejection() {
        assertEquals(ReportSectionId.GDP_VA_REPORT, registry.resolve("GDP_VA_REPORT"));
        assertEquals(ReportSectionId.GDP_VA_REPORT, registry.resolve("gdp_va_report"));
        assertEquals(ReportSectionId.INTERNAL_VA_BRINQA, registry.resolve("internal_va_brinqa"));

        // Unknown identifier should throw ResourceNotFoundException
        ResourceNotFoundException notFoundEx = assertThrows(
                ResourceNotFoundException.class,
                () -> registry.resolve("NON_EXISTENT_SECTION")
        );
        assertTrue(notFoundEx.getMessage().contains("NON_EXISTENT_SECTION"));

        // Blank/null identifier should throw ValidationException
        assertThrows(ValidationException.class, () -> registry.resolve(""));
        assertThrows(ValidationException.class, () -> registry.resolve("   "));
        assertThrows(ValidationException.class, () -> registry.resolve(null));
    }

    @Test
    @DisplayName("isValid should accurately check valid and invalid identifiers")
    void testIsValid() {
        assertTrue(registry.isValid("GDP_VA_REPORT"));
        assertTrue(registry.isValid("INTERNAL_VA_BRINQA"));
        assertTrue(registry.isValid("wiz"));
        assertFalse(registry.isValid("UNKNOWN"));
        assertFalse(registry.isValid(""));
        assertFalse(registry.isValid(null));
    }
}
