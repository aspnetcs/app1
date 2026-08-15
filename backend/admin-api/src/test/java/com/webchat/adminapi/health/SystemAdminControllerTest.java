package com.webchat.adminapi.health;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SystemAdminControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemAdminController(jdbcTemplate)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void infoUsesSystemRouteAndReturnsDatabaseSummary() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT version()", String.class)).thenReturn("PostgreSQL 16");
        when(jdbcTemplate.queryForList(
                "SELECT relname as table_name, n_live_tup as row_count FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10"
        )).thenReturn(List.of(Map.of("table_name", "audit_log", "row_count", 42)));

        mockMvc.perform(
                        get("/api/v1/admin/system/info")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.jvm.heapUsed").exists())
                .andExpect(jsonPath("$.data.threads.current").exists())
                .andExpect(jsonPath("$.data.database.version").value("PostgreSQL 16"))
                .andExpect(jsonPath("$.data.database.tables[0].table_name").value("audit_log"));
    }

    @Test
    void infoRejectsNonAdminRequests() throws Exception {
        mockMvc.perform(
                        get("/api/v1/admin/system/info")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, UUID.randomUUID())
                                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "user")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(jdbcTemplate);
    }
}
