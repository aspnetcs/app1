package com.webchat.platformapi.auth.guest;

public record GuestLoginRequest(String deviceId, String recoveryToken, String deviceFingerprint) {}
