package com.company.vapt;

import com.company.vapt.common.logging.CorrelationIdFilter;
import com.company.vapt.common.observability.PerformanceTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VaptApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PerformanceTimer performanceTimer;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void contextLoads() {
        assertThat(userRepository).isNotNull();
        assertThat(passwordEncoder).isNotNull();
        assertThat(performanceTimer).isNotNull();
    }

    @Test
    void bootstrapAdminIsCreatedWithBcryptHash() {
        Optional<AppUser> adminOpt = userRepository.findByEmail("manohar.kaloni@transunion.com");
        assertThat(adminOpt).isPresent();

        AppUser admin = adminOpt.get();
        assertThat(admin.getRole()).isEqualTo("ADMIN");
        assertThat(admin.getStatus()).isEqualTo("ACTIVE");
        assertThat(admin.getPasswordHash()).startsWith("$2a$");
        assertThat(passwordEncoder.matches("test-admin-secret-pass", admin.getPasswordHash())).isTrue();
    }

    @Test
    void healthEndpointResponds() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void correlationIdHeaderIsGeneratedWhenMissing() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.CORRELATION_ID_HEADER));
    }

    @Test
    void correlationIdHeaderIsPreservedWhenSent() throws Exception {
        String customCorrelationId = "custom-test-correlation-id-12345";
        mockMvc.perform(get("/actuator/health")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, customCorrelationId));
    }

    @Test
    void performanceTimerRecordsExecution() throws Exception {
        String result = performanceTimer.record("test.operation", "PHASE1_TEST", "SUCCESS", () -> "COMPLETED");
        assertThat(result).isEqualTo("COMPLETED");

        performanceTimer.recordDuration("test.duration", "PHASE1_STAGE", "SUCCESS", Duration.ofMillis(42));
    }

    @Test
    void apiRequestMetricsRecordDurationAndFiniteOutcomesWithoutRouteLabels() throws Exception {
        long successBefore = metricCount("SUCCESS");
        mockMvc.perform(get("/api/reports/sections")
                        .with(SecurityMockMvcRequestPostProcessors.user("metrics-admin").roles("ADMIN")))
                .andExpect(status().isOk());
        assertThat(metricCount("SUCCESS")).isEqualTo(successBefore + 1);

        long clientErrorBefore = metricCount("CLIENT_ERROR");
        mockMvc.perform(get("/api/reports/sections/UNKNOWN")
                        .with(SecurityMockMvcRequestPostProcessors.user("metrics-admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
        assertThat(metricCount("CLIENT_ERROR")).isEqualTo(clientErrorBefore + 1);

        Timer timer = meterRegistry.find("api.request").tag("stage", "HTTP").tag("result", "SUCCESS").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTags()).extracting("key").containsExactlyInAnyOrder("stage", "result");
    }

    private long metricCount(String result) {
        Timer timer = meterRegistry.find("api.request").tag("stage", "HTTP").tag("result", result).timer();
        return timer != null ? timer.count() : 0;
    }
}
