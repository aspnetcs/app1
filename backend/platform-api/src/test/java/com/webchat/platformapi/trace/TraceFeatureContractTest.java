package com.webchat.platformapi.trace;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the P0 trace foundation.
 *
 * Notes:
 * - These tests are intentionally disabled until the corresponding feature code lands.
 * - They are kept here so CI starts enforcing the contract as soon as the feature is enabled.
 */
class TraceFeatureContractTest {

    @Disabled("Enable after backend trace foundation is implemented (X-Trace-Id + admin trace endpoints).")
    @Test
    void chatCompletionsReturnsXTraceIdHeader() {
        // Intended assertions (once implemented):
        // - POST /api/v1/chat/completions returns response header X-Trace-Id
        // - value matches allowed charset/length constraints
    }

    @Disabled("Enable after SSE stream includes traceId field in event payload.")
    @Test
    void sseEventsIncludeTraceId() {
        // Intended assertions (once implemented):
        // - POST /api/v1/chat/completions/sse emits chat.delta events whose JSON payload includes traceId
        // - traceId stays constant for the lifetime of the stream
    }

    @Disabled("Enable after admin trace query endpoints are implemented and protected by admin auth.")
    @Test
    void adminTraceEndpointsRequireAdminAuth() {
        // Intended assertions (once implemented):
        // - GET /api/admin/traces returns 401/403 for non-admin requests
        // - GET /api/admin/traces/{traceId}/spans returns 401/403 for non-admin requests
    }
}

