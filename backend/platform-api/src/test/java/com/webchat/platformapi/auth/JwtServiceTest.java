package com.webchat.platformapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new ObjectMapper(), "0123456789abcdef0123456789abcdef");
    }

    @Test
    void generateAndVerify() {
        Map<String, Object> claims = Map.of("userId", "u-123", "phone", "13800000001");
        String token = jwtService.generateHs256(claims, Duration.ofHours(1));

        assertNotNull(token);
        assertTrue(token.contains("."));
        assertEquals(3, token.split("\\.").length);

        Map<String, Object> payload = jwtService.verifyHs256AndDecode(token);
        assertEquals("u-123", payload.get("userId"));
        assertEquals("13800000001", payload.get("phone"));
        assertNotNull(payload.get("iat"));
        assertNotNull(payload.get("exp"));
    }

    @Test
    void expiredTokenThrows() {
        Map<String, Object> claims = Map.of("userId", "u-456");
        // TTL = 0 秒 → 立即过期（exp == iat）
        String token = jwtService.generateHs256(claims, Duration.ZERO);

        assertThrows(JwtService.JwtException.class, () -> jwtService.verifyHs256AndDecode(token));
    }

    @Test
    void tamperedTokenThrows() {
        String token = jwtService.generateHs256(Map.of("userId", "u-789"), Duration.ofHours(1));

        // 篡改 payload 部分
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".AAAA" + parts[1].substring(4) + "." + parts[2];

        assertThrows(JwtService.JwtException.class, () -> jwtService.verifyHs256AndDecode(tampered));
    }

    @Test
    void wrongSecretThrows() {
        String token = jwtService.generateHs256(Map.of("userId", "u-000"), Duration.ofHours(1));

        JwtService other = new JwtService(new ObjectMapper(), "abcdef0123456789abcdef0123456789");
        assertThrows(JwtService.JwtException.class, () -> other.verifyHs256AndDecode(token));
    }

    @Test
    void nullOrBlankTokenThrows() {
        assertThrows(JwtService.JwtException.class, () -> jwtService.verifyHs256AndDecode(null));
        assertThrows(JwtService.JwtException.class, () -> jwtService.verifyHs256AndDecode(""));
        assertThrows(JwtService.JwtException.class, () -> jwtService.verifyHs256AndDecode("   "));
    }

    @Test
    void emptySecretThrows() {
        assertThrows(IllegalStateException.class, () -> new JwtService(new ObjectMapper(), ""));
        assertThrows(IllegalStateException.class, () -> new JwtService(new ObjectMapper(), "too-short"));
    }
}
