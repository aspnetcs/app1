package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestAuthTokenPolicyTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private UserRepository userRepository;

    private GuestAuthProperties properties;
    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        properties = new GuestAuthProperties();
        properties.setAccessTokenTtlSeconds(1800);
        properties.setRefreshTokenTtlSeconds(7200);
        properties.setRefreshEnabled(true);
        authTokenService = new AuthTokenService(jwtService, deviceSessionRepository, userRepository, properties, "12345678901234567890123456789012");
    }

    @Test
    void issueUsesGuestTokenLifecycle() {
        UserEntity guestUser = guestUser();
        DeviceSessionEntity savedSession = new DeviceSessionEntity();
        savedSession.setId(UUID.randomUUID());
        when(deviceSessionRepository.save(any(DeviceSessionEntity.class))).thenReturn(savedSession);
        when(jwtService.generateHs256(any(), eq(properties.accessTokenTtl()))).thenReturn("guest-access-token");

        AuthTokenService.IssuedTokens tokens = authTokenService.issue(guestUser, null, "203.0.113.1", "JUnit");

        assertEquals(1800L, tokens.accessExpiresIn());
        assertEquals(7200L, tokens.refreshExpiresIn());
        assertEquals("guest-access-token", tokens.accessToken());
    }

    @Test
    void refreshRejectsGuestSessionWhenGuestRefreshDisabled() {
        UserEntity guestUser = guestUser();
        DeviceSessionEntity session = new DeviceSessionEntity();
        String refreshToken = "refresh-token";
        String refreshHash = RequestUtils.sha256Hex("12345678901234567890123456789012:" + refreshToken);
        session.setId(UUID.randomUUID());
        session.setUserId(guestUser.getId());
        session.setRefreshTokenHash(refreshHash);
        session.setExpiresAt(Instant.now().plusSeconds(60));
        properties.setRefreshEnabled(false);

        when(deviceSessionRepository.findByRefreshTokenHash(refreshHash)).thenReturn(Optional.of(session));
        when(userRepository.findByIdAndDeletedAtIsNull(guestUser.getId())).thenReturn(Optional.of(guestUser));

        assertThrows(AuthTokenService.InvalidRefreshTokenException.class, () ->
                authTokenService.refresh(refreshToken, "203.0.113.1", "JUnit"));
    }

    private static UserEntity guestUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setRole("guest");
        return user;
    }
}
