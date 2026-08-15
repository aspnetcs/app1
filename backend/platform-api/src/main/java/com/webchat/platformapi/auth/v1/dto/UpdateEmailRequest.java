package com.webchat.platformapi.auth.v1.dto;

public record UpdateEmailRequest(
        String phone,
        String email,
        String code,
        String purpose
) {}
