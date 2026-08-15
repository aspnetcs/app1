package com.webchat.platformapi.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoogleOAuthProviderClient implements OAuthProviderClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GoogleOAuthProviderClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerKey() {
        return "google";
    }

    @Override
    public String buildAuthorizeUrl(OAuthProperties.Provider provider, String state, String callbackUri) {
        String scope = String.join(" ", scopeParts(provider.getScope()));
        return provider.getAuthorizeUri()
                + "?client_id=" + enc(provider.getClientId())
                + "&redirect_uri=" + enc(callbackUri)
                + "&response_type=code"
                + "&scope=" + enc(scope)
                + "&access_type=online"
                + "&include_granted_scopes=true"
                + "&state=" + enc(state);
    }

    @Override
    public OAuthTokenResponse exchangeCode(OAuthProperties.Provider provider, String code, String callbackUri) throws OAuthProviderException {
        String body = "client_id=" + enc(provider.getClientId())
                + "&client_secret=" + enc(provider.getClientSecret())
                + "&code=" + enc(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + enc(callbackUri);
        JsonNode root = postForm(provider.getTokenUri(), body);
        String accessToken = text(root, "access_token");
        if (blank(accessToken)) {
            throw new OAuthProviderException("google access token missing");
        }
        return new OAuthTokenResponse(accessToken, text(root, "refresh_token"), text(root, "id_token"));
    }

    @Override
    public OAuthUserProfile fetchUserProfile(OAuthProperties.Provider provider, OAuthTokenResponse tokenResponse) throws OAuthProviderException {
        JsonNode user = getJson(provider.getUserInfoUri(), tokenResponse.accessToken());
        String providerUserId = RequestUtils.firstNonBlank(text(user, "sub"), text(user, "id"));
        if (blank(providerUserId)) {
            throw new OAuthProviderException("google subject missing");
        }
        String displayName = RequestUtils.firstNonBlank(text(user, "name"), text(user, "email"));
        String username = RequestUtils.firstNonBlank(text(user, "given_name"), text(user, "name"));
        String avatarUrl = text(user, "picture");
        String email = text(user, "email");
        boolean emailVerified = user.path("email_verified").asBoolean(false);
        return new OAuthUserProfile("sub", providerUserId, email, emailVerified, displayName, username, avatarUrl);
    }

    private JsonNode getJson(String url, String accessToken) throws OAuthProviderException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthProviderException("google api status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthProviderException("google api request failed", e);
        }
    }

    private JsonNode postForm(String url, String body) throws OAuthProviderException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthProviderException("google token status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthProviderException("google token exchange failed", e);
        }
    }

    private static List<String> scopeParts(String scope) {
        List<String> values = new ArrayList<>();
        if (scope == null || scope.isBlank()) return values;
        for (String item : scope.split("[,\\s]+")) {
            if (!item.isBlank()) values.add(item.trim());
        }
        return values;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || field == null) return null;
        String value = node.path(field).asText(null);
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
