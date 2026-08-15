package com.webchat.adminapi;

import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUsageControllerTest {

    @Mock
    private AiUsageService usageService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUsageController(usageService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void adminUsageRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(usageService);
    }

    @Test
    void adminUsageReturnsServiceSummary() throws Exception {
        when(usageService.getAdminSummary(any(), any())).thenReturn(List.of(
                Map.of("channelType", "openai", "model", "gpt-4o", "totalTokens", 3200)
        ));

        mockMvc.perform(admin(get("/api/v1/admin/ai/usage").param("days", "7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].channelType").value("openai"))
                .andExpect(jsonPath("$.data[0].model").value("gpt-4o"))
                .andExpect(jsonPath("$.data[0].totalTokens").value(3200));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
