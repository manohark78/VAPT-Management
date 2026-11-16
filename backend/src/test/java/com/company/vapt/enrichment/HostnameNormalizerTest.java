package com.company.vapt.enrichment;

import com.company.vapt.enrichment.service.HostnameNormalizationProperties;
import com.company.vapt.enrichment.service.HostnameNormalizer;
import com.company.vapt.enrichment.service.TargetClassifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HostnameNormalizerTest {
    @Test
    void doesNotApplyAnUnconfiguredFqdnRule() {
        HostnameNormalizationProperties properties = new HostnameNormalizationProperties();
        HostnameNormalizer normalizer = new HostnameNormalizer(properties);
        assertNull(normalizer.registrableHost("www.abcd.com"));
    }

    @Test
    void usesTheMostSpecificConfiguredSuffixAndSupportsExamples() {
        HostnameNormalizationProperties properties = new HostnameNormalizationProperties();
        properties.setSuffixes(List.of("com", "co.uk"));
        HostnameNormalizer normalizer = new HostnameNormalizer(properties);
        assertEquals("abcd", normalizer.registrableHost("www.abcd.com"));
        assertEquals("aakkass", normalizer.registrableHost("mup.aakkass.com"));
        assertEquals("kkod", normalizer.registrableHost("mhco1.kkod.com"));
        assertEquals("service", normalizer.registrableHost("edge.service.co.uk"));
        assertEquals("www.abcd.com", normalizer.normalizeDnsName("WWW.ABCD.COM."));
        assertEquals("web01", normalizer.registrableHost("company.web01.com"));
    }

    @Test
    void classifiesScalarTargetsWithoutNetworkResolution() {
        TargetClassifier classifier = new TargetClassifier();
        assertEquals("IP", classifier.classify("10.20.30.40"));
        assertEquals("IP", classifier.classify("2001:db8::1"));
        assertEquals("FQDN", classifier.classify("host.example.test"));
        assertEquals("HOSTNAME", classifier.classify("host01"));
        assertEquals("UNKNOWN", classifier.classify("  "));
    }
}
