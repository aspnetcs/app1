package com.webchat.platformapi.ai.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChannelKeysRequest(
        @JsonProperty("apiKeys")
        List<String> apiKeys,
        @JsonProperty("api_key")
        String apiKey
) {
}



