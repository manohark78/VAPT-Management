package com.company.vapt.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:h2_console_prod;DB_CLOSE_DELAY=-1;MODE=PostgreSQL","vapt.storage.root-dir=./target/test-storage","vapt.security.admin-initial-password=ConsoleTestAdmin!42"})
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class H2ConsoleProductionDisabledTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @Test @WithMockUser(username="admin",roles="ADMIN")
    void consoleIsDisabledOutsideDevelopment() throws Exception {
        assertEquals("false",environment.getProperty("spring.h2.console.enabled","false"));
        mvc.perform(get("/h2-console/")).andExpect(status().isForbidden());
    }
}
