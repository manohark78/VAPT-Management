package com.company.vapt.user;

import com.company.vapt.audit.domain.AuditEvent;
import com.company.vapt.audit.repository.AuditEventRepository;
import com.company.vapt.common.exception.VaptException;
import com.company.vapt.user.dto.response.AuthUserDto;
import com.company.vapt.user.dto.request.LoginRequest;
import com.company.vapt.user.service.AuthenticationService;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationServiceTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail("test.user@transunion.com")) {
            AppUser user = new AppUser(
                    "test.user@transunion.com",
                    passwordEncoder.encode("SecretPass123!"),
                    "Test User",
                    "USER",
                    "ACTIVE"
            );
            userRepository.save(user);
        }
    }

    @Test
    void successfulLoginAuthenticatesAndClearsAttempts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginRequest loginRequest = new LoginRequest("test.user@transunion.com", "SecretPass123!");

        AuthUserDto dto = authenticationService.login(loginRequest, request);

        assertThat(dto).isNotNull();
        assertThat(dto.getEmail()).isEqualTo("test.user@transunion.com");
        assertThat(dto.getRole()).isEqualTo("USER");

        AppUser user = userRepository.findByEmail("test.user@transunion.com").orElseThrow();
        assertThat(user.getFailedAttemptCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();

        List<AuditEvent> audits = auditEventRepository.findByActionOrderByTimestampDesc("LOGIN_SUCCESS");
        assertThat(audits).isNotEmpty();
    }

    @Test
    void wrongPasswordIncrementsFailedAttempts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginRequest loginRequest = new LoginRequest("test.user@transunion.com", "WrongPassword!");

        assertThatThrownBy(() -> authenticationService.login(loginRequest, request))
                .isInstanceOf(VaptException.class)
                .hasMessageContaining("Invalid email or password");

        AppUser user = userRepository.findByEmail("test.user@transunion.com").orElseThrow();
        assertThat(user.getFailedAttemptCount()).isEqualTo(1);
    }

    @Test
    void accountLocksAfterMaxFailedAttempts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginRequest loginRequest = new LoginRequest("test.user@transunion.com", "WrongPassword!");

        for (int i = 0; i < 5; i++) {
            try {
                authenticationService.login(loginRequest, request);
            } catch (VaptException ignored) {
            }
        }

        AppUser user = userRepository.findByEmail("test.user@transunion.com").orElseThrow();
        assertThat(user.getFailedAttemptCount()).isGreaterThanOrEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();

        // Even with correct password, locked account must be rejected
        LoginRequest correctLogin = new LoginRequest("test.user@transunion.com", "SecretPass123!");
        assertThatThrownBy(() -> authenticationService.login(correctLogin, request))
                .isInstanceOf(VaptException.class)
                .hasMessageContaining("temporarily locked");
    }
}
