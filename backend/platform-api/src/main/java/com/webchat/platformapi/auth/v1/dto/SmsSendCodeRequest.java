package com.webchat.platformapi.auth.v1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record SmsSendCodeRequest(
        String phone,
        String purpose,
        @JsonAlias({"challenge_token", "captchaToken"}) String challengeToken
) {}
