package com.webchat.adminapi.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/messages")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
public class MessageBlockAdminController {

    private static final String QUERY = """
            select id, message_id, conversation_id, role, block_type, block_key, sequence_no, status, payload_json, created_at, updated_at
            from ai_message_block
            where message_id = ?
            order by sequence_no asc, created_at asc
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MessageBlockAdminController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/{messageId}/blocks")
    public ApiResponse<List<Map<String, Object>>> blocks(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("messageId") String messageIdText
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }

        UUID messageId;
        try {
            messageId = UUID.fromString(messageIdText);
        } catch (IllegalArgumentException ignored) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "messageId is invalid");
        }

        List<Map<String, Object>> rows = jdbcTemplate.query(QUERY, ps -> ps.setObject(1, messageId), (rs, rowNum) -> {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("id", rs.getObject("id"));
            map.put("message_id", rs.getObject("message_id"));
            map.put("conversation_id", rs.getObject("conversation_id"));
            map.put("role", rs.getString("role"));
            map.put("type", rs.getString("block_type"));
            map.put("key", rs.getString("block_key"));
            map.put("sequence", rs.getInt("sequence_no"));
            map.put("status", rs.getString("status"));
            map.put("payload", parsePayload(rs.getString("payload_json")));
            map.put("created_at", toIso(rs.getTimestamp("created_at")));
            map.put("updated_at", toIso(rs.getTimestamp("updated_at")));
            return map;
        });
        return ApiResponse.ok(rows == null ? List.of() : rows);
    }

    private Object parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() { });
        } catch (Exception ignored) {
            return Map.of("raw", payloadJson);
        }
    }

    private static String toIso(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toInstant().toString();
    }
}
