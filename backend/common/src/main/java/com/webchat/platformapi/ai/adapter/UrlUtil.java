package com.webchat.platformapi.ai.adapter;

public final class UrlUtil {
    private UrlUtil() {}

    public static String join(String baseUrl, String path) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        String p = path == null ? "" : path.trim();
        if (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (!p.startsWith("/")) p = "/" + p;
        return b + p;
    }
}
