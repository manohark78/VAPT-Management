package com.company.vapt.user.controller;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.user.dto.request.AssignScopeRequest;
import com.company.vapt.user.dto.response.AuthUserDto;
import com.company.vapt.user.dto.request.CreateUserRequest;
import com.company.vapt.user.dto.response.AdminCreatedUserDto;
import com.company.vapt.user.dto.response.UserScopeDto;
import com.company.vapt.user.service.UserScopeService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserScopeService userScopeService;

    public AdminUserController(UserScopeService userScopeService) {
        this.userScopeService = userScopeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuthUserDto>>> getAllUsers() {
        String correlationId = getCorrelationId();
        List<AuthUserDto> users = userScopeService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.ok(users, correlationId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCreatedUserDto>> createUser(@Valid @RequestBody CreateUserRequest request,
                                                               Authentication authentication) {
        String correlationId = getCorrelationId();
        String adminActor = authentication != null ? authentication.getName() : "ADMIN";
        AdminCreatedUserDto created = userScopeService.createUser(request, adminActor);
        return ResponseEntity.ok(ApiResponse.ok(created, correlationId));
    }

    @PostMapping("/{userId}/activation")
    public ResponseEntity<ApiResponse<AdminCreatedUserDto>> issueActivation(@PathVariable Long userId,Authentication authentication) {
        String adminActor=authentication!=null?authentication.getName():"ADMIN";
        return ResponseEntity.ok(ApiResponse.ok(userScopeService.issueActivation(userId,adminActor),getCorrelationId()));
    }

    @GetMapping("/{userId}/scopes")
    public ResponseEntity<ApiResponse<List<UserScopeDto>>> getUserScopes(@PathVariable Long userId) {
        String correlationId = getCorrelationId();
        List<UserScopeDto> scopes = userScopeService.getUserScopes(userId);
        return ResponseEntity.ok(ApiResponse.ok(scopes, correlationId));
    }

    @PostMapping("/{userId}/scopes")
    public ResponseEntity<ApiResponse<UserScopeDto>> assignScope(@PathVariable Long userId,
                                                                 @RequestBody AssignScopeRequest request,
                                                                 Authentication authentication) {
        String correlationId = getCorrelationId();
        String adminActor = authentication != null ? authentication.getName() : "ADMIN";
        UserScopeDto scope = userScopeService.assignScope(userId, request, adminActor);
        return ResponseEntity.ok(ApiResponse.ok(scope, correlationId));
    }

    @DeleteMapping("/{userId}/scopes/{scopeId}")
    public ResponseEntity<ApiResponse<String>> removeScope(@PathVariable Long userId,
                                                           @PathVariable Long scopeId,
                                                           Authentication authentication) {
        String correlationId = getCorrelationId();
        String adminActor = authentication != null ? authentication.getName() : "ADMIN";
        userScopeService.removeScope(scopeId, adminActor);
        return ResponseEntity.ok(ApiResponse.ok("Scope removed successfully", correlationId));
    }

    private String getCorrelationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "unknown";
    }
}
