package com.webchat.adminapi.agent.runtime;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.AiChannelStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentRunAdminService {

    private final JdbcTemplate jdbc;
    private final AiChannelRepository channelRepo;

    public AgentRunAdminService(JdbcTemplate jdbc, AiChannelRepository channelRepo) {
        this.jdbc = jdbc;
        this.channelRepo = channelRepo;
    }

    public Map<String, Object> listRuns(int page, int size, String status, UUID userId) {
        int resolvedSize = Math.min(Math.max(size, 1), 100);
        int resolvedPage = Math.max(page, 0);
        int offset = resolvedPage * resolvedSize;

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        var args = new java.util.ArrayList<Object>();
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status = ? ");
            args.add(status.trim());
        }
        if (userId != null) {
            where.append(" AND r.user_id = ? ");
            args.add(userId);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(1) FROM ai_agent_run r" + where,
                args.toArray(),
                Long.class
        );
        long resolvedTotal = total == null ? 0L : total;

        args.add(resolvedSize);
        args.add(offset);

        List<Map<String, Object>> items = jdbc.queryForList(
                """
                        SELECT
                          r.id,
                          r.user_id,
                          r.agent_id,
                          r.requested_channel_id,
                          r.bound_channel_id,
                          r.status,
                          r.error_message,
                          r.created_at,
                          r.updated_at,
                          r.started_at,
                          r.completed_at,
                          a.status AS approval_status,
                          a.decided_by AS approval_decided_by,
                          a.decided_at AS approval_decided_at,
                          a.note AS approval_note
                        FROM ai_agent_run r
                        LEFT JOIN ai_agent_run_approval a ON a.run_id = r.id
                        """
                        + where +
                        " ORDER BY r.created_at DESC LIMIT ? OFFSET ?",
                args.toArray()
        );

        return Map.of(
                "items", items.stream().map(AgentRunAdminService::normalizeRunPayload).toList(),
                "total", resolvedTotal,
                "page", resolvedPage,
                "size", resolvedSize
        );
    }

    public Map<String, Object> getRun(UUID runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT
                          r.id,
                          r.user_id,
                          r.agent_id,
                          r.requested_channel_id,
                          r.bound_channel_id,
                          r.status,
                          r.error_message,
                          r.created_at,
                          r.updated_at,
                          r.started_at,
                          r.completed_at,
                          a.status AS approval_status,
                          a.decided_by AS approval_decided_by,
                          a.decided_at AS approval_decided_at,
                          a.note AS approval_note
                        FROM ai_agent_run r
                        LEFT JOIN ai_agent_run_approval a ON a.run_id = r.id
                        WHERE r.id = ?
                        """,
                runId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("run not found");
        }
        return normalizeRunPayload(rows.get(0));
    }

    @Transactional
    public Map<String, Object> decide(UUID adminUserId, UUID runId, String decision, Long boundChannelId, String note) {
        if (adminUserId == null) throw new IllegalArgumentException("adminUserId required");
        if (runId == null) throw new IllegalArgumentException("runId required");

        String normalized = decision == null ? "" : decision.trim().toLowerCase(java.util.Locale.ROOT);
        boolean approve = "approve".equals(normalized) || "approved".equals(normalized);
        boolean reject = "reject".equals(normalized) || "rejected".equals(normalized);
        if (!approve && !reject) {
            throw new IllegalArgumentException("decision must be approve or reject");
        }

        // Lock run + approval rows to avoid double decisions.
        List<Map<String, Object>> runRows = jdbc.queryForList(
                "SELECT id, status, requested_channel_id FROM ai_agent_run WHERE id = ? FOR UPDATE",
                runId
        );
        if (runRows.isEmpty()) {
            throw new IllegalArgumentException("run not found");
        }
        String runStatus = String.valueOf(runRows.get(0).get("status"));
        if (!"pending".equalsIgnoreCase(runStatus)) {
            throw new IllegalStateException("run is not pending");
        }

        List<Map<String, Object>> approvalRows = jdbc.queryForList(
                "SELECT id, status FROM ai_agent_run_approval WHERE run_id = ? FOR UPDATE",
                runId
        );
        if (approvalRows.isEmpty()) {
            throw new IllegalArgumentException("approval not found");
        }
        String approvalStatus = String.valueOf(approvalRows.get(0).get("status"));
        if (!"pending".equalsIgnoreCase(approvalStatus)) {
            throw new IllegalStateException("approval is not pending");
        }

        Instant now = Instant.now();
        String approvalNext = approve ? "approved" : "rejected";
        String runNext = approve ? "approved" : "rejected";

        Long requestedChannelId = null;
        Object requested = runRows.get(0).get("requested_channel_id");
        if (requested instanceof Number n) {
            requestedChannelId = n.longValue();
        }
        Long resolvedBoundChannelId = null;
        if (approve) {
            resolvedBoundChannelId = resolveBoundChannel(boundChannelId, requestedChannelId);
        }

        jdbc.update(
                """
                        UPDATE ai_agent_run_approval
                        SET status = ?,
                            decided_by = ?,
                            decided_at = ?,
                            note = ?,
                            updated_at = ?
                        WHERE run_id = ?
                        """,
                approvalNext,
                adminUserId,
                now,
                safeNote(note),
                now,
                runId
        );

        jdbc.update(
                """
                        UPDATE ai_agent_run
                        SET status = ?,
                            bound_channel_id = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                runNext,
                resolvedBoundChannelId,
                now,
                runId
        );

        return getRun(runId);
    }

    private Long resolveBoundChannel(Long explicitBoundChannelId, Long requestedChannelId) {
        Long candidate = explicitBoundChannelId != null ? explicitBoundChannelId : requestedChannelId;
        if (candidate == null) {
            List<AiChannelEntity> channels = channelRepo.findByEnabledTrueAndStatus(AiChannelStatus.NORMAL);
            if (channels == null || channels.isEmpty()) {
                return null;
            }
            candidate = channels.get(0).getId();
        }

        AiChannelEntity selected = channelRepo.findById(candidate).orElseThrow(() -> new IllegalArgumentException("channel not found"));
        if (!selected.isEnabled() || selected.getStatus() == AiChannelStatus.DISABLED_MANUAL) {
            throw new IllegalArgumentException("channel unavailable");
        }
        return selected.getId();
    }

    private static String safeNote(String note) {
        String t = note == null ? "" : note.trim();
        if (t.length() > 500) t = t.substring(0, 500);
        return t.isEmpty() ? null : t;
    }

    /**
     * Normalize payload keys for admin clients. Keep the join columns grouped.
     */
    private static Map<String, Object> normalizeRunPayload(Map<String, Object> row) {
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
}
