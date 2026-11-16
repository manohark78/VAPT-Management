package com.company.vapt.user.dto.response;

public class UserScopeDto {

    private Long id;
    private String ipAddress;
    private String applicationName;

    public UserScopeDto() {
    }

    public UserScopeDto(Long id, String ipAddress, String applicationName) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.applicationName = applicationName;
    }

    public Long getId() {
        return id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getApplicationName() {
        return applicationName;
    }
}
