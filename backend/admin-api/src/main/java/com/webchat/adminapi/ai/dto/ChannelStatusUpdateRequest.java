package com.webchat.adminapi.ai.dto;

public record ChannelStatusUpdateRequest(
        Boolean enabled,
        Integer status
) {
}
