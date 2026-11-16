package com.company.vapt.user.dto.request;

public class AssignScopeRequest {

    private String ipAddress;
    private String applicationName;

    public AssignScopeRequest() {
    }

    public AssignScopeRequest(String ipAddress, String applicationName) {
        this.ipAddress = ipAddress;
        this.applicationName = applicationName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
