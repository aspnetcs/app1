package com.webchat.adminapi.ai.service;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.AiChannelStatus;
import com.webchat.platformapi.common.api.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChannelUsageAdminService {

    private final AiChannelRepository channelRepo;
    private final AiChannelKeyRepository keyRepo;
    private final JdbcTemplate jdbc;

    public ChannelUsageAdminService(
            AiChannelRepository channelRepo,
            AiChannelKeyRepository keyRepo,
            JdbcTemplate jdbc
    ) {
        this.channelRepo = channelRepo;
        this.keyRepo = keyRepo;
        this.jdbc = jdbc;
    }

    public ApiResponse<Map<String, Object>> usage(Integer hours) {
        int safeHours = hours == null ? 24 : Math.max(1, Math.min(24 * 30, hours));
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(safeHours));

        List<AiChannelEntity> channels;
        try {
            channels = channelRepo.findAll();
        } catch (Exception e) {
            channels = List.of();
        }
        channels.sort(Comparator.comparing(AiChannelEntity::getId, Comparator.nullsLast(Long::compareTo)));

        List<AiChannelKeyEntity> keys;
        try {
            keys = keyRepo.findAll();
        } catch (Exception e) {
            keys = List.of();
        }

        Map<Long, List<AiChannelKeyEntity>> keysByChannel = new HashMap<>();
        for (AiChannelKeyEntity key : keys) {
            if (key == null) {
                continue;
            }
            Long channelId = key.getChannelId();
            if (channelId == null) {
                continue;
            }
            keysByChannel.computeIfAbsent(channelId, ignored -> new ArrayList<>()).add(key);
        }

        Map<Long, UsageStats> windowStats = loadWindowStats(from);
        long totalReq = 0;
        long totalDone = 0;
        long totalErr = 0;

        List<Map<String, Object>> out = new ArrayList<>();
        for (AiChannelEntity channel : channels) {
            if (channel == null || channel.getId() == null) {
                continue;
            }
            Long channelId = channel.getId();
            List<AiChannelKeyEntity> channelKeys = keysByChannel.getOrDefault(channelId, List.of());
            int keyCount = channelKeys.size();

            int enabledKeys = 0;
            int disabledKeys = 0;
            for (AiChannelKeyEntity key : channelKeys) {
                if (key == null) {
                    continue;
                }
                if (key.isEnabled() && key.getStatus() == AiChannelStatus.NORMAL) {
                    enabledKeys += 1;
                } else {
                    disabledKeys += 1;
                }
            }

            Map<String, Object> dto = ChannelAdminMapper.toChannelDto(channel, keyCount);
            dto.put("enabledKeys", enabledKeys);
            dto.put("disabledKeys", disabledKeys);

            UsageStats stats = windowStats.get(channelId);
            if (stats != null) {
                dto.put("windowTotal", stats.total());
                dto.put("windowDone", stats.done());
                dto.put("windowError", stats.error());
                totalReq += stats.total();
                totalDone += stats.done();
                totalErr += stats.error();
            } else {
                dto.put("windowTotal", 0);
                dto.put("windowDone", 0);
                dto.put("windowError", 0);
            }
            out.add(dto);
        }

        return ApiResponse.ok(Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "hours", safeHours,
                "total", totalReq,
                "done", totalDone,
                "error", totalErr,
                "channels", out
        ));
    }

    private Map<Long, UsageStats> loadWindowStats(Instant from) {
        if (jdbc == null || from == null) {
            return Map.of();
        }

        String sql = """
                SELECT (NULLIF(detail->>'channelId', ''))::bigint AS channel_id,
                       COUNT(*) AS total,
                       SUM(CASE WHEN detail->>'status' = 'done' THEN 1 ELSE 0 END) AS done,
                       SUM(CASE WHEN detail->>'status' = 'done' THEN 0 ELSE 1 END) AS error
                FROM audit_log
                WHERE action = 'ai.chat.stream'
                  AND created_at >= ?
                  AND NULLIF(detail->>'channelId', '') IS NOT NULL
                GROUP BY (NULLIF(detail->>'channelId', ''))::bigint
                """;

        Map<Long, UsageStats> out = new HashMap<>();
        try {
            jdbc.query(sql, rs -> {
                long id = rs.getLong("channel_id");
                if (rs.wasNull()) {
                    return;
                }
                long total = rs.getLong("total");
                long done = rs.getLong("done");
                long error = rs.getLong("error");
                out.put(id, new UsageStats(total, done, error));
            }, java.sql.Timestamp.from(from));
        } catch (Exception e) {
            return Map.of();
        }
        return out;
    }

    private record UsageStats(long total, long done, long error) {
    }
}
