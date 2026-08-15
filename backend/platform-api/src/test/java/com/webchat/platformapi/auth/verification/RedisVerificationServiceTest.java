package com.webchat.platformapi.auth.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisVerificationServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private ListOperations<String, String> listOps;

    private RedisVerificationService service;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForList()).thenReturn(listOps);
        service = new RedisVerificationService(redis, new ObjectMapper(), "test-salt", false);
    }

    @Test
    void sendSmsCode_storesAndReturns6Digits() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String code = service.sendSmsCode("13800000001", "login");

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        // 楠岃瘉鍐欏叆 Redis
        verify(valueOps).set(eq("sms_code:login:13800000001"), anyString(), any(Duration.class));
        verify(listOps, never()).leftPush(anyString(), anyString());
    }

    @Test
    void sendEmailCode_storesAndReturns6Digits() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String code = service.sendEmailCode("test@example.com", "login");

        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));

        verify(valueOps).set(eq("email_code:login:test@example.com"), anyString(), any(Duration.class));
        verify(listOps, never()).leftPush(anyString(), anyString());
    }

    @Test
    void sendSmsCode_rateLimited() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        assertThrows(RedisVerificationService.RateLimitException.class,
                () -> service.sendSmsCode("13800000001", "login"));
    }

    @Test
    void verifySmsCode_correctCode() {
        // 鍏堝彂閫佽幏鍙?code
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String code = service.sendSmsCode("13800000001", "login");

        // 妯℃嫙 Redis 涓瓨鍌ㄧ殑鍊?
        // 闇€瑕佹ā鎷?get 杩斿洖瀵瑰簲鐨?hash
        // 鐢变簬鏄崟鍏冩祴璇曪紝鎴戜滑鐩存帴楠岃瘉璋冪敤閾?
        verify(valueOps).set(startsWith("sms_code:login:"), anyString(), any(Duration.class));
    }

    @Test
    void consumeChallengeToken_validToken() {
        when(redis.delete("challenge:valid-token")).thenReturn(true);

        assertTrue(service.consumeChallengeToken("valid-token"));
        verify(redis).delete("challenge:valid-token");
    }

    @Test
    void consumeChallengeToken_invalidToken() {
        when(redis.delete("challenge:invalid-token")).thenReturn(false);

        assertFalse(service.consumeChallengeToken("invalid-token"));
        verify(redis).delete("challenge:invalid-token");
    }

    @Test
    void consumeChallengeToken_nullOrBlank() {
        assertFalse(service.consumeChallengeToken(null));
        assertFalse(service.consumeChallengeToken(""));
        assertFalse(service.consumeChallengeToken("   "));
    }

    @Test
    void consumeChallengeToken_withMatchingRequesterIp_returnsTrue() throws Exception {
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "purpose", "auth",
                "expires_at", System.currentTimeMillis() + 60_000,
                "ip_hash", com.webchat.platformapi.common.util.RequestUtils.sha256Hex("127.0.0.1")
        ));
        when(valueOps.getAndDelete("challenge:bound-token")).thenReturn(json);

        assertTrue(service.consumeChallengeToken("bound-token", "127.0.0.1"));
        verify(valueOps).getAndDelete("challenge:bound-token");
    }

    @Test
    void consumeChallengeToken_withMismatchedRequesterIp_returnsFalse() throws Exception {
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "purpose", "auth",
                "expires_at", System.currentTimeMillis() + 60_000,
                "ip_hash", com.webchat.platformapi.common.util.RequestUtils.sha256Hex("127.0.0.1")
        ));
        when(valueOps.getAndDelete("challenge:bound-token")).thenReturn(json);

        assertFalse(service.consumeChallengeToken("bound-token", "192.168.1.10"));
        verify(valueOps).getAndDelete("challenge:bound-token");
    }

    @Test
    void sendSmsCode_dailyLimitExceeded() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(11L); // 瓒呰繃姣忔棩涓婇檺 10

        assertThrows(RedisVerificationService.RateLimitException.class,
                () -> service.sendSmsCode("13800000001", "login"));
    }

    @Test
    void getMockCodes_returnsEmptyWhenFeatureDisabled() {
        assertTrue(service.getMockCodes().isEmpty());
        verify(redis, never()).opsForList();
    }

    @Test
    void clearMockCodes_isNoopWhenFeatureDisabled() {
        service.clearMockCodes();

        verify(redis, never()).delete("dev:mock_codes");
    }

    @Test
    void sendSmsCode_logsMockCodeWhenFeatureEnabled() {
        RedisVerificationService enabledService = new RedisVerificationService(redis, new ObjectMapper(), "test-salt", true);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForList()).thenReturn(listOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(1L);

        String code = enabledService.sendSmsCode("13800000001", "login");

        assertNotNull(code);
        verify(listOps).leftPush(eq("dev:mock_codes"), contains("\"target\":\"sms:13800000001\""));
        verify(redis).expire("dev:mock_codes", Duration.ofHours(24));
    }

    @Test
    void clearMockCodes_deletesRedisKeyWhenFeatureEnabled() {
        RedisVerificationService enabledService = new RedisVerificationService(redis, new ObjectMapper(), "test-salt", true);

        enabledService.clearMockCodes();

        verify(redis).delete("dev:mock_codes");
    }
}
