package com.webchat.platformapi.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestUtilsTest {

    @Test
    void clientIpReturnsFirstForwardedAddressForTrustedLoopbackProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.8, 203.0.113.9");

        assertEquals("198.51.100.8", RequestUtils.clientIp(request));
    }

    @Test
    void clientIpIgnoresForwardedHeaderForUntrustedProxy() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.8, 203.0.113.9");

        assertEquals("10.0.0.8", RequestUtils.clientIp(request));
    }

    @Test
    void clientIpFallsBackToRemoteAddrWhenForwardedHeaderIsBlank() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "   ");

        assertEquals("127.0.0.1", RequestUtils.clientIp(request));
    }

    @Test
    void clientIpReturnsNullWhenRequestIsNull() {
        assertNull(RequestUtils.clientIp(null));
    }
}
