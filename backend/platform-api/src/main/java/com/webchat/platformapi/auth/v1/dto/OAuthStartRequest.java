package com.webchat.platformapi.auth.v1.dto;

public record OAuthStartRequest(
        String redirectUri,
        DeviceInfo device
) {
    public record DeviceInfo(String deviceType, String deviceName, String brand) {
    }
}
