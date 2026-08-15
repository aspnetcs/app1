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
import java.util.Comparator;
import java.util.List;

@Component
public class GithubOAuthProviderClient implements OAuthProviderClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GithubOAuthProviderClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerKey() {
        return "github";
    }

    @Override
    public String buildAuthorizeUrl(OAuthProperties.Provider provider, String state, String callbackUri) {
        String scope = String.join(" ", scopeParts(provider.getScope()));
        return provider.getAuthorizeUri()
                + "?client_id=" + enc(provider.getClientId())
                + "&redirect_uri=" + enc(callbackUri)
                + "&scope=" + enc(scope)
                + "&state=" + enc(state);
    }

    @Override
    public OAuthTokenResponse exchangeCode(OAuthProperties.Provider provider, String code, String callbackUri) throws OAuthProviderException {
        String body = "client_id=" + enc(provider.getClientId())
                + "&client_secret=" + enc(provider.getClientSecret())
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(callbackUri);
        JsonNode root = postForm(provider.getTokenUri(), body);
        String accessToken = text(root, "access_token");
        if (blank(accessToken)) {
            throw new OAuthProviderException("github access token missing");
        }
        return new OAuthTokenResponse(accessToken, text(root, "refresh_token"), text(root, "id_token"));
    }

    @Override
    public OAuthUserProfile fetchUserProfile(OAuthProperties.Provider provider, OAuthTokenResponse tokenResponse) throws OAuthProviderException {
        JsonNode user = getJson(provider.getUserInfoUri(), tokenResponse.accessToken());
        String email = text(user, "email");
        boolean verified = false;

        if (!blank(provider.getEmailUri())) {
            try {
                JsonNode emails = getJson(provider.getEmailUri(), tokenResponse.accessToken());
                if (emails.isArray()) {
                    List<JsonNode> candidates = new ArrayList<>();
                    emails.forEach(candidates::add);
                    candidates.sort(Comparator.comparing((JsonNode item) -> !item.path("primary").asBoolean(false))
                            .thenComparing(item -> !item.path("verified").asBoolean(false)));
                    for (JsonNode item : candidates) {
                        if (!item.path("verified").asBoolean(false)) {
                            continue;
                        }
                        email = text(item, "email");
                        verified = !blank(email);
                        if (verified) break;
                    }
                }
            } catch (OAuthProviderException ignored) {
                verified = false;
            }
        }

        if (blank(email)) {
            verified = false;
        }

        String providerUserId = text(user, "id");
        if (blank(providerUserId)) {
            throw new OAuthProviderException("github user id missing");
        }
        String displayName = RequestUtils.firstNonBlank(text(user, "name"), text(user, "login"));
        String username = text(user, "login");
        String avatarUrl = text(user, "avatar_url");
        return new OAuthUserProfile("id", providerUserId, email, verified, displayName, username, avatarUrl);
    }

    private JsonNode getJson(String url, String accessToken) throws OAuthProviderException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("User-Agent", "platform-api-oauth")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthProviderException("github api status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthProviderException("github api request failed", e);
        }
    }

    private JsonNode postForm(String url, String body) throws OAuthProviderException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "platform-api-oauth")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new OAuthProviderException("github token status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (OAuthProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuthProviderException("github token exchange failed", e);
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
