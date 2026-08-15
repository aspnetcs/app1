package com.webchat.platformapi.auth.v1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PasswordLoginRequest(
        String phone,
        String email,
        String identifier,
        String password,
        @JsonAlias({"challenge_token", "captchaToken"}) String challengeToken
) {}
