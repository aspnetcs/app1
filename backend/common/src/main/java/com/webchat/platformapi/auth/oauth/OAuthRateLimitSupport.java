package com.webchat.platformapi.auth.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Locale;

public final class OAuthRateLimitSupport {

    private static final Logger log = LoggerFactory.getLogger(OAuthRateLimitSupport.class);

    private OAuthRateLimitSupport() {
    }

    public enum DegradationPolicy {
        ALLOW,
        DENY;

        boolean allowsRequest() {
            return this == ALLOW;
        }
    }

    public static boolean allow(
            StringRedisTemplate redis,
            Duration windowTtl,
            String action,
            int limitPerMinute,
            DegradationPolicy degradationPolicy,
            String... keyParts
    ) {
        if (limitPerMinute <= 0) {
            return true;
        }
        StringBuilder key = new StringBuilder("rate:oauth:")
                .append(action)
                .append(':');
        boolean appended = false;
        if (keyParts != null) {
            for (String part : keyParts) {
                if (part == null) {
                    continue;
                }
                if (appended) {
                    key.append(':');
                }
                key.append(safe(part));
                appended = true;
            }
        }
        if (!appended) {
            key.append("unknown");
        }
        try {
            Long count = redis.opsForValue().increment(key.toString());
            if (count != null && count == 1L) {
                redis.expire(key.toString(), windowTtl);
            }
            return count == null || count <= limitPerMinute;
        } catch (Exception e) {
            DegradationPolicy policy = degradationPolicy == null ? DegradationPolicy.DENY : degradationPolicy;
            log.warn("[oauth-rate-limit] degraded={} action={} key={} cause={}",
                    policy.name().toLowerCase(Locale.ROOT),
                    action,
                    key,
                    e.toString());
            return policy.allowsRequest();
        }
    }

    private static String safe(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace(':', '_');
    }
}
