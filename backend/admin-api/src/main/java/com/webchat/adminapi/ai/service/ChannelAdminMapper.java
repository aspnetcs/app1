package com.webchat.adminapi.ai.service;

import com.webchat.platformapi.ai.channel.AiChannelEntity;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ChannelAdminMapper {

    private ChannelAdminMapper() {
    }

    static boolean matchesKeyword(Map<String, Object> item, String keyword) {
        if (keyword == null) {
            return true;
        }
        String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
        return containsKeyword(item.get("id"), loweredKeyword)
                || containsKeyword(item.get("name"), loweredKeyword)
                || containsKeyword(item.get("type"), loweredKeyword)
                || containsKeyword(item.get("status"), loweredKeyword);
    }

    static Map<String, Object> toChannelDto(AiChannelEntity channel, int keyCount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", channel.getId());
        map.put("name", channel.getName());
        map.put("type", channel.getType());
        map.put("baseUrl", channel.getBaseUrl());
        map.put("models", channel.getModels() == null ? "" : channel.getModels());
        map.put("modelMapping", channel.getModelMapping() == null ? Map.of() : channel.getModelMapping());
        map.put("extraConfig", channel.getExtraConfig() == null ? Map.of() : channel.getExtraConfig());
        map.put("enabled", channel.isEnabled());
        map.put("priority", channel.getPriority());
        map.put("weight", channel.getWeight());
        map.put("maxConcurrent", channel.getMaxConcurrent());
        map.put("status", channel.getStatus());
        map.put("testModel", channel.getTestModel() == null ? "" : channel.getTestModel());
        map.put("fallbackChannelId", channel.getFallbackChannelId());
        map.put("successCount", channel.getSuccessCount());
        map.put("failCount", channel.getFailCount());
        map.put("consecutiveFailures", channel.getConsecutiveFailures());
        map.put("lastSuccessAt", channel.getLastSuccessAt() == null ? "" : channel.getLastSuccessAt().toString());
        map.put("lastFailAt", channel.getLastFailAt() == null ? "" : channel.getLastFailAt().toString());
        map.put("keyCount", Math.max(0, keyCount));
        map.put("createdAt", channel.getCreatedAt() == null ? "" : channel.getCreatedAt().toString());
        map.put("updatedAt", channel.getUpdatedAt() == null ? "" : channel.getUpdatedAt().toString());
        return map;
    }

    private static boolean containsKeyword(Object value, String keyword) {
        if (value == null) {
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(keyword);
    }
}
