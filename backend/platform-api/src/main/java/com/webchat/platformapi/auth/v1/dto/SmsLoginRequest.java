package com.webchat.platformapi.auth.v1.dto;

public record SmsLoginRequest(
        String phone,
        String code,
        String purpose
) {}
