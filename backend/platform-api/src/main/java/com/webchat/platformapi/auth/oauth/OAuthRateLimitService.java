package com.webchat.platformapi.auth.oauth;

import com.webchat.platformapi.auth.oauth.OAuthRateLimitSupport;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class OAuthRateLimitService {

    private static final Duration WINDOW_TTL = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;
    private final OAuthProperties properties;

    public OAuthRateLimitService(StringRedisTemplate redis, OAuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public boolean allowStart(String clientIp, String provider) {
        return OAuthRateLimitSupport.allow(
                redis,
                WINDOW_TTL,
                "start",
                properties.getStartRateLimitPerMinute(),
                OAuthRateLimitSupport.DegradationPolicy.DENY,
                clientIp,
                provider
        );
    }

    public boolean allowConsumeTicket(String clientIp) {
        return OAuthRateLimitSupport.allow(
                redis,
                WINDOW_TTL,
                "consume",
                properties.getConsumeTicketRateLimitPerMinute(),
                OAuthRateLimitSupport.DegradationPolicy.DENY,
                clientIp
        );
    }
}
