package com.webchat.adminapi.webread;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.webread.WebReadService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebReadAdminControllerTest {

    @Mock
    private WebReadService webReadService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WebReadAdminController(webReadService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/web-read/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsCurrentServiceValues() throws Exception {
        when(webReadService.isEnabled()).thenReturn(true);
        when(webReadService.getMaxContentChars()).thenReturn(8000);
        when(webReadService.getConnectTimeoutMs()).thenReturn(5000);
        when(webReadService.isAllowHttp()).thenReturn(false);
        when(webReadService.isEnforceHostAllowlist()).thenReturn(true);
        when(webReadService.getAllowedHostsRaw()).thenReturn("example.com,openai.com");

        mockMvc.perform(admin(get("/api/v1/admin/web-read/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.maxContentChars").value(8000))
                .andExpect(jsonPath("$.data.connectTimeoutMs").value(5000))
                .andExpect(jsonPath("$.data.allowHttp").value(false))
                .andExpect(jsonPath("$.data.enforceHostAllowlist").value(true))
                .andExpect(jsonPath("$.data.allowedHosts").value("example.com,openai.com"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
