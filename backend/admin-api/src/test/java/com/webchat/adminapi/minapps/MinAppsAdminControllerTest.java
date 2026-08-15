package com.webchat.adminapi.minapps;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class MinAppsAdminControllerTest {

    private MockEnvironment environment;
    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        mockMvc = MockMvcBuilders.standaloneSetup(new MinAppsAdminController(environment)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/minapps/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsDisabledByDefault() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/minapps/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.flagName").value("platform.minapps.enabled"));
    }

    @Test
    void configReflectsEnabledFlag() throws Exception {
        environment.setProperty("platform.minapps.enabled", "true");

        mockMvc.perform(admin(get("/api/v1/admin/minapps/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}

