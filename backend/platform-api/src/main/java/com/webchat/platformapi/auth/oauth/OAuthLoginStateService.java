package com.webchat.platformapi.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class OAuthLoginStateService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthLoginStateService(StringRedisTemplate redis, ObjectMapper objectMapper, OAuthProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record LoginState(
            String provider,
            String redirectUri,
            String callbackUri,
            String deviceType,
            String deviceName,
            String brand
    ) {
    }

    public String issue(LoginState state) throws OAuthStateException {
        if (state == null) throw new OAuthStateException("oauth state missing");
        String token = randomToken("os_");
        try {
            redis.opsForValue().set(key(token), objectMapper.writeValueAsString(state),
                    Duration.ofSeconds(Math.max(30, properties.getStateTtlSeconds())));
            return token;
        } catch (Exception e) {
            throw new OAuthStateException("oauth state store failed", e);
        }
    }

    public LoginState consume(String stateToken) throws OAuthStateException {
        if (stateToken == null || stateToken.isBlank()) return null;
        try {
            String json = redis.opsForValue().get(key(stateToken));
        if (json != null) redis.delete(key(stateToken));
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, LoginState.class);
        } catch (Exception e) {
            throw new OAuthStateException("oauth state read failed", e);
        }
    }

    private String key(String token) {
        return "oauth:state:" + token;
    }

    private String randomToken(String prefix) {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class OAuthStateException extends Exception {
        public OAuthStateException(String message) {
            super(message);
        }

        public OAuthStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
