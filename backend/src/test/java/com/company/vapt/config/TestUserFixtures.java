package com.company.vapt.config;

import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.domain.UserScope;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.user.repository.UserScopeRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Seed accounts and scopes used by integration tests only; no normal users are provisioned at runtime. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestUserFixtures {
    @Bean
    ApplicationRunner testUserFixture(AppUserRepository users, UserScopeRepository scopes, PasswordEncoder encoder) {
        return args -> {
            users.findByEmail("app.owner@transunion.com").orElseGet(() -> {
                AppUser user = users.save(new AppUser("app.owner@transunion.com", encoder.encode("DevOwner@2026!"),
                        "Application Owner 1", "USER", "ACTIVE"));
                scopes.save(new UserScope(user, "10.20.30.40", "TransUnion Portal"));
                scopes.save(new UserScope(user, "10.20.30.41", "CIBIL Bureau Gateway"));
                return user;
            });
        };
    }
}
