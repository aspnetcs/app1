package com.webchat.platformapi.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates or propagates X-Request-Id for every HTTP request.
 * Sets MDC for structured logging and returns the ID in the response header.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTR_KEY = "requestId";

    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACE_MDC_KEY = "traceId";
    public static final String TRACE_ATTR_KEY = "traceId";

    private static final int MAX_ID_LEN = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = normalizeHeaderId(request.getHeader(HEADER), 16);
        String traceId = normalizeHeaderId(request.getHeader(TRACE_HEADER), 32);

        MDC.put(MDC_KEY, requestId);
        MDC.put(TRACE_MDC_KEY, traceId);
        request.setAttribute(ATTR_KEY, requestId);
        request.setAttribute(TRACE_ATTR_KEY, traceId);
        response.setHeader(HEADER, requestId);
        response.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
            MDC.remove(TRACE_MDC_KEY);
        }
    }

    private static String normalizeHeaderId(String raw, int fallbackLen) {
        if (raw != null) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                String normalized = keepSafeIdChars(trimmed);
                if (normalized.length() > MAX_ID_LEN) {
                    normalized = normalized.substring(0, MAX_ID_LEN);
                }
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }
        String hex = UUID.randomUUID().toString().replace("-", "");
        if (fallbackLen <= 0) {
            return hex;
        }
        return hex.length() <= fallbackLen ? hex : hex.substring(0, fallbackLen);
    }

    private static String keepSafeIdChars(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Header IDs should be log-safe and URL-safe.
            if ((c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}


