package com.company.vapt.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import jakarta.servlet.http.Cookie;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void loginWithValidCredentialsReturnsUserAndSetsSession() throws Exception {
        String requestJson = "{\"email\":\"app.owner@transunion.com\",\"password\":\"DevOwner@2026!\"}";

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("app.owner@transunion.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.scopes").isArray())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);

        // Access /api/auth/me using established session
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("app.owner@transunion.com"));
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        String requestJson = "{\"email\":\"app.owner@transunion.com\",\"password\":\"WrongPassword!\"}";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @WithMockUser(username = "app.owner@transunion.com", roles = {"USER"})
    void normalUserCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "manohar.kaloni@transunion.com", roles = {"ADMIN"})
    void adminUserCanAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void realAdminLoginSessionCanReadUploadAndImportMetadataEndpoints() throws Exception {
        MvcResult login=mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manohar.kaloni@transunion.com\",\"password\":\"test-admin-secret-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andReturn();
        MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
        mockMvc.perform(get("/api/admin/cmdb/datasets").session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/sections/WIZ/imports").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void csrfBootstrapAllowsAuthenticatedAdminUploadRequestToReachValidation() throws Exception {
        MvcResult login=mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manohar.kaloni@transunion.com\",\"password\":\"test-admin-secret-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
        MvcResult csrf=mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isString()).andReturn();
        Cookie csrfCookie=csrf.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(csrfCookie,"CSRF endpoint must materialize the cookie used by the SPA");
        String token=csrfCookie.getValue();
        MockMultipartFile emptyFile=new MockMultipartFile("file","empty.csv","text/csv",new byte[0]);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/reports/sections/WIZ/imports").file(emptyFile).session(session).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN",token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void adminProvisionedAccountRequiresOneTimeActivationAndStartsWithoutScopes() throws Exception {
        MvcResult login=mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"manohar.kaloni@transunion.com\",\"password\":\"test-admin-secret-pass\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
        MvcResult created=mockMvc.perform(post("/api/admin/users").session(session).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\" New.User@TransUnion.com \",\"fullName\":\"New User\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.user.status").value("PENDING"))
                .andExpect(jsonPath("$.data.user.scopes").isEmpty())
                .andReturn();
        String activationPath=com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(),"$.data.activationPath");
        Integer userId=com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(),"$.data.user.id");
        String token=activationPath.substring(activationPath.indexOf("token=")+6);
        String activationJson="{\"token\":\""+token+"\",\"password\":\"NewSecure!Pass123\"}";
        var csrf=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
        mockMvc.perform(post("/api/auth/activate").with(csrf).contentType(MediaType.APPLICATION_JSON).content(activationJson))
                .andExpect(status().isOk());
        String storedHash=jdbc.queryForObject("select token_hash from user_activation_token where user_id=?",String.class,userId.longValue());
        String submittedHash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.junit.jupiter.api.Assertions.assertEquals(submittedHash,storedHash);
        mockMvc.perform(post("/api/auth/activate").with(csrf).contentType(MediaType.APPLICATION_JSON).content(activationJson))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new.user@transunion.com\",\"password\":\"NewSecure!Pass123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username="manohar.kaloni@transunion.com",roles="ADMIN")
    void viteProxyDoesNotRequireCrossOriginCors() throws Exception {
        mockMvc.perform(options("/api/admin/cmdb/datasets")
                        .header("Origin","http://localhost:3000")
                        .header("Access-Control-Request-Method","GET"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
