package com.company.vapt.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:h2_console_dev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "vapt.storage.root-dir=./target/test-storage",
        "vapt.security.admin-initial-password=ConsoleTestAdmin!42"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class H2ConsoleDevSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;

    @Test
    void consoleIsEnabledAtExpectedPathAndUsesApplicationDatasource() {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("spring.h2.console.path")).isEqualTo("/h2-console");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:h2:mem:h2_console_dev;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
    }

    @Test
    @WithMockUser(username="admin", roles="ADMIN")
    void adminPassesConsoleSecurityAndReceivesSameOriginFramePolicy() throws Exception {
        mvc.perform(get("/h2-console/").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
    }

    @Test
    @WithMockUser(username="user", roles="USER")
    void normalUserIsDeniedFromConsole() throws Exception {
        mvc.perform(get("/h2-console/").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username="admin", roles="ADMIN")
    void administratorCannotOpenConsoleFromANonLocalAddress() throws Exception {
        mvc.perform(get("/h2-console/").with(request -> {
                    request.setRemoteAddr("192.0.2.25");
                    return request;
                }))
                .andExpect(status().isForbidden());
    }
}
