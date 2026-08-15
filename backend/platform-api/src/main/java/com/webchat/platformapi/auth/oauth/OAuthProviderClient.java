package com.webchat.platformapi.auth.oauth;

public interface OAuthProviderClient {

    String providerKey();

    String buildAuthorizeUrl(OAuthProperties.Provider provider, String state, String callbackUri);

    OAuthTokenResponse exchangeCode(OAuthProperties.Provider provider, String code, String callbackUri) throws OAuthProviderException;

    OAuthUserProfile fetchUserProfile(OAuthProperties.Provider provider, OAuthTokenResponse tokenResponse) throws OAuthProviderException;

    record OAuthTokenResponse(String accessToken, String refreshToken, String idToken) {}

    class OAuthProviderException extends Exception {
        public OAuthProviderException(String message) {
            super(message);
        }

        public OAuthProviderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
