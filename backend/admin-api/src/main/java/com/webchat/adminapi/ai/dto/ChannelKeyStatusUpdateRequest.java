package com.webchat.adminapi.ai.dto;

public record ChannelKeyStatusUpdateRequest(
        Boolean enabled,
        Integer status,
        Integer weight
) {
}
