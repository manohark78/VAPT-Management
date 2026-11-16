package com.company.vapt.user.dto.response;

import java.util.List;

public class AuthUserDto {

    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private List<UserScopeDto> scopes;

    public AuthUserDto() {
    }

    public AuthUserDto(Long id, String email, String fullName, String role, String status, List<UserScopeDto> scopes) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.status = status;
        this.scopes = scopes;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getRole() {
        return role;
    }

    public String getStatus() {
        return status;
    }

    public List<UserScopeDto> getScopes() {
        return scopes;
    }
}
