package com.webchat.adminapi.auth.admin;

import java.util.Map;

public record OAuthAdminConfigUpdateRequest(
        Boolean enabled,
        Boolean allowAdminLogin,
        Map<String, ProviderConfig> providers
) {
    public record ProviderConfig(
            Boolean enabled,
            Boolean allowAdminLogin
    ) {
    }
}


