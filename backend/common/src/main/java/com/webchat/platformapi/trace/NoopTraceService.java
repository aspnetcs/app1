package com.webchat.platformapi.trace;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(TraceService.class)
public class NoopTraceService implements TraceService {

    @Override
    public void recordSpan(UUID userId, String traceId, String requestId, String parentSpanId, String name,
                           Instant startAt, Instant endAt, String status, Map<String, Object> attrs) {
        // Intentionally no-op. Trace persistence is disabled by default.
    }
}

