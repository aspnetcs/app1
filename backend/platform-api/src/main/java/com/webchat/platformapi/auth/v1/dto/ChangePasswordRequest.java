package com.webchat.platformapi.auth.v1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ChangePasswordRequest(
        String code,
        @JsonAlias("new_password") String newPassword,
        String purpose,
        @JsonAlias("verify_type") String verifyType
) {}
