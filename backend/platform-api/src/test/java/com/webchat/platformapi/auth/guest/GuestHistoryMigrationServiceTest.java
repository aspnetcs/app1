package com.webchat.platformapi.auth.guest;


import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.ai.usage.AiUsageLogRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestHistoryMigrationServiceTest {

    @Mock
    private GuestAuthService guestAuthService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiConversationRepository aiConversationRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AiUsageLogRepository aiUsageLogRepository;

    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;

    private GuestAuthProperties properties;
    private GuestHistoryMigrationService migrationService;

    @BeforeEach
    void setUp() {
        properties = new GuestAuthProperties();
        properties.setHistoryMigrationEnabled(true);
        migrationService = new GuestHistoryMigrationService(
                properties,
                guestAuthService,
                userRepository,
                aiConversationRepository,
                agentRepository,
                aiUsageLogRepository,
                deviceSessionRepository,
                userIdentityRepository
        );
    }

    @Test
    void migratesGuestHistoryFromBearerGuestSession() {
        UUID sourceUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserEntity guestUser = guestUser(sourceUserId);
        DeviceSessionEntity guestSession = new DeviceSessionEntity();
        guestSession.setUserId(sourceUserId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthFilter.ATTR_USER_ID, sourceUserId);
        request.setAttribute(JwtAuthFilter.ATTR_USER_ROLE, "guest");

        when(userRepository.findByIdAndDeletedAtIsNull(sourceUserId)).thenReturn(Optional.of(guestUser));

        when(agentRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(sourceUserId)).thenReturn(List.of());
        when(deviceSessionRepository.findAllByUserIdAndRevokedAtIsNull(sourceUserId)).thenReturn(List.of(guestSession));

        migrationService.migrateGuestHistory(request, targetUserId);

        verify(aiConversationRepository).reassignUserId(sourceUserId, targetUserId);
        verify(aiUsageLogRepository).reassignUserId(sourceUserId, targetUserId);
        verify(userIdentityRepository).deleteAllByUserIdAndProvider(sourceUserId, "guest");
        verify(userIdentityRepository).deleteAllByUserIdAndProvider(sourceUserId, "guest");
        verify(userRepository).save(guestUser);
        assertNotNull(guestUser.getDeletedAt());
        assertNotNull(guestSession.getRevokedAt());
    }

    @Test
    void migratesGuestHistoryFromRecoveryTokenWhenBearerGuestMissing() {
        UUID sourceUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserEntity guestUser = guestUser(sourceUserId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GuestHistoryMigrationService.GUEST_RECOVERY_TOKEN_HEADER, "recovery-token");

        when(guestAuthService.findGuestUserByRecoveryToken("recovery-token")).thenReturn(guestUser);

        when(agentRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(sourceUserId)).thenReturn(List.of());
        when(deviceSessionRepository.findAllByUserIdAndRevokedAtIsNull(sourceUserId)).thenReturn(List.of());

        migrationService.migrateGuestHistory(request, targetUserId);

        verify(guestAuthService).findGuestUserByRecoveryToken("recovery-token");
        verify(aiConversationRepository).reassignUserId(sourceUserId, targetUserId);
    }

    @Test
    void migratesGuestHistoryFromRecoveryCookieWhenHeaderMissing() {
        UUID sourceUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UserEntity guestUser = guestUser(sourceUserId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("guest_recovery", "cookie-recovery-token"));

        when(guestAuthService.findGuestUserByRecoveryToken("cookie-recovery-token")).thenReturn(guestUser);

        when(agentRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(sourceUserId)).thenReturn(List.of());
        when(deviceSessionRepository.findAllByUserIdAndRevokedAtIsNull(sourceUserId)).thenReturn(List.of());

        migrationService.migrateGuestHistory(request, targetUserId);

        verify(guestAuthService).findGuestUserByRecoveryToken("cookie-recovery-token");
        verify(aiConversationRepository).reassignUserId(sourceUserId, targetUserId);
    }

    @Test
    void skipsMigrationWhenSourceGuestMatchesTargetUser() {
        UUID targetUserId = UUID.randomUUID();
        UserEntity guestUser = guestUser(targetUserId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(GuestHistoryMigrationService.GUEST_RECOVERY_TOKEN_HEADER, "recovery-token");

        when(guestAuthService.findGuestUserByRecoveryToken("recovery-token")).thenReturn(guestUser);

        migrationService.migrateGuestHistory(request, targetUserId);

        verify(aiConversationRepository, never()).reassignUserId(targetUserId, targetUserId);
        verify(userRepository, never()).save(guestUser);
    }



    private static UserEntity guestUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole("guest");
        return user;
    }
}
