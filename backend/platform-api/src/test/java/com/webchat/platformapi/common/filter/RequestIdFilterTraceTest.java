package com.webchat.platformapi.common.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RequestIdFilterTraceTest {

    @Test
    void generatesTraceIdWhenMissing_andReturnsHeaderAndAttribute() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, new MockFilterChain());

        Object attr = req.getAttribute(RequestIdFilter.TRACE_ATTR_KEY);
        assertNotNull(attr);
        String traceId = String.valueOf(attr);
        assertFalse(traceId.isBlank());
        assertTrue(traceId.length() <= 64);
        assertEquals(traceId, resp.getHeader(RequestIdFilter.TRACE_HEADER));
    }

    @Test
    void propagatesTraceIdWhenProvided_andSanitizesUnsafeChars() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        req.addHeader(RequestIdFilter.TRACE_HEADER, "  tr_abc-123.XX!!@@  ");

        filter.doFilter(req, resp, new MockFilterChain());

        String traceId = String.valueOf(req.getAttribute(RequestIdFilter.TRACE_ATTR_KEY));
        // Unsafe characters removed.
        assertEquals("tr_abc-123.XX", traceId);
        assertEquals("tr_abc-123.XX", resp.getHeader(RequestIdFilter.TRACE_HEADER));
    }
}

