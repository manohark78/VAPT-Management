package com.company.vapt.user;

import com.company.vapt.user.dto.request.AssignScopeRequest;
import com.company.vapt.user.service.UserScopeService;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserScopeAuthorizationTest {

    @Autowired
    private UserScopeService userScopeService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser adminUser;
    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByEmail("manohar.kaloni@transunion.com").orElseThrow();

        userA = userRepository.findByEmail("user.a@transunion.com")
                .orElseGet(() -> userRepository.save(new AppUser("user.a@transunion.com", passwordEncoder.encode("Pass1!"), "User A", "USER", "ACTIVE")));

        userB = userRepository.findByEmail("user.b@transunion.com")
                .orElseGet(() -> userRepository.save(new AppUser("user.b@transunion.com", passwordEncoder.encode("Pass2!"), "User B", "USER", "ACTIVE")));

        userScopeService.assignScope(userA.getId(), new AssignScopeRequest("10.10.10.1", "Alpha Service"), "ADMIN");
        userScopeService.assignScope(userB.getId(), new AssignScopeRequest("10.20.20.2", "Beta Service"), "ADMIN");
    }

    @Test
    void adminHasUniversalAccess() {
        assertThat(userScopeService.hasAccess(adminUser.getId(), "10.10.10.1", "Alpha Service")).isTrue();
        assertThat(userScopeService.hasAccess(adminUser.getId(), "10.20.20.2", "Beta Service")).isTrue();
        assertThat(userScopeService.hasAccess(adminUser.getId(), "99.99.99.99", "Any Random App")).isTrue();
    }

    @Test
    void normalUserHasAccessToAssignedScopeOnly() {
        // User A has access to 10.10.10.1 and Alpha Service
        assertThat(userScopeService.hasAccess(userA.getId(), "10.10.10.1", "Alpha Service")).isTrue();
        assertThat(userScopeService.hasAccess(userA.getId(), "10.10.10.1", "Other App")).isTrue(); // IP match
        assertThat(userScopeService.hasAccess(userA.getId(), "99.99.99.99", "Alpha Service")).isTrue(); // App match

        // User A does NOT have access to User B's assets
        assertThat(userScopeService.hasAccess(userA.getId(), "10.20.20.2", "Beta Service")).isFalse();
        assertThat(userScopeService.hasAccess(userA.getId(), "192.168.1.100", "Finance System")).isFalse();
    }

    @Test
    void enforceAccessThrowsAccessDeniedWhenUnauthorized() {
        // User B attempting to access User A's asset
        assertThatThrownBy(() -> userScopeService.enforceAccess(userB.getId(), "10.10.10.1", "Alpha Service"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied");
    }
}
