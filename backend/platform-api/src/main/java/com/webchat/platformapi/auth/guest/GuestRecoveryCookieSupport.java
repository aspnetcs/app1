package com.webchat.platformapi.auth.guest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

final class GuestRecoveryCookieSupport {

    static final String COOKIE_NAME = "guest_recovery";
    private static final String COOKIE_PATH = "/";
    private static final String COOKIE_SAME_SITE = "Lax";

    private GuestRecoveryCookieSupport() {
    }

    static String resolveRecoveryToken(HttpServletRequest request, String headerName) {
        if (request == null) {
            return null;
        }
        String headerToken = trimToNull(request.getHeader(headerName));
        if (headerToken != null) {
            return headerToken;
        }
        Cookie cookie = WebUtils.getCookie(request, COOKIE_NAME);
        return cookie == null ? null : trimToNull(cookie.getValue());
    }

    static void writeRecoveryCookie(
            HttpServletRequest request,
            HttpServletResponse response,
            String recoveryToken,
            Duration ttl
    ) {
        if (response == null) {
            return;
        }
        String normalizedToken = trimToNull(recoveryToken);
        if (normalizedToken == null) {
            clearRecoveryCookie(response, request);
            return;
        }
        Duration safeTtl = ttl == null || ttl.isNegative() ? Duration.ZERO : ttl;
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, normalizedToken)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite(COOKIE_SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(safeTtl)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static void clearRecoveryCookie(HttpServletResponse response, HttpServletRequest request) {
        if (response == null) {
            return;
        }
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite(COOKIE_SAME_SITE)
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = trimToNull(request.getHeader("X-Forwarded-Proto"));
        return forwardedProto != null && "https".equalsIgnoreCase(forwardedProto);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
