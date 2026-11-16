package com.company.vapt.enrichment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Explicit suffix rules; empty by default to avoid assuming one DNS convention. */
@Component
@ConfigurationProperties(prefix = "vapt.enrichment.hostname-normalization")
public class HostnameNormalizationProperties {
    private List<String> suffixes = new ArrayList<>();
    public List<String> getSuffixes() { return suffixes; }
    public void setSuffixes(List<String> suffixes) { this.suffixes = suffixes == null ? new ArrayList<>() : suffixes; }
}
