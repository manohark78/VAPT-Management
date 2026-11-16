package com.company.vapt.enrichment.service;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Normalizes DNS names only using explicitly configured public suffixes. */
@Component
public class HostnameNormalizer {
    private final HostnameNormalizationProperties properties;
    public HostnameNormalizer(HostnameNormalizationProperties properties) { this.properties = properties; }

    public String normalizeDnsName(String value) {
        if (value == null || value.isBlank()) return null;
        String candidate = value.trim();
        while (candidate.endsWith(".")) candidate = candidate.substring(0, candidate.length() - 1);
        try { return IDN.toASCII(candidate).toLowerCase(Locale.ROOT); }
        catch (IllegalArgumentException invalidName) { return candidate.toLowerCase(Locale.ROOT); }
    }

    /** Returns the label immediately before a configured suffix (eTLD+1), or null if unknown. */
    public String registrableHost(String fqdn) {
        String normalized = normalizeDnsName(fqdn);
        if (normalized == null || !normalized.contains(".")) return null;
        String[] labels = normalized.split("\\.");
        List<String> suffixes = properties.getSuffixes().stream()
                .filter(s -> s != null && !s.isBlank()).map(this::normalizeDnsName).toList();
        String selected = null;
        int selectedLength = -1;
        for (String suffix : suffixes) {
            String[] suffixLabels = suffix.split("\\.");
            if (labels.length <= suffixLabels.length) continue;
            int start = labels.length - suffixLabels.length;
            if (String.join(".", Arrays.copyOfRange(labels, start, labels.length)).equals(suffix)
                    && suffixLabels.length > selectedLength) {
                selected = labels[start - 1];
                selectedLength = suffixLabels.length;
            }
        }
        return selected;
    }
}
