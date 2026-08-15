package com.webchat.adminapi.audit;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditAdminControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuditAdminController(jdbcTemplate)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void logsUsesAuditRouteAndConsumerQueryShape() throws Exception {
        UUID filteredUserId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("action", "user.login", "user_id", filteredUserId.toString())));

        mockMvc.perform(
                        get("/api/v1/admin/audit/logs")
                                .param("page", "0")
                                .param("size", "20")
                                .param("action", "user.login")
                                .param("userIdFilter", filteredUserId.toString())
                                .param("hours", "24")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].action").value("user.login"))
                .andExpect(jsonPath("$.data.items[0].user_id").value(filteredUserId.toString()));

        ArgumentCaptor<String> countSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> countArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(countSqlCaptor.capture(), eq(Long.class), countArgsCaptor.capture());
        assertTrue(countSqlCaptor.getValue().contains("SELECT COUNT(*)"));
        assertTrue(countSqlCaptor.getValue().contains("AND action = ?"));
        assertTrue(countSqlCaptor.getValue().contains("AND user_id = ?"));
        assertEquals(3, countArgsCaptor.getValue().length);

        ArgumentCaptor<String> listSqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> listArgsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(listSqlCaptor.capture(), listArgsCaptor.capture());
        assertTrue(listSqlCaptor.getValue().contains("ORDER BY created_at DESC LIMIT ? OFFSET ?"));
        assertEquals(5, listArgsCaptor.getValue().length);
        assertEquals(20, listArgsCaptor.getValue()[3]);
        assertEquals(0, listArgsCaptor.getValue()[4]);
    }

    @Test
    void statsUsesDedicatedAuditStatsRoute() throws Exception {
        when(jdbcTemplate.queryForList(anyString(), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("action", "user.login", "count", 3)));

        mockMvc.perform(
                        get("/api/v1/admin/audit/stats")
                                .param("hours", "168")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].action").value("user.login"))
                .andExpect(jsonPath("$.data[0].count").value(3));
    }

    @Test
    void actionsUsesDedicatedAuditActionsRoute() throws Exception {
        when(jdbcTemplate.queryForList("SELECT DISTINCT action FROM audit_log ORDER BY action", String.class))
                .thenReturn(List.of("user.login", "admin.login"));

        mockMvc.perform(
                        get("/api/v1/admin/audit/actions")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0]").value("user.login"))
                .andExpect(jsonPath("$.data[1]").value("admin.login"));
    }

    @Test
    void logsRejectNonAdminRequests() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/audit/logs")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }
}
