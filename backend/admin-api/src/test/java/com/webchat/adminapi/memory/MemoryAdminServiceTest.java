package com.webchat.adminapi.memory;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryAdminServiceTest {

    @Mock
    private SysConfigService sysConfigService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private MemoryAdminService memoryAdminService;

    @Test
    void configAndStatsReadFromStorage() {
        when(sysConfigService.getOrDefault("platform.memory.enabled", "false")).thenReturn("true");
        when(sysConfigService.getOrDefault("platform.memory.require-consent", "true")).thenReturn("false");
        when(sysConfigService.getOrDefault("platform.memory.max-entries-per-user", "50")).thenReturn("80");
        when(sysConfigService.getOrDefault("platform.memory.max-chars-per-entry", "1000")).thenReturn("1500");
        when(sysConfigService.getOrDefault("platform.memory.retention-days", "30")).thenReturn("45");
        when(sysConfigService.getOrDefault("platform.memory.summary-model", "")).thenReturn("memory-model");
        when(jdbcTemplate.queryForObject("select count(*) from memory_consent", Long.class)).thenReturn(2L);
        when(jdbcTemplate.queryForObject("select count(*) from memory_entry", Long.class)).thenReturn(6L);
        when(jdbcTemplate.queryForObject("select count(*) from memory_audit where status = 'pending'", Long.class)).thenReturn(1L);

        Map<String, Object> config = memoryAdminService.getConfig();
        Map<String, Object> stats = memoryAdminService.getStats();

        assertThat(config).containsEntry("summaryModel", "memory-model");
        assertThat(stats).containsEntry("totalEntries", 6L);
        assertThat(stats).containsEntry("averageEntriesPerUser", 3.0);
    }

    @Test
    void auditsReturnPagedEnvelope() {
        when(jdbcTemplate.queryForObject("select count(*) from memory_audit", Long.class)).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.PreparedStatementSetter.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(Map.of(
                        "id", UUID.randomUUID().toString(),
                        "userId", UUID.randomUUID().toString(),
                        "action", "consent_update",
                        "summary", "updated",
                        "status", "success",
                        "createdAt", Timestamp.from(Instant.parse("2026-04-18T00:00:00Z")).toInstant().toString()
                )));

        Map<String, Object> result = memoryAdminService.getAudits(0, 20);

        assertThat(result).containsEntry("total", 1L);
        assertThat((List<?>) result.get("items")).hasSize(1);
    }
}
