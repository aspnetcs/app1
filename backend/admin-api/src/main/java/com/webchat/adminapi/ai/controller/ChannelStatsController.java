package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Channel health stats for admin dashboard (Feature #6).
 */
@RestController
@RequestMapping("/api/v1/admin/ai/channels")
public class ChannelStatsController {

    private final AiChannelRepository channelRepo;

    public ChannelStatsController(AiChannelRepository channelRepo) {
        this.channelRepo = channelRepo;
    }

    @GetMapping("/stats")
    public ApiResponse<List<Map<String, Object>>> stats(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");

        List<AiChannelEntity> channels = channelRepo.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiChannelEntity ch : channels) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ch.getId());
            item.put("name", ch.getName());
            item.put("type", ch.getType());
            item.put("enabled", ch.isEnabled());
            item.put("status", ch.getStatus());
            item.put("successCount", ch.getSuccessCount());
            item.put("failCount", ch.getFailCount());
            long total = ch.getSuccessCount() + ch.getFailCount();
            double successRate = total > 0 ? (double) ch.getSuccessCount() / total * 100 : 0;
            item.put("successRate", Math.round(successRate * 100.0) / 100.0);
            item.put("consecutiveFailures", ch.getConsecutiveFailures());
            item.put("lastSuccessAt", ch.getLastSuccessAt());
            item.put("lastFailAt", ch.getLastFailAt());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }
}



