package com.webchat.platformapi.auth.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RedisVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RedisVerificationService.class);

    private static final Duration CODE_TTL = Duration.ofSeconds(300);
    private static final Duration SEND_LIMIT_TTL = Duration.ofSeconds(60);
    private static final int DAILY_LIMIT_SMS = 10;
    private static final int DAILY_LIMIT_EMAIL = 20;
    private static final String MOCK_CODES_KEY = "dev:mock_codes";
    private static final int MOCK_CODES_MAX = 50;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String salt;
    private final boolean mockCodesEnabled;

    public RedisVerificationService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${jwt.secret}") String salt,
            @Value("${platform.auth.mock-codes.enabled:false}") boolean mockCodesEnabled
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.salt = salt == null ? "" : salt;
        this.mockCodesEnabled = mockCodesEnabled;
    }

    public boolean consumeChallengeToken(String token) {
        if (token == null || token.isBlank()) return false;
        String key = "challenge:" + token;
        try {
            return Boolean.TRUE.equals(redis.delete(key));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean consumeChallengeToken(String token, String requesterIp) {
        if (token == null || token.isBlank()) return false;
        String key = "challenge:" + token;
        try {
            String json = redis.opsForValue().get(key);
        if (json != null) redis.delete(key);
            if (json == null || json.isBlank()) return false;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            Object expiresAtObj = data.get("expires_at");
            long now = System.currentTimeMillis();
            if (expiresAtObj instanceof Number number && now > number.longValue()) {
                return false;
            }
            String expectedIpHash = data.get("ip_hash") == null ? "" : String.valueOf(data.get("ip_hash"));
            String actualIpHash = RequestUtils.sha256Hex(requesterIp == null ? "" : requesterIp.trim());
            return MessageDigest.isEqual(expectedIpHash.getBytes(StandardCharsets.UTF_8), actualIpHash.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public String sendSmsCode(String phone, String purpose) {
        enforceSendLimit("sms", phone, DAILY_LIMIT_SMS);
        String code = randomDigits(6);
        String codeHash = RequestUtils.sha256Hex(salt + ":" + code);

        String key = "sms_code:" + purpose + ":" + phone;
        setJson(key, Map.of(
                "code_hash", codeHash,
                "expires_at", System.currentTimeMillis() + CODE_TTL.toMillis(),
                "error_count", 0
        ), CODE_TTL);

        logMockCode("sms:" + phone, code, purpose);
        return code;
    }

    public String sendEmailCode(String email, String purpose) {
        enforceSendLimit("email", email, DAILY_LIMIT_EMAIL);
        String code = randomDigits(6);
        String codeHash = RequestUtils.sha256Hex(salt + ":" + code);

        String key = "email_code:" + purpose + ":" + email;
        setJson(key, Map.of(
                "code_hash", codeHash,
                "expires_at", System.currentTimeMillis() + CODE_TTL.toMillis(),
                "error_count", 0
        ), CODE_TTL);

        logMockCode("email:" + email, code, purpose);
        return code;
    }

    public boolean verifySmsCode(String phone, String purpose, String code) {
        return verifyCode("sms_code:" + purpose + ":" + phone, code);
    }

    public boolean verifyEmailCode(String email, String purpose, String code) {
        return verifyCode("email_code:" + purpose + ":" + email, code);
    }

    // ===== Mock Code Panel =====

    public List<Map<String, Object>> getMockCodes() {
        if (!mockCodesEnabled) return List.of();
        try {
            List<String> items = redis.opsForList().range(MOCK_CODES_KEY, 0, MOCK_CODES_MAX - 1);
            if (items == null || items.isEmpty()) return List.of();
            long now = System.currentTimeMillis();
            List<Map<String, Object>> out = new ArrayList<>();
            for (String json : items) {
                @SuppressWarnings("unchecked")
                Map<String, Object> item = objectMapper.readValue(json, Map.class);
                Object expireAt = item.get("expireAt");
                boolean expired = expireAt instanceof Number n && now > n.longValue();
                Map<String, Object> entry = new LinkedHashMap<>(item);
                entry.put("expired", expired);
                out.add(entry);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public void clearMockCodes() {
        if (!mockCodesEnabled) return;
        try {
            redis.delete(MOCK_CODES_KEY);
        } catch (Exception e) {
            log.warn("[verify] clearMockCodes failed", e);
        }
    }

    private void logMockCode(String target, String code, String purpose) {
        if (!mockCodesEnabled) return;
        try {
            long now = System.currentTimeMillis();
            long expireAt = now + CODE_TTL.toMillis();
            String json = objectMapper.writeValueAsString(Map.of(
                    "target", target,
                    "code", code,
                    "purpose", purpose,
                    "time", now,
                    "expireAt", expireAt
            ));
            redis.opsForList().leftPush(MOCK_CODES_KEY, json);
            redis.opsForList().trim(MOCK_CODES_KEY, 0, MOCK_CODES_MAX - 1);
            redis.expire(MOCK_CODES_KEY, Duration.ofHours(24));
        } catch (Exception e) {
            log.warn("[verify] logMockCode failed", e);
        }
    }

    // ===== Internal Helpers =====

    private boolean verifyCode(String key, String code) {
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) return false;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            long now = System.currentTimeMillis();

            Object lockUntilObj = data.get("lock_until");
            if (lockUntilObj instanceof Number n && now < n.longValue()) {
                return false;
            }

            Object expiresAtObj = data.get("expires_at");
            if (expiresAtObj instanceof Number n && now > n.longValue()) {
                redis.delete(key);
                return false;
            }

            String expectedHash = String.valueOf(data.get("code_hash"));
            String providedHash = RequestUtils.sha256Hex(salt + ":" + code);
            if (MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8), providedHash.getBytes(StandardCharsets.UTF_8))) {
                redis.delete(key);
                return true;
            }

            int errorCount = data.get("error_count") instanceof Number n ? n.intValue() : 0;
            errorCount += 1;

            Long lockUntil = null;
            if (errorCount >= 5) {
                lockUntil = now + Duration.ofMinutes(15).toMillis();
            }

            Long ttlSeconds = redis.getExpire(key);
            Duration ttl = (ttlSeconds == null || ttlSeconds < 0) ? CODE_TTL : Duration.ofSeconds(ttlSeconds);

            Map<String, Object> next = new HashMap<>();
            next.put("code_hash", expectedHash);
            next.put("expires_at", data.get("expires_at"));
            next.put("error_count", errorCount);
            if (lockUntil != null) next.put("lock_until", lockUntil);

            setJson(key, next, ttl);

            return false;
        } catch (Exception e) {
            redis.delete(key);
            return false;
        }
    }

    private void enforceSendLimit(String channel, String keyPart, int dailyLimit) {
        // 60s window
        String windowKey = "rate:" + channel + "_send:" + keyPart;
        Boolean first = redis.opsForValue().setIfAbsent(windowKey, "1", SEND_LIMIT_TTL);
        if (Boolean.FALSE.equals(first)) {
            throw new RateLimitException("请稍后再试");
        }

        // daily
        String day = LocalDate.now().toString();
        String dailyKey = "rate:" + channel + "_send_daily:" + keyPart + ":" + day;
        Long count = redis.opsForValue().increment(dailyKey);
        if (count != null && count == 1) {
            redis.expire(dailyKey, Duration.ofDays(1));
        }
        if (count != null && count > dailyLimit) {
            throw new RateLimitException("今日发送次数已达上限");
        }
    }

    private void setJson(String key, Map<String, Object> value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            throw new IllegalStateException("redis json set failed", e);
        }
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(0, 10));
        }
        return sb.toString();
    }

    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
