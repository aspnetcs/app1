package com.webchat.platformapi.auth.v1.dto;

public record RegisterRequest(
        String phone,
        String email,
        String code,
        String password,
        String purpose
) {}
