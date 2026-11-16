package com.company.vapt.enrichment.service;

import java.util.List;
import java.util.UUID;

public record CmdbVersionSnapshot(UUID jasVersionId, UUID headcountVersionId,
                                  UUID appInventoryVersionId, List<String> missingDatasets) {
    public boolean complete() { return missingDatasets.isEmpty(); }
}
