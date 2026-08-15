package com.webchat.platformapi.auth.jwt;

import com.webchat.platformapi.auth.JwtService;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 统一 JWT 鉴权过滤器。
 * 解析 Authorization: Bearer token，将 userId 放入 request attribute。
 * 不强制要求登录——未登录时 attribute 为空，由 Controller 决定是否返回 401。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    public static final String ATTR_USER_ID = "authenticatedUserId";
    public static final String ATTR_SESSION_ID = "authenticatedSessionId";
    public static final String ATTR_USER_ROLE = "authenticatedUserRole";

    private final JwtService jwtService;
    private final DeviceSessionRepository deviceSessionRepository;

    public JwtAuthFilter(JwtService jwtService, DeviceSessionRepository deviceSessionRepository) {
        this.jwtService = jwtService;
        this.deviceSessionRepository = deviceSessionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            String token = authorization.trim();
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
            if (!token.isEmpty()) {
                try {
                    Map<String, Object> payload = jwtService.verifyHs256AndDecode(token);
                    Object userIdObj = payload.get("userId");
                    Object sidObj = payload.get("sid");
                    if (userIdObj != null) {
                        UUID userId = UUID.fromString(String.valueOf(userIdObj));
                        if (sidObj == null) {
                            // H3 fix: token without session ID is treated as unauthenticated
                            log.debug("[jwt] valid token but missing sid, userId={}", userId);
                        } else {
                            UUID sessionId = UUID.fromString(String.valueOf(sidObj));
                            boolean ok = deviceSessionRepository.findActiveSessionForUser(sessionId, userId, Instant.now()).isPresent();
                            if (ok) {
                                request.setAttribute(ATTR_USER_ID, userId);
                                request.setAttribute(ATTR_SESSION_ID, sessionId);
                                // Propagate role from JWT for RBAC
                                Object roleObj = payload.get("role");
                                String role = roleObj != null ? String.valueOf(roleObj).trim() : "user";
                                request.setAttribute(ATTR_USER_ROLE, role.isEmpty() ? "user" : role);
                            } else {
                                log.debug("[jwt] session invalid or expired: userId={}, sid={}", userId, sessionId);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("[jwt] token invalid or expired: {}", e.getMessage());
                }
            }
        }

        chain.doFilter(request, response);
    }
}



