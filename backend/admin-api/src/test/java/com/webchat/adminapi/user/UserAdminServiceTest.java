package com.webchat.adminapi.user;

import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private RolePolicyProperties rolePolicyProperties;

    @Mock
    private RolePolicyService rolePolicyService;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userRepository, jdbcTemplate, rolePolicyProperties, rolePolicyService);
    }

    @Test
    void updateReturnsFullDetailAndRevokesSessionsForDisable() {
        UUID userId = UUID.randomUUID();
        UserEntity user = createUser(userId, "user", null, 12, 3);
        RolePolicyProperties.RoleConfig config = new RolePolicyProperties.RoleConfig();
        config.setDailyChatQuota(20);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePolicyProperties.getPolicy("premium")).thenReturn(config);
        when(rolePolicyService.getDailyUsageCount(userId)).thenReturn(7L);

        ApiResponse<Map<String, Object>> response = service.update(userId, Map.of(
                "role", "premium",
                "status", "disabled",
                "tokenQuota", 88
        ));

        assertEquals(0, response.code());
        assertNotNull(response.data());
        assertEquals("premium", response.data().get("role"));
        assertEquals("disabled", response.data().get("status"));
        assertEquals(88L, response.data().get("tokenQuota"));
        assertEquals(7L, response.data().get("dailyQuotaUsed"));
        assertEquals(20, response.data().get("dailyQuotaLimit"));
        assertNotNull(user.getDeletedAt());
        verify(jdbcTemplate).update(any(String.class), eq(userId));
    }

    @Test
    void updateCanRestoreDisabledUser() {
        UUID userId = UUID.randomUUID();
        UserEntity user = createUser(userId, "guest", Instant.now(), 0, 0);
        RolePolicyProperties.RoleConfig config = new RolePolicyProperties.RoleConfig();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rolePolicyProperties.getPolicy("guest")).thenReturn(config);
        when(rolePolicyService.getDailyUsageCount(userId)).thenReturn(0L);

        ApiResponse<Map<String, Object>> response = service.update(userId, Map.of("status", "active"));

        assertEquals(0, response.code());
        assertNotNull(response.data());
        assertEquals("active", response.data().get("status"));
        assertNull(user.getDeletedAt());
        verify(jdbcTemplate, never()).update(any(String.class), eq(userId));
    }

    @Test
    void parseHelpersAcceptRealAdminInputs() {
        assertEquals(120L, UserAdminService.parseTokenQuota("120"));
        assertEquals(0L, UserAdminService.parseTokenQuota(-3));
        assertEquals(Boolean.TRUE, UserAdminService.parseStatusFlag("disabled"));
        assertEquals(Boolean.FALSE, UserAdminService.parseStatusFlag("active"));
        assertTrue(UserAdminService.parseStatusFlag("oops") == null);
    }

    private static UserEntity createUser(UUID id, String role, Instant deletedAt, long tokenQuota, long tokenUsed) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setRole(role);
        user.setPhone("13800000000");
        user.setEmail("demo@example.com");
        user.setTokenQuota(tokenQuota);
        user.setTokenUsed(tokenUsed);
        user.setCreatedAt(Instant.parse("2026-03-24T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-03-24T01:00:00Z"));
        user.setDeletedAt(deletedAt);
        return user;
    }
}
