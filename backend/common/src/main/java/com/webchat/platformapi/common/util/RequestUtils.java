package com.webchat.platformapi.common.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared request utilities — eliminates duplication across controllers.
 */
public final class RequestUtils {
    private RequestUtils() {}

    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String remoteAddr = request.getRemoteAddr();
        String xff = request.getHeader("X-Forwarded-For");
        if (isTrustedProxy(remoteAddr) && xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            return first.isEmpty() ? remoteAddr : first;
        }
        return remoteAddr;
    }

    public static String userAgent(HttpServletRequest request) {
        if (request == null) return null;
        return request.getHeader("User-Agent");
    }

    public static String trimOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    public static String firstNonBlank(Object first, Object second) {
        String left = trimOrNull(first);
        if (left != null) return left;
        return trimOrNull(second);
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && phone.matches("^1[3-9]\\d{9}$");
    }

    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    public static String sha256Hex(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
