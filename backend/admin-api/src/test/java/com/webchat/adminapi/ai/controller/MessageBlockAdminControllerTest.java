package com.webchat.adminapi.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MessageBlockAdminControllerTest {

    private JdbcTemplate jdbcTemplate;
    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MessageBlockAdminController(jdbcTemplate, new ObjectMapper())
        ).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void blocksRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/messages/{messageId}/blocks", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));
    }

    @Test
    void blocksReturnStoredRows() throws Exception {
        UUID messageId = UUID.randomUUID();
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("id", UUID.randomUUID().toString()),
                        Map.entry("message_id", messageId.toString()),
                        Map.entry("conversation_id", UUID.randomUUID().toString()),
                        Map.entry("role", "assistant"),
                        Map.entry("type", "citation"),
                        Map.entry("key", "citation-0"),
                        Map.entry("sequence", 1),
                        Map.entry("status", "final"),
                        Map.entry("payload", Map.of("title", "Doc A")),
                        Map.entry("created_at", "2026-04-18T00:00:00Z"),
                        Map.entry("updated_at", "2026-04-18T00:00:01Z")
                )));

        mockMvc.perform(admin(get("/api/v1/admin/messages/{messageId}/blocks", messageId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].type").value("citation"))
                .andExpect(jsonPath("$.data[0].key").value("citation-0"))
                .andExpect(jsonPath("$.data[0].payload.title").value("Doc A"));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
