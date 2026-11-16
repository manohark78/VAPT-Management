package com.company.vapt.enrichment.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Component
public class CmdbLookupNormalizer {
    private final HostnameNormalizer hostnameNormalizer;
    public CmdbLookupNormalizer(HostnameNormalizer hostnameNormalizer) { this.hostnameNormalizer = hostnameNormalizer; }

    public String normalize(String field, String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return switch (field) {
            case "FQDN", "Host" -> hostnameNormalizer.normalizeDnsName(trimmed);
            case "IP Address" -> trimmed;
            default -> trimmed.toLowerCase(Locale.ROOT);
        };
    }

    public String hash(String normalizedValue) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
}
