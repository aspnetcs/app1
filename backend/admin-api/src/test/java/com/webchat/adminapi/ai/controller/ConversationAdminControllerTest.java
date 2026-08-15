package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationAdminControllerTest {

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ConversationAdminController(true, true, true)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void conversationConfigRoutesReturnStructuredPayloads() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/conversations/pin-star-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.fields[0]").value("pinned"))
                .andExpect(jsonPath("$.data.managementEnabled").value(true))
                .andExpect(jsonPath("$.data.restoreEnabled").value(true));

        mockMvc.perform(admin(get("/api/v1/admin/conversations/message-version-config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.supportsRestoreFlow").value(true));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
