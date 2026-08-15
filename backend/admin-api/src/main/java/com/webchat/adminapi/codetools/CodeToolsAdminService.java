package com.webchat.adminapi.codetools;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CodeToolsAdminService {

    private final JdbcTemplate jdbc;

    public CodeToolsAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> listTasks(int page, int size, String status, UUID targetUserId) {
        int resolvedSize = Math.min(Math.max(size, 1), 100);
        int resolvedPage = Math.max(page, 0);
        int offset = resolvedPage * resolvedSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ? ");
            args.add(status.trim());
        }
        if (targetUserId != null) {
            where.append(" AND t.user_id = ? ");
            args.add(targetUserId);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(1) FROM ai_code_tools_task t" + where,
                args.toArray(),
                Long.class
        );
        long resolvedTotal = total == null ? 0L : total;

        args.add(resolvedSize);
        args.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(
                """
                        SELECT
                          t.id,
                          t.user_id,
                          t.kind,
                          t.status,
                          t.input_json,
                          t.created_at,
                          t.updated_at,
                          a.status AS approval_status,
                          a.decided_by AS approval_decided_by,
                          a.decided_at AS approval_decided_at,
                          a.note AS approval_note
                        FROM ai_code_tools_task t
                        LEFT JOIN ai_code_tools_task_approval a ON a.task_id = t.id
                        """
                        + where +
                        " ORDER BY t.created_at DESC LIMIT ? OFFSET ?",
                args.toArray()
        );

        return Map.of(
                "items", items.stream().map(CodeToolsAdminService::normalizeTaskPayload).toList(),
                "total", resolvedTotal,
                "page", resolvedPage,
                "size", resolvedSize
        );
    }

    public Map<String, Object> getTask(UUID taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT
                          t.id,
                          t.user_id,
                          t.kind,
                          t.status,
                          t.input_json,
                          t.created_at,
                          t.updated_at,
                          a.status AS approval_status,
                          a.decided_by AS approval_decided_by,
                          a.decided_at AS approval_decided_at,
                          a.note AS approval_note
                        FROM ai_code_tools_task t
                        LEFT JOIN ai_code_tools_task_approval a ON a.task_id = t.id
                        WHERE t.id = ?
                        """,
                taskId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("task not found");
        }
        return normalizeTaskPayload(rows.get(0));
    }

    public List<Map<String, Object>> listLogs(UUID taskId) {
        if (taskId == null) throw new IllegalArgumentException("taskId required");
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT id, task_id, level, message, created_at
                        FROM ai_code_tools_task_log
                        WHERE task_id = ?
                        ORDER BY created_at ASC
                        """,
                taskId
        );
        return rows.stream().map(CodeToolsAdminService::normalizeLogPayload).toList();
    }

    public List<Map<String, Object>> listArtifacts(UUID taskId) {
        if (taskId == null) throw new IllegalArgumentException("taskId required");
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT id, task_id, artifact_type, name, mime, content_text, content_url, created_at
                        FROM ai_code_tools_task_artifact
                        WHERE task_id = ?
                        ORDER BY created_at DESC
                        """,
                taskId
        );
        return rows.stream().map(CodeToolsAdminService::normalizeArtifactPayload).toList();
    }

    @Transactional
    public Map<String, Object> decide(UUID adminUserId, UUID taskId, String decision, String note) {
        if (adminUserId == null) throw new IllegalArgumentException("adminUserId required");
        if (taskId == null) throw new IllegalArgumentException("taskId required");

        String normalized = decision == null ? "" : decision.trim().toLowerCase(Locale.ROOT);
        boolean approve = "approve".equals(normalized) || "approved".equals(normalized);
        boolean reject = "reject".equals(normalized) || "rejected".equals(normalized);
        if (!approve && !reject) {
            throw new IllegalArgumentException("decision must be approve or reject");
        }

        // Lock task + approval rows to avoid double decisions.
        List<Map<String, Object>> taskRows = jdbc.queryForList(
                "SELECT id, status FROM ai_code_tools_task WHERE id = ? FOR UPDATE",
                taskId
        );
        if (taskRows.isEmpty()) throw new IllegalArgumentException("task not found");
        String taskStatus = String.valueOf(taskRows.get(0).get("status"));
        if (!"pending".equalsIgnoreCase(taskStatus)) throw new IllegalStateException("task is not pending");

        List<Map<String, Object>> approvalRows = jdbc.queryForList(
                "SELECT id, status FROM ai_code_tools_task_approval WHERE task_id = ? FOR UPDATE",
                taskId
        );
        if (approvalRows.isEmpty()) throw new IllegalArgumentException("approval not found");
        String approvalStatus = String.valueOf(approvalRows.get(0).get("status"));
        if (!"pending".equalsIgnoreCase(approvalStatus)) throw new IllegalStateException("approval is not pending");

        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);
        String approvalNext = approve ? "approved" : "rejected";
        String taskNext = approve ? "approved" : "rejected";

        jdbc.update(
                """
                        UPDATE ai_code_tools_task_approval
                        SET status = ?,
                            decided_by = ?,
                            decided_at = ?,
                            note = ?,
                            updated_at = ?
                        WHERE task_id = ?
                        """,
                approvalNext,
                adminUserId,
                nowTs,
                safeNote(note),
                nowTs,
                taskId
        );

        jdbc.update(
                """
                        UPDATE ai_code_tools_task
                        SET status = ?,
                            updated_at = ?
                        WHERE id = ?
                """,
                taskNext,
                nowTs,
                taskId
        );

        jdbc.update(
                """
                        INSERT INTO ai_code_tools_task_log(task_id, level, message, created_at)
                        VALUES (?, ?, ?, ?)
                        """,
                taskId,
                "INFO",
                approve ? "approved by admin" : "rejected by admin",
                nowTs
        );

        return getTask(taskId);
    }

    private static String safeNote(String note) {
        String t = note == null ? "" : note.trim();
        if (t.length() > 500) t = t.substring(0, 500);
        return t.isEmpty() ? null : t;
    }

    /**
     * Normalize payload keys for admin clients. Keep the join columns grouped.
     */
    private static Map<String, Object> normalizeTaskPayload(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("status", row.get("approval_status"));
        approval.put("decidedBy", row.get("approval_decided_by"));
        approval.put("decidedAt", row.get("approval_decided_at"));
        approval.put("note", row.get("approval_note"));
        result.put("approval", approval);
        result.remove("approval_status");
        result.remove("approval_decided_by");
        result.remove("approval_decided_at");
        result.remove("approval_note");
        return result;
    }

    private static Map<String, Object> normalizeLogPayload(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Object taskId = row.get("task_id");
        if (taskId != null) {
            result.put("taskId", String.valueOf(taskId));
            result.remove("task_id");
        }
        Object createdAt = row.get("created_at");
        if (createdAt != null) {
            result.put("createdAt", String.valueOf(createdAt));
            result.remove("created_at");
        }
        return result;
    }

    private static Map<String, Object> normalizeArtifactPayload(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Object taskId = row.get("task_id");
        if (taskId != null) {
            result.put("taskId", String.valueOf(taskId));
            result.remove("task_id");
        }
        Object createdAt = row.get("created_at");
        if (createdAt != null) {
            result.put("createdAt", String.valueOf(createdAt));
            result.remove("created_at");
        }
        Object artifactType = row.get("artifact_type");
        if (artifactType != null) {
            result.put("artifactType", String.valueOf(artifactType));
            result.remove("artifact_type");
        }
        Object contentText = row.get("content_text");
        if (row.containsKey("content_text")) {
            result.put("contentText", contentText);
            result.remove("content_text");
        }
        Object contentUrl = row.get("content_url");
        if (row.containsKey("content_url")) {
            result.put("contentUrl", contentUrl);
            result.remove("content_url");
        }
        return result;
    }
}
