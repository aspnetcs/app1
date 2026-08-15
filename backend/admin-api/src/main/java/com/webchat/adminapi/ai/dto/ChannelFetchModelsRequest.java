package com.webchat.adminapi.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

public record ChannelFetchModelsRequest(
        @JsonAlias("channel_id") JsonNode channelId,
        @JsonAlias("base_url") String baseUrl,
        @JsonAlias("api_key") String apiKey
) {
}
