package com.webchat.platformapi.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.role.RolePolicyProperties;
import com.webchat.platformapi.auth.role.RolePolicyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rate limiter for AI endpoints (chat, SSE, gateway).
 * Uses Redis sliding window per user per minute + per model per minute.
 * Supports per-role rate limits and daily quota pre-check via RolePolicyProperties.
 */
public class AiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitFilter.class);
    private static final String DAILY_KEY_PREFIX = "quota:daily:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int globalMaxPerMinute;
    private final int globalModelMaxPerMinute;
    private final RolePolicyProperties rolePolicyProperties;
    private final RolePolicyService rolePolicyService;

    public AiRateLimitFilter(StringRedisTemplate redis, ObjectMapper objectMapper,
                             int maxPerMinute, int modelMaxPerMinute,
                             RolePolicyProperties rolePolicyProperties,
                             RolePolicyService rolePolicyService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.globalMaxPerMinute = Math.max(1, maxPerMinute);
        this.globalModelMaxPerMinute = Math.max(0, modelMaxPerMinute);
        this.rolePolicyProperties = rolePolicyProperties;
        this.rolePolicyService = rolePolicyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        Object userIdAttr = request.getAttribute(JwtAuthFilter.ATTR_USER_ID);
        if (userIdAttr == null) {
            chain.doFilter(request, response);
            return;
        }

        UUID userId = (UUID) userIdAttr;
        int effectiveMaxPerMinute = globalMaxPerMinute;
        int effectiveModelMax = globalModelMaxPerMinute;

        Object roleAttr = request.getAttribute(JwtAuthFilter.ATTR_USER_ROLE);
        String role = roleAttr != null ? String.valueOf(roleAttr).trim() : "user";

        if (rolePolicyService != null) {
            effectiveMaxPerMinute = rolePolicyService.resolveRateLimit(userId, role);
            effectiveModelMax = rolePolicyService.resolveModelRateLimit(role);

            int dailyQuota = rolePolicyService.resolveDailyQuota(role);
            if (dailyQuota > 0) {
                long used = getDailyUsageCount(userId);
                if (used >= dailyQuota) {
                    log.info("[rate-limit] daily quota exhausted: userId={}, role={}, used={}, limit={}",
                            userId, role, used, dailyQuota);
                    writeRateLimitResponse(response, "daily chat quota exceeded");
                    return;
                }
            }
        } else if (rolePolicyProperties != null) {
            RolePolicyProperties.RoleConfig config = rolePolicyProperties.getPolicy(role);
            effectiveMaxPerMinute = config.getRateLimitPerMinute();
            effectiveModelMax = config.getModelRateLimitPerMinute();

            int dailyQuota = config.getDailyChatQuota();
            if (dailyQuota > 0) {
                long used = getDailyUsageCount(userId);
                if (used >= dailyQuota) {
                    log.info("[rate-limit] daily quota exhausted: userId={}, role={}, used={}, limit={}",
                            userId, role, used, dailyQuota);
                    writeRateLimitResponse(response, "daily chat quota exceeded");
                    return;
                }
            }
        }

        if (!allowUser(userId, effectiveMaxPerMinute)) {
            log.info("[rate-limit] user blocked: userId={}, role={}, path={}", userId, role, request.getRequestURI());
            writeRateLimitResponse(response, "request rate limit exceeded");
            return;
        }

        if (effectiveModelMax > 0) {
            for (String model : extractModels(request)) {
                if (!allowModel(userId, model, effectiveModelMax)) {
                    log.info("[rate-limit] model blocked: userId={}, model={}, role={}, path={}",
                            userId, model, role, request.getRequestURI());
                    writeRateLimitResponse(response, "model request rate limit exceeded");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean allowUser(UUID userId, int limit) {
        return checkLimit("rate:ai:" + userId, limit);
    }

    private boolean allowModel(UUID userId, String model, int limit) {
        return checkLimit("rate:ai:model:" + userId + ":" + model, limit);
    }

    private boolean checkLimit(String keyPrefix, int limit) {
        if (limit <= 0) {
            return true;
        }
        try {
            long minute = System.currentTimeMillis() / 60_000L;
            String key = keyPrefix + ":" + minute;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            return count != null && count <= limit;
        } catch (Exception e) {
            log.warn("[rate-limit] Redis error, fail-closed: {}", e.getMessage());
            return false;
        }
    }

    private long getDailyUsageCount(UUID userId) {
        try {
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            String key = DAILY_KEY_PREFIX + userId + ":" + today;
            String value = redis.opsForValue().get(key);
            return value != null ? Long.parseLong(value) : 0;
        } catch (Exception e) {
            log.warn("[rate-limit] daily quota read failed, fail-open: userId={}, error={}", userId, e.getMessage());
            return 0;
        }
    }

    private List<String> extractModels(HttpServletRequest request) {
        LinkedHashSet<String> models = new LinkedHashSet<>();
        try {
            if (request instanceof ContentCachingRequestWrapper wrapper) {
                byte[] body = wrapper.getContentAsByteArray();
                if (body.length > 0) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(body, Map.class);
                    addModelCandidate(models, map.get("model"));
                    addModelCandidates(models, map.get("models"));
                    addModelCandidates(models, map.get("modelIds"));
                }
            }
        } catch (Exception e) {
            return List.of();
        }
        addModelCandidate(models, request.getAttribute("ai.request.model"));
        addModelCandidates(models, request.getAttribute("ai.request.models"));
        return List.copyOf(models);
    }

    private static void addModelCandidates(LinkedHashSet<String> models, Object raw) {
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addModelCandidate(models, item);
            }
            return;
        }
        if (raw instanceof Object[] array) {
            for (Object item : array) {
                addModelCandidate(models, item);
            }
            return;
        }
        addModelCandidate(models, raw);
    }

    private static void addModelCandidate(LinkedHashSet<String> models, Object raw) {
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            models.add(value);
        }
    }

    private void writeRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of(
                "error", Map.of(
                        "message", message,
                        "type", "rate_limit_exceeded",
                        "code", "rate_limit_exceeded"
                )
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
