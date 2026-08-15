package com.webchat.platformapi.infra.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/job")
public class JobV1Controller {

    private static final Logger log = LoggerFactory.getLogger(JobV1Controller.class);
    private static final java.util.Set<String> ALLOWED_TYPES = java.util.Set.of("document", "media");

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JobV1Controller(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/enqueue")
    public ApiResponse<Map<String, Object>> enqueue(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        String type = str(body, "type");
        Object inputObj = body == null ? null : body.get("input");
        if (type == null || type.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数缺失");
        type = type.trim().toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_TYPES.contains(type)) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "不支持的任务类型");
        }

        UUID jobId = UUID.randomUUID();
        String inputJson = "{}";
        try {
            if (inputObj != null) inputJson = objectMapper.writeValueAsString(inputObj);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "input 不是合法 JSON");
        }

        try {
            jdbc.update(
                    "INSERT INTO job (id, type, status, user_id, input, output, progress, created_at, updated_at) " +
                            "VALUES (?, ?, 'queued', ?, ?::jsonb, '{}'::jsonb, 0, NOW(), NOW())",
                    jobId, type, userId, inputJson
            );
        } catch (Exception e) {
            log.warn("[job] enqueue failed for user {} type {}: {}", userId, type, e.getMessage());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "job creation failed");
        }

        return ApiResponse.ok(Map.of(
                "jobId", jobId.toString(),
                "type", type,
                "status", "queued",
                "createdAt", Instant.now().toString()
        ));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable String jobId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");

        UUID id;
        try {
            id = UUID.fromString(jobId);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "jobId 非法");
        }

        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, type, status, progress, error, output::text AS output_json, created_at, updated_at FROM job WHERE id=? AND user_id=?",
                    id, userId
            );
            Object outputObj = parseJson(String.valueOf(row.get("output_json")));
            return ApiResponse.ok(Map.of(
                    "jobId", String.valueOf(row.get("id")),
                    "type", String.valueOf(row.get("type")),
                    "status", String.valueOf(row.get("status")),
                    "progress", row.get("progress"),
                    "error", row.get("error") == null ? "" : String.valueOf(row.get("error")),
                    "output", outputObj,
                    "createdAt", String.valueOf(row.get("created_at")),
                    "updatedAt", String.valueOf(row.get("updated_at"))
            ));
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.error(ErrorCodes.USER_NOT_FOUND, "job 不存在");
        } catch (Exception e) {
            log.warn("[job] get failed for user {} job {}: {}", userId, id, e.getMessage());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "job 查询失败");
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank() || "null".equalsIgnoreCase(json.trim())) return Map.of();
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
