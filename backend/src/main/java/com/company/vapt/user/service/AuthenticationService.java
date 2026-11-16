package com.company.vapt.user.service;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.exception.VaptException;
import com.company.vapt.user.dto.response.AuthUserDto;
import com.company.vapt.user.dto.request.LoginRequest;
import com.company.vapt.user.dto.response.UserScopeDto;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.domain.UserScope;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.user.repository.UserScopeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final AppUserRepository userRepository;
    private final UserScopeRepository userScopeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${vapt.security.max-failed-attempts:5}")
    private int maxFailedAttempts = 5;

    @Value("${vapt.security.lockout-minutes:15}")
    private long lockoutDurationMinutes = 15;

    public AuthenticationService(AppUserRepository userRepository,
                                 UserScopeRepository userScopeRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.userScopeRepository = userScopeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public AuthUserDto login(LoginRequest request, HttpServletRequest httpRequest) {
        Instant now = Instant.now();
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElse(null);

        if (user == null) {
            auditService.record(normalizedEmail, "USER", "LOGIN_FAILURE", "AUTH", normalizedEmail, "User not found");
            log.warn("Login failed: user not found for email: {}", normalizedEmail);
            throw new VaptException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            auditService.record(String.valueOf(user.getId()), user.getRole(), "LOGIN_FAILURE", "AUTH", normalizedEmail, "Account is not active");
            throw new VaptException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        if (user.isAccountLocked(now)) {
            auditService.record(String.valueOf(user.getId()), user.getRole(), "LOGIN_LOCKED", "AUTH", normalizedEmail, "Account temporarily locked");
            log.warn("Login blocked: account is locked for email: {}", normalizedEmail);
            throw new VaptException("ACCOUNT_LOCKED", "Account is temporarily locked due to multiple failed login attempts. Please try again later.", HttpStatus.LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.recordFailedAttempt(maxFailedAttempts, lockoutDurationMinutes, now);
            userRepository.save(user);

            boolean lockedNow = user.isAccountLocked(now);
            String failureDetail = lockedNow ? "Account locked after " + user.getFailedAttemptCount() + " failed attempts"
                                             : "Password mismatch, attempt: " + user.getFailedAttemptCount();

            auditService.record(String.valueOf(user.getId()), user.getRole(), "LOGIN_FAILURE", "AUTH", normalizedEmail, failureDetail);
            log.warn("Login failed: password mismatch for email: {}. Attempt {}/{}", normalizedEmail, user.getFailedAttemptCount(), maxFailedAttempts);

            if (lockedNow) {
                throw new VaptException("ACCOUNT_LOCKED", "Account is temporarily locked due to multiple failed login attempts. Please try again later.", HttpStatus.LOCKED);
            }
            throw new VaptException("INVALID_CREDENTIALS", "Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        // Login Success
        user.resetFailedAttempts(now);
        userRepository.save(user);

        // Setup Spring Security Context in Session
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, Collections.singletonList(authority));

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authToken);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        auditService.record(String.valueOf(user.getId()), user.getRole(), "LOGIN_SUCCESS", "AUTH", normalizedEmail, "Login successful");
        log.info("Login successful for user: {}, role: {}", normalizedEmail, user.getRole());

        return mapToDto(user);
    }

    @Transactional(readOnly = true)
    public AuthUserDto getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new VaptException("UNAUTHORIZED", "Not authenticated", HttpStatus.UNAUTHORIZED);
        }

        String email = authentication.getName();
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new VaptException("USER_NOT_FOUND", "Authenticated user not found", HttpStatus.NOT_FOUND));

        return mapToDto(user);
    }

    public void logout(HttpServletRequest httpRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorId = auth != null ? auth.getName() : "ANONYMOUS";
        String role = auth != null && !auth.getAuthorities().isEmpty() ? auth.getAuthorities().iterator().next().getAuthority() : "USER";

        SecurityContextHolder.clearContext();
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        auditService.record(actorId, role, "LOGOUT", "AUTH", actorId, "User logged out");
        log.info("User logged out: {}", actorId);
    }

    @Transactional
    public void changePassword(String currentPassword,String newPassword,Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated())
            throw new VaptException("UNAUTHORIZED","Not authenticated",HttpStatus.UNAUTHORIZED);
        PasswordPolicy.validate(newPassword);
        AppUser user=userRepository.findByEmail(authentication.getName().trim().toLowerCase(java.util.Locale.ROOT))
                .orElseThrow(()->new VaptException("USER_NOT_FOUND","Authenticated user not found",HttpStatus.NOT_FOUND));
        if(!passwordEncoder.matches(currentPassword,user.getPasswordHash()))
            throw new VaptException("INVALID_CREDENTIALS","Current password is incorrect",HttpStatus.UNAUTHORIZED);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        user.resetFailedAttempts(Instant.now());
        userRepository.save(user);
        auditService.record(String.valueOf(user.getId()),user.getRole(),"PASSWORD_CHANGED","AUTH",String.valueOf(user.getId()),"Password changed by account holder");
        log.info("Password changed for account id={}",user.getId());
    }

    private AuthUserDto mapToDto(AppUser user) {
        List<UserScope> scopes = userScopeRepository.findByUserId(user.getId());
        List<UserScopeDto> scopeDtos = scopes.stream()
                .map(s -> new UserScopeDto(s.getId(), s.getIpAddress(), s.getApplicationName()))
                .collect(Collectors.toList());

        return new AuthUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus(),
                scopeDtos
        );
    }
}
