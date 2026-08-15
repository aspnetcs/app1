package com.webchat.platformapi.auth.v1.dto;

public record BindEmailSendCodeRequest(
        String phone,
        String email,
        String purpose
) {}
