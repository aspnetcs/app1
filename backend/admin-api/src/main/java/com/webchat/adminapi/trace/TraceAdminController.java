package com.webchat.adminapi.trace;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.trace.TraceSpanEntity;
import com.webchat.platformapi.trace.TraceSpanRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/traces")
public class TraceAdminController {

    private final TraceSpanRepository repo;

    public TraceAdminController(TraceSpanRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "traceId", required = false) String traceId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "unauthorized");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "forbidden");

        String normalizedTraceId = normalize(traceId);
        String normalizedRequestId = normalize(requestId);
        if (normalizedTraceId.isEmpty() && normalizedRequestId.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "requestId or traceId is required");
        }

        List<Map<String, Object>> items = new ArrayList<>();
        if (!normalizedTraceId.isEmpty()) {
            items.add(Map.of("traceId", normalizedTraceId));
            return ApiResponse.ok(Map.of("items", items));
        }

        // Bound blast radius: query most recent spans and infer distinct traceIds in memory.
        List<TraceSpanEntity> spans = repo.findByRequestIdOrderByStartAtDesc(normalizedRequestId, PageRequest.of(0, 500));
        Map<String, TraceSummary> summaries = new LinkedHashMap<>();
        for (TraceSpanEntity span : spans) {
            String tid = span == null ? null : span.getTraceId();
            if (tid == null || tid.isBlank()) {
                continue;
            }
            TraceSummary s = summaries.computeIfAbsent(tid, k -> new TraceSummary(tid));
            s.observe(span);
        }

        List<TraceSummary> ordered = new ArrayList<>(summaries.values());
        ordered.sort(Comparator.comparing(TraceSummary::lastSeenAt).reversed());
        for (TraceSummary s : ordered) {
            items.add(s.toMap());
        }
        return ApiResponse.ok(Map.of("items", items));
    }

    @GetMapping("/{traceId}/spans")
    public ApiResponse<Map<String, Object>> spans(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("traceId") String traceId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "unauthorized");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "forbidden");

        String normalizedTraceId = normalize(traceId);
        if (normalizedTraceId.isEmpty()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "traceId is required");
        }

        List<TraceSpanEntity> spans = repo.findByTraceIdOrderByStartAtAsc(normalizedTraceId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (TraceSpanEntity s : spans) {
            if (s == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("traceId", s.getTraceId());
            item.put("requestId", s.getRequestId());
            item.put("parentSpanId", s.getParentSpanId());
            item.put("name", s.getName());
            item.put("startAt", s.getStartAt());
            item.put("endAt", s.getEndAt());
            item.put("status", s.getStatus());
            item.put("userId", s.getUserId());
            item.put("attrsJson", s.getAttrsJson());
            item.put("createdAt", s.getCreatedAt());
            items.add(item);
        }

        return ApiResponse.ok(Map.of(
                "traceId", normalizedTraceId,
                "items", items
        ));
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.isEmpty() ? "" : t;
    }

    private static final class TraceSummary {
        private final String traceId;
        private Instant firstAt;
        private Instant lastAt;
        private String status;

        private TraceSummary(String traceId) {
            this.traceId = traceId;
        }

        void observe(TraceSpanEntity span) {
            if (span.getStartAt() != null) {
                if (firstAt == null || span.getStartAt().isBefore(firstAt)) {
                    firstAt = span.getStartAt();
                }
                if (lastAt == null || span.getStartAt().isAfter(lastAt)) {
                    lastAt = span.getStartAt();
                }
            }
            if (span.getStatus() != null && !span.getStatus().isBlank()) {
                status = span.getStatus();
            }
        }

        Instant lastSeenAt() {
            return lastAt == null ? Instant.EPOCH : lastAt;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("traceId", traceId);
            if (firstAt != null) m.put("firstAt", firstAt);
            if (lastAt != null) m.put("lastAt", lastAt);
            if (status != null) m.put("status", status);
            return m;
        }
    }
}
