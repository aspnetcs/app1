package com.webchat.adminapi.promptopt;

import com.webchat.platformapi.ai.texttransform.PromptOptimizeService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
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
class PromptOptimizeAdminControllerTest {

    @Mock
    private PromptOptimizeService promptOptimizeService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PromptOptimizeAdminController(promptOptimizeService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/prompt-optimize/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsCurrentServiceValues() throws Exception {
        when(promptOptimizeService.isEnabled()).thenReturn(true);
        when(promptOptimizeService.getDefaultModel()).thenReturn("gpt-4o");
        when(promptOptimizeService.getDefaultDirection()).thenReturn("clarify");
        when(promptOptimizeService.getMaxInputChars()).thenReturn(4000);

        mockMvc.perform(admin(get("/api/v1/admin/prompt-optimize/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.defaultModel").value("gpt-4o"))
                .andExpect(jsonPath("$.data.defaultDirection").value("clarify"))
                .andExpect(jsonPath("$.data.maxInputChars").value(4000));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
