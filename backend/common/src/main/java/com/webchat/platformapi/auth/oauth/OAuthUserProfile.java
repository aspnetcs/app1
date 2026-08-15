package com.webchat.platformapi.auth.oauth;

public record OAuthUserProfile(
        String identifierType,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String username,
        String avatarUrl
) {
}


