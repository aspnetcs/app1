package com.webchat.platformapi.auth.v1.dto;

public record EmailLoginRequest(
        String email,
        String code,
        String purpose
) {}
