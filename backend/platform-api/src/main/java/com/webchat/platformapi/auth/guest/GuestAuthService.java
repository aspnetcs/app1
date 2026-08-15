package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.common.util.UserInfoUtils;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class GuestAuthService {

    public static final String SESSION_TYPE = "guest";
    public static final String GUEST_ROLE = "guest";
    public static final String GUEST_DEVICE_PROVIDER = "guest";
    public static final String GUEST_DEVICE_SCOPE = "device";
    public static final String GUEST_DEVICE_TYPE = "device_hash";
    public static final String GUEST_FINGERPRINT_SCOPE = "fingerprint";
    public static final String GUEST_FINGERPRINT_TYPE = "browser_fingerprint";
    public static final String GUEST_RECOVERY_PURPOSE = "guest_recovery";

    private static final Logger log = LoggerFactory.getLogger(GuestAuthService.class);

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final AuthTokenService authTokenService;
    private final AuditService auditService;
    private final StringRedisTemplate redis;
    private final JwtService jwtService;
    private final GuestAuthProperties properties;

    public GuestAuthService(
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            AuthTokenService authTokenService,
            AuditService auditService,
            StringRedisTemplate redis,
            JwtService jwtService,
            GuestAuthProperties properties
    ) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.authTokenService = authTokenService;
        this.auditService = auditService;
        this.redis = redis;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional(rollbackOn = Exception.class)
    public Map<String, Object> issueGuestSession(
            String deviceId,
            String recoveryToken,
            String deviceFingerprint,
            String clientIp,
            String userAgent
    ) {
        String normalizedDeviceId = normalizeIdentifier(deviceId);
        String normalizedFingerprint = normalizeIdentifier(deviceFingerprint);
        UserEntity guestUser = findGuestUserByRecoveryToken(recoveryToken);
        if (guestUser == null) {
            guestUser = findReusableGuestUser(normalizedDeviceId, normalizedFingerprint);
        }

        boolean isNewUser = guestUser == null;
        if (guestUser == null) {
            guestUser = new UserEntity();
            guestUser.setRole(GUEST_ROLE);
            guestUser.setAvatar("");
            // Audit logging uses JdbcTemplate, so the new guest user must be
            // flushed before we insert an audit row that references it.
            guestUser = userRepository.saveAndFlush(guestUser);
        }

        bindGuestIdentifiers(normalizedDeviceId, normalizedFingerprint, guestUser.getId());

        AuthTokenService.IssuedTokens tokens = authTokenService.issue(guestUser, null, clientIp, userAgent);
        auditService.log(guestUser.getId(), "auth.login.guest", Map.of(
                "sessionType", SESSION_TYPE,
                "isNewUser", isNewUser
        ), clientIp, userAgent);
        return toLoginResponse(guestUser, tokens, isNewUser, issueGuestRecoveryToken(guestUser));
    }

    public UserEntity findGuestUserByRecoveryToken(String recoveryToken) {
        String normalizedRecoveryToken = RequestUtils.trimOrNull(recoveryToken);
        if (normalizedRecoveryToken == null) {
            return null;
        }
        try {
            Map<String, Object> payload = jwtService.verifyHs256AndDecode(normalizedRecoveryToken);
            if (!GUEST_RECOVERY_PURPOSE.equals(RequestUtils.trimOrNull(String.valueOf(payload.get("purpose"))))) {
                return null;
            }
            if (!GUEST_ROLE.equalsIgnoreCase(RequestUtils.trimOrNull(String.valueOf(payload.get("role"))))) {
                return null;
            }
            String userId = RequestUtils.trimOrNull(String.valueOf(payload.get("userId")));
            if (userId == null) {
                return null;
            }
            UserEntity guestUser = userRepository.findByIdAndDeletedAtIsNull(UUID.fromString(userId)).orElse(null);
            return isGuest(guestUser) ? guestUser : null;
        } catch (Exception e) {
            log.debug("[guest-auth] resolve guest recovery token failed: {}", e.toString());
            return null;
        }
    }

    static String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 128) {
            normalized = normalized.substring(0, 128);
        }
        return normalized;
    }

    private Map<String, Object> toLoginResponse(
            UserEntity guestUser,
            AuthTokenService.IssuedTokens tokens,
            boolean isNewUser,
            String guestRecoveryToken
    ) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", tokens.accessToken());
        response.put("accessToken", tokens.accessToken());
        response.put("refreshToken", tokens.refreshToken());
        response.put("expiresIn", tokens.accessExpiresIn());
        response.put("refreshExpiresIn", tokens.refreshExpiresIn());
        response.put("sessionId", tokens.sessionId().toString());
        response.put("userInfo", UserInfoUtils.toGuestUserInfo(guestUser, "\u8bbf\u5ba2", GUEST_ROLE));
        response.put("isNewUser", isNewUser);
        response.put("sessionType", SESSION_TYPE);
        response.put("guestRecoveryToken", guestRecoveryToken);
        return response;
    }

    private UserEntity findReusableGuestUser(String deviceId, String deviceFingerprint) {
        UserEntity deviceGuest = findGuestUserByDeviceBinding(deviceId);
        if (deviceGuest != null) {
            return deviceGuest;
        }
        return findGuestUserByFingerprintBinding(deviceFingerprint);
    }

    private UserEntity findGuestUserByDeviceBinding(String deviceId) {
        if (deviceId == null) {
            return null;
        }
        UserEntity redisBoundGuest = findGuestUserByRedisBinding(deviceId);
        if (redisBoundGuest != null) {
            return redisBoundGuest;
        }
        return findGuestUserByDurableBinding(GUEST_DEVICE_SCOPE, GUEST_DEVICE_TYPE, deviceId);
    }

    private UserEntity findGuestUserByFingerprintBinding(String deviceFingerprint) {
        if (deviceFingerprint == null) {
            return null;
        }
        return findGuestUserByDurableBinding(GUEST_FINGERPRINT_SCOPE, GUEST_FINGERPRINT_TYPE, deviceFingerprint);
    }

    private UserEntity findGuestUserByRedisBinding(String deviceId) {
        try {
            String userId = redis.opsForValue().get(deviceBindingKey(deviceId));
            if (userId == null || userId.isBlank()) {
                return null;
            }
            UserEntity guestUser = userRepository.findByIdAndDeletedAtIsNull(UUID.fromString(userId.trim())).orElse(null);
            if (!isGuest(guestUser)) {
                return null;
            }
            return guestUser;
        } catch (Exception e) {
            log.warn("[guest-auth] resolve redis guest device binding failed: {}", e.toString());
            return null;
        }
    }

    private UserEntity findGuestUserByDurableBinding(String providerScope, String type, String rawIdentifier) {
        try {
            UserIdentityEntity identity = userIdentityRepository
                    .findFirstByProviderAndProviderScopeAndTypeAndIdentifier(
                            GUEST_DEVICE_PROVIDER,
                            providerScope,
                            type,
                            RequestUtils.sha256Hex(rawIdentifier)
                    )
                    .orElse(null);
            if (identity == null) {
                return null;
            }
            UserEntity guestUser = userRepository.findByIdAndDeletedAtIsNull(identity.getUserId()).orElse(null);
            return isGuest(guestUser) ? guestUser : null;
        } catch (Exception e) {
            log.warn("[guest-auth] resolve durable guest binding failed: scope={}, type={}, error={}",
                    providerScope, type, e.toString());
            return null;
        }
    }

    private void bindGuestIdentifiers(String deviceId, String deviceFingerprint, UUID userId) {
        bindGuestDevice(deviceId, userId);
        bindGuestFingerprint(deviceFingerprint, userId);
    }

    private void bindGuestDevice(String deviceId, UUID userId) {
        if (deviceId == null || userId == null) {
            return;
        }
        try {
            redis.opsForValue().set(deviceBindingKey(deviceId), userId.toString(), properties.deviceBindingTtl());
        } catch (Exception e) {
            log.warn("[guest-auth] bind guest device failed: userId={}, error={}", userId, e.toString());
        }
        try {
            upsertGuestIdentity(deviceId, userId, GUEST_DEVICE_SCOPE, GUEST_DEVICE_TYPE, true);
        } catch (Exception e) {
            log.warn("[guest-auth] persist guest device identity failed: userId={}, error={}", userId, e.toString());
        }
    }

    private void bindGuestFingerprint(String deviceFingerprint, UUID userId) {
        if (deviceFingerprint == null || userId == null) {
            return;
        }
        try {
            upsertGuestIdentity(deviceFingerprint, userId, GUEST_FINGERPRINT_SCOPE, GUEST_FINGERPRINT_TYPE, false);
        } catch (Exception e) {
            log.warn("[guest-auth] persist guest fingerprint identity failed: userId={}, error={}", userId, e.toString());
        }
    }

    private void upsertGuestIdentity(
            String rawIdentifier,
            UUID userId,
            String providerScope,
            String type,
            boolean allowRebind
    ) {
        String identifierHash = RequestUtils.sha256Hex(rawIdentifier);
        UserIdentityEntity existing = userIdentityRepository
                .findFirstByProviderAndProviderScopeAndTypeAndIdentifier(
                        GUEST_DEVICE_PROVIDER,
                        providerScope,
                        type,
                        identifierHash
                )
                .orElse(null);
        if (existing != null) {
            if (userId.equals(existing.getUserId())) {
                return;
            }
            if (!allowRebind) {
                return;
            }
            existing.setUserId(userId);
            existing.setVerified(true);
            userIdentityRepository.save(existing);
            return;
        }

        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setUserId(userId);
        identity.setProvider(GUEST_DEVICE_PROVIDER);
        identity.setProviderScope(providerScope);
        identity.setType(type);
        identity.setIdentifier(identifierHash);
        identity.setVerified(true);
        userIdentityRepository.save(identity);
    }

    private String issueGuestRecoveryToken(UserEntity guestUser) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "guest-" + guestUser.getId());
        claims.put("userId", guestUser.getId().toString());
        claims.put("role", GUEST_ROLE);
        claims.put("purpose", GUEST_RECOVERY_PURPOSE);
        return jwtService.generateHs256(claims, properties.recoveryTokenTtl());
    }

    private static boolean isGuest(UserEntity guestUser) {
        if (guestUser == null) {
            return false;
        }
        String role = guestUser.getRole();
        return role != null && GUEST_ROLE.equalsIgnoreCase(role.trim());
    }

    private static String deviceBindingKey(String deviceId) {
        return "guest:device:" + RequestUtils.sha256Hex(deviceId);
    }
}
