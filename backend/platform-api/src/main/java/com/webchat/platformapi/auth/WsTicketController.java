package com.webchat.platformapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.session.DeviceSessionEntity;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class WsTicketController {

    private static final Logger log = LoggerFactory.getLogger(WsTicketController.class);
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DeviceSessionRepository deviceSessionRepository;
    private final int rateLimitPerMinute;

    public WsTicketController(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            DeviceSessionRepository deviceSessionRepository,
            @Value("${ws-ticket.rate-limit-per-minute:30}") int rateLimitPerMinute
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.deviceSessionRepository = deviceSessionRepository;
        this.rateLimitPerMinute = Math.max(0, rateLimitPerMinute);
    }

    @PostMapping("/ws-ticket")
    public ApiResponse<Map<String, Object>> createWsTicket(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_SESSION_ID, required = false) UUID sessionId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!allowTicket(userId.toString())) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "请求过于频繁，请稍后再试");
        }

        String brand = null;
        if (sessionId != null) {
            DeviceSessionEntity s = deviceSessionRepository.findById(sessionId).orElse(null);
            if (s != null && s.getBrand() != null && !s.getBrand().isBlank()) {
                brand = s.getBrand();
            }
        }

        String ticket = "ws_" + UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + WS_TICKET_TTL.toMillis();

        try {
            String value = objectMapper.writeValueAsString(Map.of(
                    "user_id", userId.toString(),
                    "brand", brand == null ? "" : brand,
                    "session_id", sessionId == null ? "" : sessionId.toString(),
                    "expires_at", expiresAt
            ));
            redis.opsForValue().set("ws_ticket:" + ticket, value, WS_TICKET_TTL);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "创建 ws-ticket 失败");
        }

        return ApiResponse.ok(Map.of(
                "ticket", ticket,
                "expiresIn", (int) WS_TICKET_TTL.toSeconds()
        ));
    }

    private boolean allowTicket(String userId) {
        if (rateLimitPerMinute <= 0) return true;
        if (userId == null || userId.isBlank()) return true;
        try {
            long minute = System.currentTimeMillis() / 60000L;
            String key = "rate:ws_ticket:" + userId + ":" + minute;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(2));
            return count != null && count <= rateLimitPerMinute;
        } catch (Exception e) {
            // fail-closed: reject ticket if Redis unavailable
            log.warn("[ws-ticket] rate-limit Redis error, fail-closed: {}", e.getMessage());
            return false;
        }
    }


}
