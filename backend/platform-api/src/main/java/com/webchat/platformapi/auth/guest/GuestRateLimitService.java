package com.webchat.platformapi.auth.guest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class GuestRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(GuestRateLimitService.class);
    private static final Duration WINDOW_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final GuestAuthProperties properties;

    public GuestRateLimitService(StringRedisTemplate redis, GuestAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean allowIssue(String clientIp) {
        int limit = properties.getIssueRateLimitPerHour();
        if (limit <= 0) {
            return true;
        }
        String key = "rate:guest:issue:" + normalizeIp(clientIp);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW_TTL);
            }
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("[guest-auth] rate limit check failed, allow guest issue by default: key={}", key, e);
            return true;
        }
    }

    private static String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim().toLowerCase(Locale.ROOT).replace(':', '_');
    }
}
