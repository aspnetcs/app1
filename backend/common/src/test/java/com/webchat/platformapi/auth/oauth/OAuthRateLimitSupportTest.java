package com.webchat.platformapi.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthRateLimitSupportTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowSanitizesKeyPartsAndSetsExpiryOnFirstHit() {
        Duration window = Duration.ofMinutes(1);
        String key = "rate:oauth:start:203.0.113.8:github_enterprise:admin_panel";
        when(valueOperations.increment(key)).thenReturn(1L);

        boolean allowed = OAuthRateLimitSupport.allow(
                redis,
                window,
                "start",
                2,
                OAuthRateLimitSupport.DegradationPolicy.DENY,
                " 203.0.113.8 ",
                "GitHub:Enterprise",
                "Admin:Panel"
        );

        assertThat(allowed).isTrue();
        verify(redis).expire(key, window);
    }

    @Test
    void allowRejectsRequestsPastLimitWithoutResettingExpiry() {
        Duration window = Duration.ofMinutes(1);
        String key = "rate:oauth:consume:unknown";
        when(valueOperations.increment(key)).thenReturn(3L);

        boolean allowed = OAuthRateLimitSupport.allow(
                redis,
                window,
                "consume",
                2,
                OAuthRateLimitSupport.DegradationPolicy.DENY,
                (String[]) null
        );

        assertThat(allowed).isFalse();
        verify(redis, never()).expire(key, window);
    }

    @Test
    void allowHonorsDenyDegradationPolicyWhenRedisThrows() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis unavailable"));

        boolean allowed = OAuthRateLimitSupport.allow(
                redis,
                Duration.ofMinutes(1),
                "start",
                1,
                OAuthRateLimitSupport.DegradationPolicy.DENY,
                "203.0.113.9"
        );

        assertThat(allowed).isFalse();
    }

    @Test
    void allowCanStillPermitTrafficWhenExplicitAllowPolicyIsChosen() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis unavailable"));

        boolean allowed = OAuthRateLimitSupport.allow(
                redis,
                Duration.ofMinutes(1),
                "start",
                1,
                OAuthRateLimitSupport.DegradationPolicy.ALLOW,
                "203.0.113.9"
        );

        assertThat(allowed).isTrue();
    }
}
