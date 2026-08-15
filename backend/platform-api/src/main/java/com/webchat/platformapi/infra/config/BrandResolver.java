package com.webchat.platformapi.infra.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
@ConfigurationProperties(prefix = "brand")
public class BrandResolver {

    private Map<String, String> hostMap = new HashMap<>();
    private boolean allowExplicitRequestBrand = false;

    public Map<String, String> getHostMap() {
        return hostMap;
    }

    public void setHostMap(Map<String, String> hostMap) {
        this.hostMap = hostMap == null ? new HashMap<>() : hostMap;
    }

    public boolean isAllowExplicitRequestBrand() {
        return allowExplicitRequestBrand;
    }

    public void setAllowExplicitRequestBrand(boolean allowExplicitRequestBrand) {
        this.allowExplicitRequestBrand = allowExplicitRequestBrand;
    }

    public String resolve(String explicitBrand, HttpServletRequest request) {
        String explicit = normalizeBrand(explicitBrand);
        String hostBrand = resolveFromHost(request);
        if (hostBrand != null) {
            if (explicit == null || hostBrand.equalsIgnoreCase(explicit)) {
                return hostBrand;
            }
            return null;
        }
        if (allowExplicitRequestBrand) {
            return explicit;
        }
        return null;
    }

    public String resolveFromHost(HttpServletRequest request) {
        String host = forwardedHost(request);
        if (host == null || host.isBlank()) return null;
        String h = host.trim().toLowerCase(Locale.ROOT);
        int idx = h.indexOf(':');
        if (idx > 0) h = h.substring(0, idx);
        String mapped = hostMap.get(h);
        if (mapped != null && !mapped.isBlank()) return mapped.trim();
        return null;
    }

    private static String forwardedHost(HttpServletRequest request) {
        if (request == null) return null;
        String xfh = request.getHeader("X-Forwarded-Host");
        if (isTrustedProxy(request.getRemoteAddr()) && xfh != null && !xfh.isBlank()) return xfh.split(",")[0].trim();
        return request.getHeader("Host");
    }

    private static String normalizeBrand(String brand) {
        if (brand == null) return null;
        String normalized = brand.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return false;
        try {
            InetAddress address = InetAddress.getByName(remoteAddr);
            return address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
