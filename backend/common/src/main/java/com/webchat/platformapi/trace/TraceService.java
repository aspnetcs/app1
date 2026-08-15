package com.webchat.platformapi.trace;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface TraceService {

    void recordSpan(
            UUID userId,
            String traceId,
            String requestId,
            String parentSpanId,
            String name,
            Instant startAt,
            Instant endAt,
            String status,
            Map<String, Object> attrs
    );

    static TraceService noop() {
        return (userId, traceId, requestId, parentSpanId, name, startAt, endAt, status, attrs) -> {
            // no-op
        };
    }
}
