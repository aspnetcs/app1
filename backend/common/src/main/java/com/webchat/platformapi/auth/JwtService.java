package com.webchat.platformapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public JwtService(ObjectMapper objectMapper, @Value("${jwt.secret}") String secret) {
        this.objectMapper = objectMapper;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret is empty");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret is too short (need >= " + MIN_SECRET_BYTES + " bytes)");
        }
        this.secret = secretBytes;
    }

    public Map<String, Object> verifyHs256AndDecode(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("empty token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("invalid token format");
        }

        // header
        String headerJson = new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
            Object alg = header.get("alg");
            if (alg != null && !"HS256".equals(String.valueOf(alg))) {
                throw new JwtException("unsupported alg: " + alg);
            }
        } catch (JwtException e) {
            throw e;
        } catch (Exception e) {
            throw new JwtException("invalid header json");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = base64UrlEncode(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secret));
        if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new JwtException("invalid signature");
        }

        String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        Map<String, Object> payload;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payloadJson, Map.class);
            payload = map;
        } catch (Exception e) {
            throw new JwtException("invalid payload json");
        }

        Object expObj = payload.get("exp");
        if (expObj == null) {
            throw new JwtException("token missing exp claim");
        }
        if (expObj instanceof Number n) {
            long exp = n.longValue();
            long now = Instant.now().getEpochSecond();
            if (now >= exp) {
                throw new JwtException("token expired");
            }
        } else {
            throw new JwtException("invalid exp claim type");
        }

        return payload;
    }

    public String generateHs256(Map<String, Object> claims, Duration ttl) {
        // secret is validated on startup, check kept as a hard fail-safe
        if (secret.length < MIN_SECRET_BYTES) throw new IllegalStateException("jwt.secret is invalid");

        long now = Instant.now().getEpochSecond();
        long exp = now + (ttl == null ? 0 : ttl.toSeconds());

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
        );

        Map<String, Object> payload = new HashMap<>();
        if (claims != null) payload.putAll(claims);
        payload.put("iat", now);
        payload.put("exp", exp);

        try {
            String headerJson = objectMapper.writeValueAsString(header);
            String payloadJson = objectMapper.writeValueAsString(payload);

            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = base64UrlEncode(hmacSha256(signingInput.getBytes(StandardCharsets.UTF_8), secret));
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("jwt serialize failed", e);
        }
    }

    private static byte[] hmacSha256(byte[] data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("hmac init failed", e);
        }
    }

    private static byte[] base64UrlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static class JwtException extends RuntimeException {
        public JwtException(String message) {
            super(message);
        }
    }
}


