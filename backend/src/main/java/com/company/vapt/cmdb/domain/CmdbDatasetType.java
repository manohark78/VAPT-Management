package com.company.vapt.cmdb.domain;

import java.util.List;

public enum CmdbDatasetType {
    HEADCOUNT("TuHeadCount EE & CWR", List.of("Preferred Name", "Associated's Email", "CM4", "CM5", "CM6"), "Associated's Email"),
    JAS("JAS Inventory", List.of("Host", "IP Address", "FQDN", "Tu_AppName", "Environment", "OwnedBy", "Application ID"), "IP Address"),
    APP_INVENTORY("ApplicationInventory", List.of("ApplicationId", "Plan Application Owner 1", "Plan Application Owner 2"), "ApplicationId");

    private final String displayName;
    private final List<String> fields;
    private final String keyField;
    CmdbDatasetType(String displayName, List<String> fields, String keyField) {
        this.displayName = displayName; this.fields = fields; this.keyField = keyField;
    }
    public String displayName() { return displayName; }
    public List<String> fields() { return fields; }
    public String keyField() { return keyField; }
    public boolean required(String field) {
        return switch (this) {
            case HEADCOUNT -> field.equals("Associated's Email") || field.equals("Preferred Name");
            case JAS -> field.equals("Host") || field.equals("IP Address") || field.equals("FQDN") || field.equals("Application ID");
            case APP_INVENTORY -> field.equals("ApplicationId");
        };
    }
}
