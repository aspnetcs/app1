package com.webchat.adminapi.dashboard;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(jdbcTemplate)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void overviewUsesDashboardRouteAndReturnsAggregates() throws Exception {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Long.class), any(Timestamp.class)))
                .thenReturn(3L, 7L, 11L);
        when(jdbcTemplate.queryForList(contains("GROUP BY model"), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("model", "gpt-4o", "request_count", 9, "total_tokens", 1200)));
        when(jdbcTemplate.queryForList(contains("GROUP BY user_id"), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("user_id", adminUserId.toString(), "request_count", 4, "total_tokens", 800)));
        when(jdbcTemplate.queryForList(contains("GROUP BY TO_CHAR"), any(Timestamp.class)))
                .thenReturn(List.of(Map.of("day", "2026-03-23", "total_tokens", 1200, "request_count", 9)));

        mockMvc.perform(
                        get("/api/v1/admin/dashboard/overview")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.todayConversations").value(3))
                .andExpect(jsonPath("$.data.weekConversations").value(7))
                .andExpect(jsonPath("$.data.monthConversations").value(11))
                .andExpect(jsonPath("$.data.modelRanking[0].model").value("gpt-4o"))
                .andExpect(jsonPath("$.data.userRanking[0].user_id").value(adminUserId.toString()))
                .andExpect(jsonPath("$.data.tokenTrend[0].day").value("2026-03-23"));

        verify(jdbcTemplate, times(3)).queryForObject(any(String.class), eq(Long.class), any(Timestamp.class));
    }

    @Test
    void overviewRejectsNonAdminRequests() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/dashboard/overview")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }
}
