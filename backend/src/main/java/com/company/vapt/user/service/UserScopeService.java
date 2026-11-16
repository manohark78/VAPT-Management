package com.company.vapt.user.service;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.exception.ResourceNotFoundException;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.user.dto.request.AssignScopeRequest;
import com.company.vapt.user.dto.response.AuthUserDto;
import com.company.vapt.user.dto.request.CreateUserRequest;
import com.company.vapt.user.dto.response.AdminCreatedUserDto;
import com.company.vapt.user.dto.response.UserScopeDto;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.domain.UserScope;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.user.repository.UserScopeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserScopeService {

    private static final Logger log = LoggerFactory.getLogger(UserScopeService.class);

    private final AppUserRepository userRepository;
    private final UserScopeRepository userScopeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final AccountActivationService activationService;
    @Value("${vapt.security.allowed-email-domain:transunion.com}") private String allowedEmailDomain;

    public UserScopeService(AppUserRepository userRepository,
                            UserScopeRepository userScopeRepository,
                            PasswordEncoder passwordEncoder,
                            AuditService auditService,
                            AccountActivationService activationService) {
        this.userRepository = userRepository;
        this.userScopeRepository = userScopeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.activationService = activationService;
    }

    @Transactional(readOnly = true)
    public List<AuthUserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapUserToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public AdminCreatedUserDto createUser(CreateUserRequest request, String adminActorId) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if(!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ValidationException("Enter a valid company email address");
        String domain=allowedEmailDomain==null?"":allowedEmailDomain.trim().toLowerCase(java.util.Locale.ROOT);
        if (!domain.isBlank() && !normalizedEmail.endsWith("@"+domain))
            throw new ValidationException("Use a company email address ending in @"+domain);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ValidationException("User already exists with email: " + normalizedEmail);
        }

        byte[] randomPassword=new byte[48]; new java.security.SecureRandom().nextBytes(randomPassword);
        String passwordHash = passwordEncoder.encode(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomPassword));
        AppUser user = new AppUser(
                normalizedEmail,
                passwordHash,
                request.getFullName().trim(),
                request.getRole()==null?"USER":request.getRole(),
                "PENDING"
        );

        AppUser saved = userRepository.save(user);
        auditService.record(adminActorId, "ADMIN", "USER_CREATE", "USER", String.valueOf(saved.getId()),
                "Created user: " + normalizedEmail + " with role: " + saved.getRole());
        log.info("Admin {} created pending user account id={}", adminActorId, saved.getId());
        AccountActivationService.IssuedToken token=activationService.issue(saved,adminActorId);
        return new AdminCreatedUserDto(mapUserToDto(saved),token.activationPath(),token.expiresAt());
    }

    @Transactional
    public AdminCreatedUserDto issueActivation(Long userId,String adminActorId) {
        AppUser user=userRepository.findById(userId).orElseThrow(()->new ResourceNotFoundException("AppUser",String.valueOf(userId)));
        AccountActivationService.IssuedToken token=activationService.issue(user,adminActorId);
        return new AdminCreatedUserDto(mapUserToDto(user),token.activationPath(),token.expiresAt());
    }

    @Transactional
    public UserScopeDto assignScope(Long userId, AssignScopeRequest request, String adminActorId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", String.valueOf(userId)));

        String ip = request.getIpAddress() != null && !request.getIpAddress().isBlank()
                ? request.getIpAddress().trim() : null;
        String app = request.getApplicationName() != null && !request.getApplicationName().isBlank()
                ? request.getApplicationName().trim() : null;

        if (ip == null && app == null) {
            throw new ValidationException("At least an IP address or an application name must be provided to define a scope");
        }

        UserScope scope = new UserScope(user, ip, app);
        UserScope saved = userScopeRepository.save(scope);

        auditService.record(adminActorId, "ADMIN", "USER_SCOPE_ASSIGN", "USER_SCOPE", String.valueOf(saved.getId()),
                "Assigned scope [IP=" + ip + ", App=" + app + "] to user: " + user.getEmail());
        log.info("Assigned scope [IP={}, App={}] to user: {}", ip, app, user.getEmail());

        return new UserScopeDto(saved.getId(), saved.getIpAddress(), saved.getApplicationName());
    }

    @Transactional(readOnly = true)
    public List<UserScopeDto> getUserScopes(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("AppUser", String.valueOf(userId));
        }

        return userScopeRepository.findByUserId(userId).stream()
                .map(s -> new UserScopeDto(s.getId(), s.getIpAddress(), s.getApplicationName()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeScope(Long scopeId, String adminActorId) {
        UserScope scope = userScopeRepository.findById(scopeId)
                .orElseThrow(() -> new ResourceNotFoundException("UserScope", String.valueOf(scopeId)));

        Long userId = scope.getUser().getId();
        userScopeRepository.delete(scope);

        auditService.record(adminActorId, "ADMIN", "USER_SCOPE_DELETE", "USER_SCOPE", String.valueOf(scopeId),
                "Removed scope ID " + scopeId + " from user ID " + userId);
        log.info("Admin {} removed scope ID {} from user ID {}", adminActorId, scopeId, userId);
    }

    @Transactional(readOnly = true)
    public boolean hasAccess(Long userId, String ipAddress, String applicationName) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", String.valueOf(userId)));

        // ADMIN has universal access
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            return true;
        }

        List<UserScope> scopes = userScopeRepository.findByUserId(userId);
        if (scopes.isEmpty()) {
            return false;
        }

        // Check if caller's scopes match either IP or Application
        for (UserScope scope : scopes) {
            boolean ipMatch = scope.getIpAddress() != null && scope.getIpAddress().equalsIgnoreCase(ipAddress);
            boolean appMatch = scope.getApplicationName() != null && scope.getApplicationName().equalsIgnoreCase(applicationName);

            if (ipMatch || appMatch) {
                return true;
            }
        }

        return false;
    }

    public void enforceAccess(Long userId, String ipAddress, String applicationName) {
        if (!hasAccess(userId, ipAddress, applicationName)) {
            log.warn("Access denied: User {} lacks scope for IP: {}, App: {}", userId, ipAddress, applicationName);
            throw new AccessDeniedException("Access denied: You do not have permissions for the requested asset or application");
        }
    }

    private AuthUserDto mapUserToDto(AppUser user) {
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
