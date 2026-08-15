package com.webchat.platformapi.brand;

import com.webchat.platformapi.infra.config.BrandResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrandResolverTest {

    @Test
    void resolveUsesHostMappingAndRejectsConflictingExplicitBrand() {
        BrandResolver resolver = new BrandResolver();
        resolver.setHostMap(Map.of("brand-a.example.com", "brand-a"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Host", "brand-a.example.com");

        assertEquals("brand-a", resolver.resolve(null, request));
        assertNull(resolver.resolve("brand-b", request));
    }

    @Test
    void resolveAllowsExplicitBrandOnlyWhenEnabledAndNoHostBrand() {
        BrandResolver resolver = new BrandResolver();
        resolver.setAllowExplicitRequestBrand(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("Host", "unknown.example.com");

        assertEquals("brand-b", resolver.resolve("brand-b", request));
    }

    @Test
    void resolveIgnoresForwardedHostFromUntrustedRemote() {
        BrandResolver resolver = new BrandResolver();
        resolver.setHostMap(Map.of("brand-a.example.com", "brand-a"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-Host", "brand-a.example.com");
        request.addHeader("Host", "unknown.example.com");

        assertNull(resolver.resolve(null, request));
    }
}
