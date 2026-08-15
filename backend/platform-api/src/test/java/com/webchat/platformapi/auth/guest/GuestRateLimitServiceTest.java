package com.webchat.platformapi.auth.guest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private GuestAuthProperties properties;
    private GuestRateLimitService guestRateLimitService;

    @BeforeEach
    void setUp() {
        properties = new GuestAuthProperties();
        properties.setIssueRateLimitPerHour(2);
        when(redis.opsForValue()).thenReturn(valueOperations);
        guestRateLimitService = new GuestRateLimitService(redis, properties);
    }

    @Test
    void allowIssueAllowsWithinLimitAndSetsTtlOnFirstHit() {
        when(valueOperations.increment("rate:guest:issue:203.0.113.8")).thenReturn(1L);

        boolean allowed = guestRateLimitService.allowIssue("203.0.113.8");

        assertTrue(allowed);
        verify(redis).expire("rate:guest:issue:203.0.113.8", java.time.Duration.ofHours(1));
    }

    @Test
    void allowIssueRejectsWhenRedisCounterExceedsLimit() {
        when(valueOperations.increment("rate:guest:issue:203.0.113.9")).thenReturn(3L);

        boolean allowed = guestRateLimitService.allowIssue("203.0.113.9");

        assertFalse(allowed);
        verify(redis, never()).expire("rate:guest:issue:203.0.113.9", java.time.Duration.ofHours(1));
    }

    @Test
    void allowIssueFailsClosedWhenRedisUnavailable() {
        when(valueOperations.increment("rate:guest:issue:unknown")).thenThrow(new RuntimeException("redis down"));

        boolean allowed = guestRateLimitService.allowIssue(null);

        assertFalse(allowed);
    }
}
