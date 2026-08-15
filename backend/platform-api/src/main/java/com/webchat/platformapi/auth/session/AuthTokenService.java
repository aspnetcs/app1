package com.webchat.platformapi.auth.session;

import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.guest.GuestAuthProperties;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthTokenService {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);
    private static final String GUEST_ROLE = "guest";

    private final JwtService jwtService;
    private final DeviceSessionRepository deviceSessionRepository;
    private final UserRepository userRepository;
    private final GuestAuthProperties guestAuthProperties;
    private final String salt;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthTokenService(
            JwtService jwtService,
            DeviceSessionRepository deviceSessionRepository,
            UserRepository userRepository,
            GuestAuthProperties guestAuthProperties,
            @Value("${jwt.secret}") String salt
    ) {
        this.jwtService = jwtService;
        this.deviceSessionRepository = deviceSessionRepository;
        this.userRepository = userRepository;
        this.guestAuthProperties = guestAuthProperties;
        this.salt = salt == null ? "" : salt;
    }

    public record DeviceInfo(String deviceType, String deviceName, String brand) {}

    public record IssuedTokens(
            String accessToken,
            String refreshToken,
            UUID sessionId,
            long accessExpiresIn,
            long refreshExpiresIn
    ) {}

    public IssuedTokens issue(UserEntity user, DeviceInfo device, String ip, String userAgent) {
        Instant now = Instant.now();
        TokenPolicy tokenPolicy = resolveIssuePolicy(user);
        String refreshToken = generateRefreshToken();
        String refreshHash = RequestUtils.sha256Hex(salt + ":" + refreshToken);

        DeviceSessionEntity session = new DeviceSessionEntity();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(refreshHash);
        String deviceType = device == null ? null : emptyToNull(device.deviceType());
        deviceType = truncate(deviceType, 20);
        session.setDeviceType(blank(deviceType) ? "unknown" : deviceType);
        session.setDeviceName(truncate(device == null ? null : emptyToNull(device.deviceName()), 128));
        session.setBrand(truncate(device == null ? null : emptyToNull(device.brand()), 32));
        session.setIp(truncate(emptyToNull(ip), 45));
        session.setUserAgent(truncate(emptyToNull(userAgent), 512));
        session.setExpiresAt(now.plus(tokenPolicy.refreshTtl()));
        session.setLastActiveAt(now);

        DeviceSessionEntity saved = deviceSessionRepository.save(session);

        String accessToken = jwtService.generateHs256(buildClaims(user, saved.getId()), tokenPolicy.accessTtl());
        return new IssuedTokens(
                accessToken,
                refreshToken,
                saved.getId(),
                tokenPolicy.accessTtl().toSeconds(),
                tokenPolicy.refreshTtl().toSeconds()
        );
    }

    public IssuedTokens refresh(String refreshToken, String ip, String userAgent) throws InvalidRefreshTokenException {
        if (refreshToken == null || refreshToken.isBlank()) throw new InvalidRefreshTokenException();

        Instant now = Instant.now();
        String refreshHash = RequestUtils.sha256Hex(salt + ":" + refreshToken);
        DeviceSessionEntity session = deviceSessionRepository.findByRefreshTokenHash(refreshHash).orElse(null);
        if (session == null) throw new InvalidRefreshTokenException();
        if (session.getRevokedAt() != null) throw new InvalidRefreshTokenException();
        if (session.getExpiresAt() != null && now.isAfter(session.getExpiresAt())) throw new InvalidRefreshTokenException();

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(session.getUserId()).orElse(null);
        if (user == null) throw new InvalidRefreshTokenException();
        TokenPolicy tokenPolicy = resolveRefreshPolicy(user);

        // rotate
        String nextRefreshToken = generateRefreshToken();
        String nextHash = RequestUtils.sha256Hex(salt + ":" + nextRefreshToken);

        DeviceSessionEntity next = new DeviceSessionEntity();
        next.setUserId(session.getUserId());
        next.setRefreshTokenHash(nextHash);
        next.setDeviceType(session.getDeviceType());
        next.setDeviceName(session.getDeviceName());
        next.setBrand(session.getBrand());
        next.setIp(truncate(emptyToNull(ip), 45));
        next.setUserAgent(truncate(emptyToNull(userAgent), 512));
        next.setExpiresAt(now.plus(tokenPolicy.refreshTtl()));
        next.setLastActiveAt(now);
        next.setRotatedFrom(session.getId());

        DeviceSessionEntity savedNext = deviceSessionRepository.save(next);

        session.setRevokedAt(now);
        session.setReplacedBy(savedNext.getId());
        session.setLastActiveAt(now);
        session.setIp(truncate(emptyToNull(ip), 45));
        session.setUserAgent(truncate(emptyToNull(userAgent), 512));
        deviceSessionRepository.save(session);

        String accessToken = jwtService.generateHs256(buildClaims(user, savedNext.getId()), tokenPolicy.accessTtl());
        return new IssuedTokens(
                accessToken,
                nextRefreshToken,
                savedNext.getId(),
                tokenPolicy.accessTtl().toSeconds(),
                tokenPolicy.refreshTtl().toSeconds()
        );
    }

    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        Instant now = Instant.now();
        String refreshHash = RequestUtils.sha256Hex(salt + ":" + refreshToken);
        DeviceSessionEntity session = deviceSessionRepository.findByRefreshTokenHash(refreshHash).orElse(null);
        if (session == null) return;
        if (session.getRevokedAt() != null) return;
        session.setRevokedAt(now);
        session.setLastActiveAt(now);
        deviceSessionRepository.save(session);
    }

    private static Map<String, Object> buildClaims(UserEntity user, UUID sessionId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("sid", sessionId.toString());
        String role = user.getRole();
        claims.put("role", role == null || role.isBlank() ? "user" : role);
        return claims;
    }

    private String generateRefreshToken() {
        byte[] buf = new byte[32];
        secureRandom.nextBytes(buf);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private TokenPolicy resolveIssuePolicy(UserEntity user) {
        if (isGuest(user)) {
            return new TokenPolicy(guestAuthProperties.accessTokenTtl(), guestAuthProperties.refreshTokenTtl());
        }
        return new TokenPolicy(ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL);
    }

    private TokenPolicy resolveRefreshPolicy(UserEntity user) throws InvalidRefreshTokenException {
        if (isGuest(user)) {
            if (!guestAuthProperties.isRefreshEnabled()) {
                throw new InvalidRefreshTokenException();
            }
            return new TokenPolicy(guestAuthProperties.accessTokenTtl(), guestAuthProperties.refreshTokenTtl());
        }
        return new TokenPolicy(ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL);
    }

    private static boolean isGuest(UserEntity user) {
        if (user == null) return false;
        String role = user.getRole();
        return role != null && GUEST_ROLE.equalsIgnoreCase(role.trim());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (maxLen <= 0) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }

    private record TokenPolicy(Duration accessTtl, Duration refreshTtl) {}

    public static class InvalidRefreshTokenException extends Exception {}
}
