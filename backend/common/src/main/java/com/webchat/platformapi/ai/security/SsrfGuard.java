package com.webchat.platformapi.ai.security;

import com.webchat.platformapi.ai.AiProperties;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

@Service
public class SsrfGuard {

    public static class SsrfException extends RuntimeException {
        public SsrfException(String message) {
            super(message);
        }
    }

    private final AiProperties properties;

    public SsrfGuard(AiProperties properties) {
        this.properties = properties;
    }

    public void assertAllowedBaseUrl(String baseUrl) {
        String raw = baseUrl == null ? "" : baseUrl.trim();
        if (raw.isEmpty()) throw new SsrfException("base_url 不能为空");

        URI uri;
        try {
            uri = URI.create(raw);
        } catch (Exception e) {
            throw new SsrfException("base_url 非法");
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost();
        if (host == null || host.isBlank()) throw new SsrfException("base_url 缺少 host");
        host = host.trim().toLowerCase();

        boolean localhost = isLocalhost(host);

        boolean allowHttpLocalhost = properties != null && properties.getSsrf() != null && properties.getSsrf().isAllowHttpLocalhost();
        boolean allowHttp = properties != null && properties.getSsrf() != null && properties.getSsrf().isAllowHttp();
        if ("http".equals(scheme)) {
            if (!(localhost && allowHttpLocalhost) && !allowHttp) {
                throw new SsrfException("仅允许 https，上游 http 需显式开启");
            }
        } else if (!"https".equals(scheme)) {
            throw new SsrfException("仅允许 https/http");
        }

        if (!localhost || !allowHttpLocalhost) {
            List<String> allowlist = properties == null || properties.getSsrf() == null ? List.of() : properties.getSsrf().getAllowlist();
            if (allowlist == null || allowlist.isEmpty()) {
                throw new SsrfException("未配置上游 allowlist");
            }
            boolean allowed = false;
            for (String entry : allowlist) {
                if (entry == null) continue;
                String e = entry.trim().toLowerCase();
                if (e.isEmpty()) continue;
                if ("*".equals(e)) { allowed = true; break; }
                if (host.equals(e)) {
                    allowed = true;
                    break;
                }
                if (e.startsWith("*.")) {
                    String suffix = e.substring(1); // ".example.com"
                    if (host.endsWith(suffix) && host.length() > suffix.length()) {
                        allowed = true;
                        break;
                    }
                } else if (e.startsWith(".")) {
                    if (host.endsWith(e) && host.length() > e.length()) {
                        allowed = true;
                        break;
                    }
                }
            }
            if (!allowed) throw new SsrfException("上游域名不在 allowlist");
        }

        boolean allowPrivate = properties != null && properties.getSsrf() != null && properties.getSsrf().isAllowPrivate();
        if (localhost && allowHttpLocalhost) return;

        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new SsrfException("上游域名解析失败");
        }
        if (addrs == null || addrs.length == 0) throw new SsrfException("上游域名解析失败");

        for (InetAddress a : addrs) {
            if (a == null) continue;
            if (isPrivateAddress(a) && !allowPrivate) {
                throw new SsrfException("禁止访问内网地址（可配置 allow-private 放开）");
            }
        }
    }

    private static boolean isLocalhost(String host) {
        return "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "host.docker.internal".equals(host);
    }

    private static boolean isPrivateAddress(InetAddress a) {
        if (a.isAnyLocalAddress()) return true;
        if (a.isLoopbackAddress()) return true;
        if (a.isLinkLocalAddress()) return true;
        if (a.isSiteLocalAddress()) return true;
        if (a.isMulticastAddress()) return true;

        byte[] b = a.getAddress();
        if (b == null) return true;
        if (b.length == 16) {
            // IPv6 unique local: fc00::/7
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return true;
            // IPv6 link-local: fe80::/10
            if (first == 0xfe) {
                int second = b[1] & 0xff;
                if ((second & 0xc0) == 0x80) return true;
            }
        }

        return false;
    }
}

