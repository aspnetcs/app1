package com.webchat.platformapi.auth.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OAuthProperties properties;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        properties = new OAuthProperties();
        properties.setStartRateLimitPerMinute(10);
        properties.setConsumeTicketRateLimitPerMinute(20);
    }

    @Test
    void allowStartDeniesWhenRedisIsUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis unavailable"));

        OAuthRateLimitService service = new OAuthRateLimitService(redis, properties);

        assertThat(service.allowStart("203.0.113.8", "github")).isFalse();
    }

    @Test
    void allowConsumeTicketDeniesWhenRedisIsUnavailable() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("redis unavailable"));

        OAuthRateLimitService service = new OAuthRateLimitService(redis, properties);

        assertThat(service.allowConsumeTicket("203.0.113.8")).isFalse();
    }
}
