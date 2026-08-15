package com.webchat.platformapi.auth.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class OAuthLoginTicketService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final OAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthLoginTicketService(StringRedisTemplate redis, ObjectMapper objectMapper, OAuthProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record LoginTicket(String provider, Map<String, Object> loginResponse) {
    }

    public String issue(LoginTicket ticket) throws OAuthTicketException {
        if (ticket == null || ticket.loginResponse() == null || ticket.loginResponse().isEmpty()) {
            throw new OAuthTicketException("oauth ticket payload missing");
        }
        String token = randomToken("ot_");
        try {
            redis.opsForValue().set(key(token), objectMapper.writeValueAsString(ticket),
                    Duration.ofSeconds(Math.max(30, properties.getTicketTtlSeconds())));
            return token;
        } catch (Exception e) {
            throw new OAuthTicketException("oauth ticket store failed", e);
        }
    }

    public LoginTicket consume(String ticketToken) throws OAuthTicketException {
        if (ticketToken == null || ticketToken.isBlank()) return null;
        try {
            String json = redis.opsForValue().get(key(ticketToken));
        if (json != null) redis.delete(key(ticketToken));
            if (json == null || json.isBlank()) return null;
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            String provider = stringValue(raw.get("provider"));
            Map<String, Object> loginResponse = raw.get("loginResponse") instanceof Map<?, ?> map
                    ? objectMapper.convertValue(map, new TypeReference<>() {})
                    : Map.of();
            return new LoginTicket(provider, loginResponse);
        } catch (Exception e) {
            throw new OAuthTicketException("oauth ticket read failed", e);
        }
    }

    private String key(String token) {
        return "oauth:ticket:" + token;
    }

    private String randomToken(String prefix) {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static class OAuthTicketException extends Exception {
        public OAuthTicketException(String message) {
            super(message);
        }

        public OAuthTicketException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
