package com.webchat.platformapi.history;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class HistorySearchService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public HistorySearchService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> searchTopics(UUID userId, String keyword, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String likePattern = toLikePattern(normalizedKeyword);

        long total = queryForLong(
                """
                select count(*)
                from ai_conversation c
                where c.user_id = ?
                  and c.deleted_at is null
                  and c.is_temporary = false
                  and lower(coalesce(c.title, '')) like ?
                """,
                userId,
                likePattern
        );

        List<Map<String, Object>> items = jdbcTemplate.query(
                """
                select c.id,
                       c.title,
                       c.model,
                       c.updated_at,
                       (
                           select count(*)
                           from ai_message m
                           where m.conversation_id = c.id
                             and m.parent_message_id is null
                       ) as message_count
                from ai_conversation c
                where c.user_id = ?
                  and c.deleted_at is null
                  and c.is_temporary = false
                  and lower(coalesce(c.title, '')) like ?
                order by c.updated_at desc
                limit ? offset ?
                """,
                ps -> {
                    ps.setObject(1, userId);
                    ps.setString(2, likePattern);
                    ps.setInt(3, safeSize);
                    ps.setInt(4, safePage * safeSize);
                },
                (rs, rowNum) -> mapTopicRow(rs, normalizedKeyword)
        );

        return toPage(items, total, safePage, safeSize);
    }

    public Map<String, Object> searchMessages(UUID userId, String keyword, UUID topicId, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String likePattern = toLikePattern(normalizedKeyword);

        ArrayList<Object> countArgs = new ArrayList<>();
        String countSql = buildUserMessageSearchSql(true, userId, topicId, countArgs);
        countArgs.add(likePattern);
        long total = queryForLong(countSql, countArgs.toArray());

        ArrayList<Object> listArgs = new ArrayList<>();
        String listSql = buildUserMessageSearchSql(false, userId, topicId, listArgs);
        listArgs.add(likePattern);
        listArgs.add(safeSize);
        listArgs.add(safePage * safeSize);

        List<Map<String, Object>> items = jdbcTemplate.query(
                listSql,
                ps -> applyArgs(ps, listArgs),
                (rs, rowNum) -> mapMessageRow(rs, normalizedKeyword)
        );

        return toPage(items, total, safePage, safeSize);
    }

    public Map<String, Object> searchFiles(UUID userId, String keyword, UUID topicId, int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String likePattern = toLikePattern(normalizedKeyword);

        ArrayList<Object> countArgs = new ArrayList<>();
        String countSql = buildUserFileSearchSql(true, userId, topicId, countArgs);
        addFileSearchArgs(countArgs, likePattern);
        long total = queryForLong(countSql, countArgs.toArray());

        ArrayList<Object> listArgs = new ArrayList<>();
        String listSql = buildUserFileSearchSql(false, userId, topicId, listArgs);
        addFileSearchArgs(listArgs, likePattern);
        listArgs.add(safeSize);
        listArgs.add(safePage * safeSize);

        List<FileSearchRow> rows = jdbcTemplate.query(
                listSql,
                ps -> applyArgs(ps, listArgs),
                this::mapFileSearchRow
        );
        if (rows.isEmpty()) {
            return toPage(List.of(), total, safePage, safeSize);
        }

        Map<UUID, List<FileBlockRow>> blocksByMessageId = loadBlocksByMessageId(rows.stream().map(FileSearchRow::messageId).toList());
        ArrayList<Map<String, Object>> items = new ArrayList<>();
        for (FileSearchRow row : rows) {
            List<FileBlockRow> blocks = blocksByMessageId.getOrDefault(row.messageId(), List.of());
            String searchableText = buildFileSearchText(row.mediaUrl(), blocks);
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", row.conversationId().toString());
            item.put("conversationTitle", row.conversationTitle());
            item.put("messageId", row.messageId().toString());
            item.put("anchorMessageId", row.messageId().toString());
            item.put("fileLabel", resolveFileLabel(row.mediaUrl(), blocks));
            item.put("snippet", buildSnippet(searchableText, normalizedKeyword));
            item.put("createdAt", toIso(row.createdAt()));
            items.add(item);
        }

        return toPage(items, total, safePage, safeSize);
    }

    private static String buildUserMessageSearchSql(boolean countOnly, UUID userId, UUID topicId, List<Object> args) {
        String select = countOnly
                ? "select count(*)"
                : """
                select m.id,
                       m.conversation_id,
                       c.title as conversation_title,
                       m.role,
                       m.content,
                       m.created_at
                """;
        StringBuilder sql = new StringBuilder(select).append(
                """
                from ai_message m
                join ai_conversation c on c.id = m.conversation_id
                where c.user_id = ?
                  and c.deleted_at is null
                  and c.is_temporary = false
                  and m.parent_message_id is null
                """
        );
        args.add(userId);
        if (topicId != null) {
            sql.append(" and c.id = ?");
            args.add(topicId);
        }
        sql.append(" and lower(coalesce(m.content, '')) like ?");
        if (!countOnly) {
            sql.append(" order by m.created_at desc limit ? offset ?");
        }
        return sql.toString();
    }

    private static String buildUserFileSearchSql(boolean countOnly, UUID userId, UUID topicId, List<Object> args) {
        String select = countOnly
                ? "select count(*)"
                : """
                select m.id,
                       m.conversation_id,
                       c.title as conversation_title,
                       m.media_url,
                       m.created_at
                """;
        StringBuilder sql = new StringBuilder(select).append(
                """
                from ai_message m
                join ai_conversation c on c.id = m.conversation_id
                where c.user_id = ?
                  and c.deleted_at is null
                  and c.is_temporary = false
                  and m.parent_message_id is null
                """
        );
                args.add(userId);
                if (topicId != null) {
                        sql.append(" and c.id = ?");
                        args.add(topicId);
                }
                sql.append(
                                """
                                 and (
                                             lower(coalesce(m.media_url, '')) like ?
                                        or exists (
                                                select 1
                                                from ai_message_block b
                                                where b.message_id = m.id
                                                    and (
                                                             lower(coalesce(b.block_key, '')) like ?
                                                        or lower(coalesce(b.block_type, '')) like ?
                                                        or lower(coalesce(b.payload_json, '')) like ?
                                                    )
                                        )
                                 )
                                """
                );
                if (!countOnly) {
                        sql.append(" order by m.created_at desc limit ? offset ?");
                }
                return sql.toString();
    }

    private static void addFileSearchArgs(List<Object> args, String likePattern) {
        args.add(likePattern);
        args.add(likePattern);
        args.add(likePattern);
        args.add(likePattern);
    }

    private Map<String, Object> mapTopicRow(ResultSet rs, String keyword) throws SQLException {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        UUID id = rs.getObject("id", UUID.class);
        item.put("id", id == null ? "" : id.toString());
        item.put("conversationId", id == null ? "" : id.toString());
        item.put("title", safeString(rs.getString("title"), "未命名对话"));
        item.put("model", safeString(rs.getString("model"), ""));
        item.put("snippet", buildSnippet(rs.getString("title"), keyword));
        item.put("updatedAt", toIso(rs.getTimestamp("updated_at")));
        item.put("messageCount", rs.getLong("message_count"));
        return item;
    }

    private Map<String, Object> mapMessageRow(ResultSet rs, String keyword) throws SQLException {
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        UUID conversationId = rs.getObject("conversation_id", UUID.class);
        UUID messageId = rs.getObject("id", UUID.class);
        item.put("conversationId", conversationId == null ? "" : conversationId.toString());
        item.put("conversationTitle", safeString(rs.getString("conversation_title"), "未命名对话"));
        item.put("messageId", messageId == null ? "" : messageId.toString());
        item.put("anchorMessageId", messageId == null ? "" : messageId.toString());
        item.put("role", safeString(rs.getString("role"), "user"));
        item.put("snippet", buildSnippet(rs.getString("content"), keyword));
        item.put("createdAt", toIso(rs.getTimestamp("created_at")));
        return item;
    }

    private FileSearchRow mapFileSearchRow(ResultSet rs, int rowNum) throws SQLException {
        return new FileSearchRow(
                rs.getObject("id", UUID.class),
                rs.getObject("conversation_id", UUID.class),
                safeString(rs.getString("conversation_title"), "未命名对话"),
                rs.getString("media_url"),
                rs.getTimestamp("created_at")
        );
    }

    private Map<UUID, List<FileBlockRow>> loadBlocksByMessageId(Collection<UUID> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(messageIds));
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = """
                select message_id, block_type, block_key, payload_json
                from ai_message_block
                where message_id in (%s)
                order by message_id asc, sequence_no asc, created_at asc
                """.formatted(placeholders);

        List<FileBlockRow> rows = jdbcTemplate.query(
                sql,
                ps -> {
                    for (int i = 0; i < ids.size(); i++) {
                        ps.setObject(i + 1, ids.get(i));
                    }
                },
                (rs, rowNum) -> new FileBlockRow(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("block_type"),
                        rs.getString("block_key"),
                        rs.getString("payload_json")
                )
        );

        LinkedHashMap<UUID, List<FileBlockRow>> map = new LinkedHashMap<>();
        for (FileBlockRow row : rows) {
            if (row.messageId() == null) {
                continue;
            }
            map.computeIfAbsent(row.messageId(), ignored -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private long queryForLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static void applyArgs(java.sql.PreparedStatement statement, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            statement.setObject(i + 1, args.get(i));
        }
    }

    private static String safeString(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static int normalizePage(int page) {
        return Math.max(0, page);
    }

    private static int normalizeSize(int size) {
        return Math.max(1, Math.min(MAX_SIZE, size <= 0 ? DEFAULT_SIZE : size));
    }

    private static String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private static String toLikePattern(String keyword) {
        return "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private static String buildSnippet(String raw, String keyword) {
        String text = normalizeSearchText(raw);
        if (text.isEmpty()) {
            return "";
        }
        if (keyword == null || keyword.isBlank()) {
            return shorten(text, 96);
        }

        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int hit = lowerText.indexOf(lowerKeyword);
        if (hit < 0) {
            return shorten(text, 96);
        }

        int start = Math.max(0, hit - 24);
        int end = Math.min(text.length(), hit + lowerKeyword.length() + 48);
        String prefix = start > 0 ? "..." : "";
        String suffix = end < text.length() ? "..." : "";
        return prefix + text.substring(start, end).trim() + suffix;
    }

    private static String shorten(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    private static String normalizeSearchText(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", " ").trim();
    }

    private String buildFileSearchText(String mediaUrl, List<FileBlockRow> blocks) {
        ArrayList<String> parts = new ArrayList<>();
        String normalizedMediaUrl = normalizeSearchText(mediaUrl);
        if (!normalizedMediaUrl.isEmpty()) {
            parts.add(normalizedMediaUrl);
        }
        for (FileBlockRow block : blocks) {
            String key = normalizeSearchText(block.blockKey());
            if (!key.isEmpty()) {
                parts.add(key);
            }
            String type = normalizeSearchText(block.blockType());
            if (!type.isEmpty()) {
                parts.add(type);
            }
            parts.addAll(extractPayloadStrings(block.payloadJson()));
        }
        return String.join(" ", parts);
    }

    private String resolveFileLabel(String mediaUrl, List<FileBlockRow> blocks) {
        for (FileBlockRow block : blocks) {
            String key = normalizeSearchText(block.blockKey());
            if (!key.isEmpty()) {
                return key;
            }
            for (String candidate : extractPayloadStrings(block.payloadJson())) {
                String normalized = normalizeSearchText(candidate);
                if (!normalized.isEmpty() && normalized.length() <= 96) {
                    return normalized;
                }
            }
        }

        String mediaName = extractFileName(mediaUrl);
        if (!mediaName.isEmpty()) {
            return mediaName;
        }
        return "附件命中";
    }

    private List<String> extractPayloadStrings(String payloadJson) {
        String normalized = normalizeSearchText(payloadJson);
        if (normalized.isEmpty()) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(payloadJson, Object.class);
            ArrayList<String> values = new ArrayList<>();
            collectPayloadStrings(parsed, values);
            return values;
        } catch (Exception ignored) {
            return List.of(normalized);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectPayloadStrings(Object value, List<String> out) {
        if (value == null) {
            return;
        }
        if (value instanceof String text) {
            String normalized = normalizeSearchText(text);
            if (!normalized.isEmpty()) {
                out.add(normalized);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) {
                collectPayloadStrings(child, out);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object child : iterable) {
                collectPayloadStrings(child, out);
            }
            return;
        }
        if (value.getClass().isArray()) {
            for (Object child : (Object[]) value) {
                collectPayloadStrings(child, out);
            }
        }
    }

    private static String extractFileName(String rawUrl) {
        String normalized = normalizeSearchText(rawUrl);
        if (normalized.isEmpty()) {
            return "";
        }
        int queryIndex = normalized.indexOf('?');
        String withoutQuery = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
        int slashIndex = withoutQuery.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex + 1 >= withoutQuery.length()) {
            return withoutQuery;
        }
        return withoutQuery.substring(slashIndex + 1);
    }

    private static Map<String, Object> toPage(List<Map<String, Object>> items, long total, int page, int size) {
        return Map.of(
                "items", items,
                "total", total,
                "page", page,
                "size", size
        );
    }

    private static String toIso(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toInstant().toString();
    }

    private record FileSearchRow(
            UUID messageId,
            UUID conversationId,
            String conversationTitle,
            String mediaUrl,
            Timestamp createdAt
    ) {
    }

    private record FileBlockRow(
            UUID messageId,
            String blockType,
            String blockKey,
            String payloadJson
    ) {
    }
}