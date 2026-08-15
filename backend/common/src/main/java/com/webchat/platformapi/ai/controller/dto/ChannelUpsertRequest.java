package com.webchat.platformapi.ai.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record ChannelUpsertRequest(
        String name,
        String type,
        @JsonProperty("base_url")
        String baseUrl,
        String models,
        @JsonProperty("test_model")
        String testModel,
        @JsonProperty("fallback_channel_id")
        Long fallbackChannelId,
        @JsonProperty("model_mapping")
        Map<String, String> modelMapping,
        @JsonProperty("extra_config")
        Map<String, Object> extraConfig,
        Integer priority,
        Integer weight,
        @JsonProperty("max_concurrent")
        Integer maxConcurrent,
        Boolean enabled,
        Integer status,
        @JsonProperty("apiKeys")
        List<String> apiKeys,
        @JsonProperty("api_key")
        String apiKey
) {
}


