package com.company.vapt.user.service;

import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BootstrapAdminRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    public static final String BOOTSTRAP_ADMIN_EMAIL = "manohar.kaloni@transunion.com";
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${vapt.security.admin-initial-password:${VAPT_ADMIN_INITIAL_PASSWORD:}}")
    private String configuredAdminPassword;

    public BootstrapAdminRunner(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        provisionBootstrapAdmin();
    }

    private void provisionBootstrapAdmin() {
        if (userRepository.existsByEmail(BOOTSTRAP_ADMIN_EMAIL)) {
            log.info("Bootstrap admin already exists; initial password is not reapplied.");
            return;
        }

        String rawPassword = configuredAdminPassword;
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalStateException("Set VAPT_ADMIN_INITIAL_PASSWORD before first startup to provision the bootstrap administrator.");
        }

        String encodedHash = passwordEncoder.encode(rawPassword);
        AppUser admin = new AppUser(
                BOOTSTRAP_ADMIN_EMAIL,
                encodedHash,
                "Manohar Kaloni",
                "ADMIN",
                "ACTIVE"
        );

        userRepository.save(admin);
        log.info("Bootstrap admin initialized successfully: {}. Role: ADMIN", BOOTSTRAP_ADMIN_EMAIL);
    }

}
