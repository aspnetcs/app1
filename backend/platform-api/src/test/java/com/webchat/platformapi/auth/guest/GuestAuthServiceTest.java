package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private AuditService auditService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserIdentityRepository userIdentityRepository;
    @Mock
    private JwtService jwtService;

    private GuestAuthProperties properties;
    private GuestAuthService guestAuthService;

    @BeforeEach
    void setUp() {
        properties = new GuestAuthProperties();
        properties.setDeviceBindingTtlSeconds(2592000);
        properties.setRecoveryTokenTtlSeconds(2592000);
        when(jwtService.generateHs256(any(), any())).thenReturn("guest-recovery-token");
        guestAuthService = new GuestAuthService(
                userRepository,
                userIdentityRepository,
                authTokenService,
                auditService,
                redis,
                jwtService,
                properties
        );
    }

    @Test
    void issueGuestSessionReusesExistingGuestUserForSameDevice() {
        String deviceId = "device-abc";
        UserEntity guestUser = guestUser();
        AuthTokenService.IssuedTokens tokens = issuedTokens();

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(guestUser.getId().toString());
        when(userRepository.findByIdAndDeletedAtIsNull(guestUser.getId())).thenReturn(Optional.of(guestUser));
        when(authTokenService.issue(guestUser, null, "203.0.113.1", "JUnit")).thenReturn(tokens);

        Map<String, Object> response = guestAuthService.issueGuestSession(deviceId, null, null, "203.0.113.1", "JUnit");

        assertEquals(guestUser.getId().toString(), ((Map<?, ?>) response.get("userInfo")).get("userId"));
        assertEquals("guest", response.get("sessionType"));
        assertFalse((Boolean) response.get("isNewUser"));
        assertEquals("guest-recovery-token", response.get("guestRecoveryToken"));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(valueOperations).set(anyString(), eq(guestUser.getId().toString()), eq(Duration.ofDays(30)));
    }

    @Test
    void issueGuestSessionCreatesGuestUserWhenNoBindingExists() {
        String deviceId = "device-new";
        UserEntity guestUser = guestUser();
        AuthTokenService.IssuedTokens tokens = issuedTokens();

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenReturn(guestUser);
        when(authTokenService.issue(guestUser, null, "203.0.113.2", "JUnit")).thenReturn(tokens);

        Map<String, Object> response = guestAuthService.issueGuestSession(deviceId, null, null, "203.0.113.2", "JUnit");

        assertTrue((Boolean) response.get("isNewUser"));
        InOrder inOrder = inOrder(userRepository, auditService);
        inOrder.verify(userRepository).saveAndFlush(any(UserEntity.class));
        inOrder.verify(auditService).log(eq(guestUser.getId()), eq("auth.login.guest"), any(), eq("203.0.113.2"), eq("JUnit"));
        verify(valueOperations).set(anyString(), eq(guestUser.getId().toString()), eq(Duration.ofDays(30)));
    }

    @Test
    void issueGuestSessionFallsBackToDurableIdentityWhenRedisBindingMissing() {
        String deviceId = "device-db-only";
        UserEntity guestUser = guestUser();
        AuthTokenService.IssuedTokens tokens = issuedTokens();
        UserIdentityEntity identity = guestDeviceIdentity(guestUser.getId());

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(userIdentityRepository.findFirstByProviderAndProviderScopeAndTypeAndIdentifier(
                eq("guest"),
                eq("device"),
                eq("device_hash"),
                anyString()
        )).thenReturn(Optional.of(identity));
        when(userRepository.findByIdAndDeletedAtIsNull(guestUser.getId())).thenReturn(Optional.of(guestUser));
        when(authTokenService.issue(guestUser, null, "203.0.113.20", "JUnit")).thenReturn(tokens);

        Map<String, Object> response = guestAuthService.issueGuestSession(deviceId, null, null, "203.0.113.20", "JUnit");

        assertFalse((Boolean) response.get("isNewUser"));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(valueOperations).set(anyString(), eq(guestUser.getId().toString()), eq(Duration.ofDays(30)));
    }

    @Test
    void issueGuestSessionReusesGuestUserFromRecoveryTokenWhenDeviceIdChanges() {
        String recoveryToken = "recovery-token";
        UserEntity guestUser = guestUser();
        AuthTokenService.IssuedTokens tokens = issuedTokens();

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(jwtService.verifyHs256AndDecode(recoveryToken)).thenReturn(Map.of(
                "userId", guestUser.getId().toString(),
                "role", "guest",
                "purpose", "guest_recovery"
        ));
        when(userRepository.findByIdAndDeletedAtIsNull(guestUser.getId())).thenReturn(Optional.of(guestUser));
        when(authTokenService.issue(guestUser, null, "203.0.113.21", "JUnit")).thenReturn(tokens);

        Map<String, Object> response = guestAuthService.issueGuestSession("device-new", recoveryToken, null, "203.0.113.21", "JUnit");

        assertFalse((Boolean) response.get("isNewUser"));
        assertEquals("guest-recovery-token", response.get("guestRecoveryToken"));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userIdentityRepository).save(any(UserIdentityEntity.class));
    }

    @Test
    void issueGuestSessionReusesGuestUserFromBrowserFingerprintWhenLocalAnchorsAreMissing() {
        String fingerprint = "browser-fingerprint";
        UserEntity guestUser = guestUser();
        AuthTokenService.IssuedTokens tokens = issuedTokens();
        UserIdentityEntity identity = guestFingerprintIdentity(guestUser.getId());

        when(userIdentityRepository.findFirstByProviderAndProviderScopeAndTypeAndIdentifier(
                eq("guest"),
                eq("fingerprint"),
                eq("browser_fingerprint"),
                anyString()
        )).thenReturn(Optional.of(identity));
        when(userRepository.findByIdAndDeletedAtIsNull(guestUser.getId())).thenReturn(Optional.of(guestUser));
        when(authTokenService.issue(guestUser, null, "203.0.113.22", "JUnit")).thenReturn(tokens);

        Map<String, Object> response = guestAuthService.issueGuestSession(null, null, fingerprint, "203.0.113.22", "JUnit");

        assertFalse((Boolean) response.get("isNewUser"));
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(userIdentityRepository, never()).save(any(UserIdentityEntity.class));
    }

    private static UserEntity guestUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setRole("guest");
        user.setAvatar("");
        user.setCreatedAt(Instant.parse("2026-03-23T00:00:00Z"));
        return user;
    }

    private static AuthTokenService.IssuedTokens issuedTokens() {
        return new AuthTokenService.IssuedTokens(
                "access-token",
                "refresh-token",
                UUID.randomUUID(),
                3600,
                86400
        );
    }

    private static UserIdentityEntity guestDeviceIdentity(UUID userId) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider("guest");
        identity.setProviderScope("device");
        identity.setType("device_hash");
        identity.setIdentifier("hashed-device");
        identity.setVerified(true);
        return identity;
    }

    private static UserIdentityEntity guestFingerprintIdentity(UUID userId) {
        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider("guest");
        identity.setProviderScope("fingerprint");
        identity.setType("browser_fingerprint");
        identity.setIdentifier("hashed-fingerprint");
        identity.setVerified(true);
        return identity;
    }
}
