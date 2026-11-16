package com.company.vapt.user.controller;

import com.company.vapt.common.response.ApiResponse;
import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.user.dto.response.AuthUserDto;
import com.company.vapt.user.dto.request.LoginRequest;
import com.company.vapt.user.dto.request.ActivateAccountRequest;
import com.company.vapt.user.dto.request.ChangePasswordRequest;
import com.company.vapt.user.service.AccountActivationService;
import com.company.vapt.user.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AccountActivationService activationService;

    public AuthController(AuthenticationService authenticationService, AccountActivationService activationService) {
        this.authenticationService = authenticationService;
        this.activationService = activationService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthUserDto>> login(@Valid @RequestBody LoginRequest request,
                                                          HttpServletRequest httpRequest) {
        String correlationId = getCorrelationId();
        AuthUserDto user = authenticationService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(user, correlationId));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthUserDto>> getCurrentUser(Authentication authentication) {
        String correlationId = getCorrelationId();
        AuthUserDto user = authenticationService.getCurrentUser(authentication);
        return ResponseEntity.ok(ApiResponse.ok(user, correlationId));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest httpRequest) {
        String correlationId = getCorrelationId();
        authenticationService.logout(httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", correlationId));
    }

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<String>> getCsrfToken(CsrfToken csrfToken) {
        String correlationId = getCorrelationId();
        String token = csrfToken != null ? csrfToken.getToken() : "";
        return ResponseEntity.ok(ApiResponse.ok(token, correlationId));
    }

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse<String>> activate(@Valid @RequestBody ActivateAccountRequest request) {
        activationService.activate(request.token(),request.password());
        return ResponseEntity.ok(ApiResponse.ok("Account activated. You may now sign in.",getCorrelationId()));
    }

    @PostMapping("/password")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        authenticationService.changePassword(request.currentPassword(),request.newPassword(),authentication);
        return ResponseEntity.ok(ApiResponse.ok("Password changed.",getCorrelationId()));
    }

    private String getCorrelationId() {
        String id = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        return id != null ? id : "unknown";
    }
}
