package com.webchat.platformapi.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "platform.trace.enabled", havingValue = "true")
public class DbTraceService implements TraceService {

    private static final int MAX_ATTRS_JSON_LEN = 8000;

    private final TraceSpanRepository repo;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public DbTraceService(
            TraceSpanRepository repo,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:}") String serviceName
    ) {
        this.repo = repo;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName == null ? "" : serviceName.trim();
    }

    @Override
    @Transactional
    public void recordSpan(UUID userId, String traceId, String requestId, String parentSpanId, String name,
                           Instant startAt, Instant endAt, String status, Map<String, Object> attrs) {
        if (traceId == null || traceId.isBlank() || requestId == null || requestId.isBlank() || name == null || name.isBlank()) {
            return;
        }
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setTraceId(traceId);
        entity.setRequestId(requestId);
        entity.setParentSpanId(parentSpanId == null ? null : parentSpanId.trim());
        entity.setName(name.trim());
        entity.setStartAt(startAt == null ? Instant.now() : startAt);
        entity.setEndAt(endAt);
        entity.setStatus(status == null ? null : status.trim());
        entity.setUserId(userId);
        entity.setCreatedAt(Instant.now());

        Map<String, Object> safeAttrs = TraceAttrSanitizer.sanitize(attrs);
        if (!serviceName.isEmpty()) {
            safeAttrs = new java.util.LinkedHashMap<>(safeAttrs);
            safeAttrs.put("service", serviceName);
        }
        entity.setAttrsJson(toJsonSafe(safeAttrs));

        repo.save(entity);
    }

    private String toJsonSafe(Map<String, Object> attrs) {
        if (attrs == null || attrs.isEmpty()) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(attrs);
            if (json.length() > MAX_ATTRS_JSON_LEN) {
                return json.substring(0, MAX_ATTRS_JSON_LEN) + "...(truncated)";
            }
            return json;
        } catch (JsonProcessingException e) {
            return "{\"error\":\"attrs_json_encode_failed\"}";
        }
    }
}

