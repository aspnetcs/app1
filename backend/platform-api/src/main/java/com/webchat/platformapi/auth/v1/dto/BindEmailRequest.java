package com.webchat.platformapi.auth.v1.dto;

public record BindEmailRequest(
        String phone,
        String email,
        String code,
        String purpose
) {}

