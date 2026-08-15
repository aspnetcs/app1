package com.webchat.platformapi.auth.v1.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

public record TokenRefreshRequest(
        @JsonAlias("refresh_token") String refreshToken
) {}
