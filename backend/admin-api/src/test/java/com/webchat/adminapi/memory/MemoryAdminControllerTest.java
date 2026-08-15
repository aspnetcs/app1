package com.webchat.adminapi.memory;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MemoryAdminControllerTest {

    @Mock
    private MemoryAdminService memoryAdminService;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MemoryAdminController(memoryAdminService)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void configRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/memory/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void configReturnsPayload() throws Exception {
        when(memoryAdminService.getConfig()).thenReturn(Map.of(
                "enabled", true,
                "requireConsent", true,
                "maxEntriesPerUser", 50,
                "maxCharsPerEntry", 1000,
                "retentionDays", 30,
                "summaryModel", "gpt"
        ));

        mockMvc.perform(admin(get("/api/v1/admin/memory/config")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.maxEntriesPerUser").value(50));
    }

    @Test
    void updateConfigReturnsSavedPayload() throws Exception {
        when(memoryAdminService.updateConfig(eq(Map.of(
                "enabled", true,
                "requireConsent", false,
                "maxEntriesPerUser", 80,
                "maxCharsPerEntry", 1200,
                "retentionDays", 45,
                "summaryModel", "memory-model"
        )))).thenReturn(Map.of(
                "enabled", true,
                "requireConsent", false,
                "maxEntriesPerUser", 80,
                "maxCharsPerEntry", 1200,
                "retentionDays", 45,
                "summaryModel", "memory-model"
        ));

        mockMvc.perform(admin(put("/api/v1/admin/memory/config")
                        .contentType("application/json")
                        .content("""
                                {"enabled":true,"requireConsent":false,"maxEntriesPerUser":80,"maxCharsPerEntry":1200,"retentionDays":45,"summaryModel":"memory-model"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.retentionDays").value(45));
    }

    @Test
    void statsAndAuditsReturnPayloads() throws Exception {
        when(memoryAdminService.getStats()).thenReturn(Map.of(
                "enabled", true,
                "totalUsers", 2,
                "totalEntries", 5,
                "pendingReviews", 1,
                "averageEntriesPerUser", 2.5
        ));
        when(memoryAdminService.getAudits(eq(0), eq(20))).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "userId", UUID.randomUUID().toString(),
                        "action", "consent_update",
                        "summary", "updated",
                        "status", "success",
                        "createdAt", "2026-04-18T00:00:00Z"
                )),
                "total", 1,
                "page", 0,
                "size", 20
        ));

        mockMvc.perform(admin(get("/api/v1/admin/memory/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEntries").value(5));

        mockMvc.perform(admin(get("/api/v1/admin/memory/audits")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].action").value("consent_update"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
