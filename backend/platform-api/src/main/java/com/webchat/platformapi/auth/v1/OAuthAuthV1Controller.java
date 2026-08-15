package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.auth.oauth.OAuthLoginService;
import com.webchat.platformapi.auth.oauth.OAuthLoginStateService;
import com.webchat.platformapi.auth.oauth.OAuthLoginTicketService;
import com.webchat.platformapi.auth.oauth.OAuthProperties;
import com.webchat.platformapi.auth.oauth.OAuthProviderClient;
import com.webchat.platformapi.auth.oauth.OAuthRateLimitService;
import com.webchat.platformapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.platformapi.auth.oauth.OAuthUserProfile;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.auth.v1.dto.OAuthConsumeTicketRequest;
import com.webchat.platformapi.auth.v1.dto.OAuthStartRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthAuthV1Controller {

    private static final Logger log = LoggerFactory.getLogger(OAuthAuthV1Controller.class);

    private final OAuthRuntimeConfigService runtimeConfigService;
    private final Map<String, OAuthProviderClient> providerClients;
    private final OAuthLoginStateService stateService;
    private final OAuthLoginTicketService ticketService;
    private final OAuthLoginService loginService;
    private final OAuthRateLimitService rateLimitService;
    private final AuditService auditService;
    private final GuestHistoryLoginSupport guestHistoryLoginSupport;

    public OAuthAuthV1Controller(
            OAuthRuntimeConfigService runtimeConfigService,
            List<OAuthProviderClient> providerClients,
            OAuthLoginStateService stateService,
            OAuthLoginTicketService ticketService,
            OAuthLoginService loginService,
            OAuthRateLimitService rateLimitService,
            AuditService auditService,
            GuestHistoryLoginSupport guestHistoryLoginSupport
    ) {
        this.runtimeConfigService = runtimeConfigService;
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(OAuthProviderClient::providerKey, client -> client));
        this.stateService = stateService;
        this.ticketService = ticketService;
        this.loginService = loginService;
        this.rateLimitService = rateLimitService;
        this.auditService = auditService;
        this.guestHistoryLoginSupport = guestHistoryLoginSupport;
    }

    @GetMapping("/providers")
    public ApiResponse<Map<String, Object>> providers() {
        OAuthRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.currentConfig();
        List<Map<String, Object>> items = runtimeConfig.providers().entrySet().stream()
                .filter(entry -> runtimeConfig.isProviderEnabled(entry.getKey()))
                .filter(entry -> !blank(entry.getValue().clientId()) && !blank(entry.getValue().clientSecret()))
                .filter(entry -> providerClients.containsKey(entry.getKey()))
                .map(entry -> {
                    OAuthRuntimeConfigService.ProviderConfig provider = entry.getValue();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("provider", entry.getKey());
                    item.put("displayName", RequestUtils.firstNonBlank(provider.displayName(), capitalize(entry.getKey())));
                    item.put("icon", RequestUtils.firstNonBlank(provider.icon(), entry.getKey()));
                    return item;
                })
                .toList();
        return ApiResponse.ok(Map.of(
                "enabled", runtimeConfig.enabled(),
                "providers", items
        ));
    }

    @PostMapping("/{provider}/start")
    public ApiResponse<Map<String, Object>> start(
            @PathVariable("provider") String rawProvider,
            @RequestBody(required = false) OAuthStartRequest requestBody,
            HttpServletRequest request
    ) {
        String provider = normalizeProvider(rawProvider);
        String clientIp = RequestUtils.clientIp(request);
        if (!rateLimitService.allowStart(clientIp, provider)) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "oauth start rate limited");
        }
        OAuthRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.currentConfig();
        if (!runtimeConfig.isProviderEnabled(provider)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "oauth login disabled");
        }

        OAuthRuntimeConfigService.ProviderConfig providerConfig = runtimeConfig.getProvider(provider);
        OAuthProviderClient providerClient = providerClients.get(provider);
        if (providerConfig == null || providerClient == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "oauth provider not supported");
        }
        if (blank(providerConfig.clientId()) || blank(providerConfig.clientSecret())) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "oauth provider not configured");
        }

        String redirectUri = requestBody == null ? null : RequestUtils.trimOrNull(requestBody.redirectUri());
        if (!isAllowedRedirectUri(redirectUri, request)) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "redirectUri not allowed");
        }

        String callbackUri = externalBaseUrl(request) + "/api/v1/auth/oauth/" + provider + "/callback";
        OAuthLoginStateService.LoginState state = new OAuthLoginStateService.LoginState(
                provider,
                redirectUri,
                callbackUri,
                requestBody == null || requestBody.device() == null ? null : RequestUtils.trimOrNull(requestBody.device().deviceType()),
                requestBody == null || requestBody.device() == null ? null : RequestUtils.trimOrNull(requestBody.device().deviceName()),
                requestBody == null || requestBody.device() == null ? null : RequestUtils.trimOrNull(requestBody.device().brand())
        );
        try {
            String stateToken = stateService.issue(state);
            String authorizeUrl = providerClient.buildAuthorizeUrl(toPropertiesProvider(providerConfig), stateToken, callbackUri);
            return ApiResponse.ok(Map.of(
                "provider", provider,
                "authorizeUrl", authorizeUrl
            ));
        } catch (OAuthLoginStateService.OAuthStateException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "oauth start failed");
        }
    }

    @GetMapping("/{provider}/callback")
    public void callback(
            @PathVariable("provider") String rawProvider,
            @RequestParam(name = "state", required = false) String stateToken,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String provider = normalizeProvider(rawProvider);
        OAuthLoginStateService.LoginState state;
        try {
            state = stateService.consume(stateToken);
        } catch (OAuthLoginStateService.OAuthStateException e) {
            writePlainError(response, HttpStatus.BAD_REQUEST, "oauth state invalid");
            return;
        }
        if (state == null || !provider.equals(state.provider())) {
            writePlainError(response, HttpStatus.BAD_REQUEST, "oauth state expired");
            return;
        }

        OAuthRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.currentConfig();
        if (!runtimeConfig.isProviderEnabled(provider)) {
            redirectWithError(response, state.redirectUri(), "disabled", "oauth login disabled");
            return;
        }

        if (!blank(error)) {
            redirectWithError(response, state.redirectUri(), error, RequestUtils.firstNonBlank(errorDescription, error));
            return;
        }
        if (blank(code)) {
            redirectWithError(response, state.redirectUri(), "missing_code", "oauth code missing");
            return;
        }

        OAuthProviderClient providerClient = providerClients.get(provider);
        OAuthRuntimeConfigService.ProviderConfig providerConfig = runtimeConfig.getProvider(provider);
        if (providerClient == null || providerConfig == null) {
            redirectWithError(response, state.redirectUri(), "unsupported", "oauth provider not supported");
            return;
        }

        try {
            OAuthProperties.Provider oauthProvider = toPropertiesProvider(providerConfig);
            OAuthProviderClient.OAuthTokenResponse tokenResponse = providerClient.exchangeCode(oauthProvider, code, state.callbackUri());
            OAuthUserProfile profile = providerClient.fetchUserProfile(oauthProvider, tokenResponse);
            OAuthLoginService.LoginOutcome loginOutcome = loginService.login(
                    provider,
                    profile,
                    new AuthTokenService.DeviceInfo(state.deviceType(), state.deviceName(), state.brand()),
                    RequestUtils.clientIp(request),
                    RequestUtils.userAgent(request)
            );
            String ticket = ticketService.issue(new OAuthLoginTicketService.LoginTicket(
                    provider,
                    loginOutcome.loginResponse()
            ));
            auditService.log(loginOutcome.user().getId(), "auth.login.oauth", Map.of(
                    "provider", provider,
                    "isNewUser", loginOutcome.isNewUser()
            ), RequestUtils.clientIp(request), RequestUtils.userAgent(request));
            redirectWithTicket(response, state.redirectUri(), ticket, provider);
        } catch (OAuthProviderClient.OAuthProviderException
                 | OAuthLoginService.OAuthLoginException
                 | OAuthLoginTicketService.OAuthTicketException e) {
            log.warn("[oauth] callback failed for provider {}: {}", provider, e.getMessage());
            redirectWithError(response, state.redirectUri(), "oauth_failed", "oauth login failed");
        }
    }

    @PostMapping("/consume-ticket")
    public ApiResponse<Map<String, Object>> consumeTicket(
            @RequestBody OAuthConsumeTicketRequest request,
            HttpServletRequest httpRequest
    ) {
        String ticket = request == null ? null : RequestUtils.trimOrNull(request.ticket());
        if (ticket == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "ticket is required");
        }
        if (!rateLimitService.allowConsumeTicket(RequestUtils.clientIp(httpRequest))) {
            return ApiResponse.error(ErrorCodes.RATE_LIMIT, "oauth consume rate limited");
        }
        try {
            OAuthLoginTicketService.LoginTicket loginTicket = ticketService.consume(ticket);
            if (loginTicket == null || loginTicket.loginResponse() == null || loginTicket.loginResponse().isEmpty()) {
                return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "ticket expired");
            }
            guestHistoryLoginSupport.migrateQuietly(httpRequest, loginTicket.loginResponse(), "oauth");
            return ApiResponse.ok(loginTicket.loginResponse());
        } catch (OAuthLoginTicketService.OAuthTicketException e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "consume ticket failed");
        }
    }

    private void redirectWithTicket(HttpServletResponse response, String redirectUri, String ticket, String provider) throws IOException {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", appendQuery(redirectUri, Map.of(
                "oauthTicket", ticket,
                "provider", provider
        )));
    }

    private void redirectWithError(HttpServletResponse response, String redirectUri, String code, String message) throws IOException {
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", appendQuery(redirectUri, Map.of(
                "oauthError", RequestUtils.firstNonBlank(code, "oauth_error"),
                "oauthMessage", RequestUtils.firstNonBlank(message, "oauth login failed")
        )));
    }

    private void writePlainError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(message);
    }

    private String appendQuery(String redirectUri, Map<String, String> params) {
        String safeRedirectUri = redirectUri == null ? "/" : redirectUri;
        String separator = safeRedirectUri.contains("?") ? "&" : "?";
        String suffix = params.entrySet().stream()
                .filter(entry -> !blank(entry.getValue()))
                .map(entry -> enc(entry.getKey()) + "=" + enc(entry.getValue()))
                .collect(Collectors.joining("&"));
        return suffix.isEmpty() ? safeRedirectUri : safeRedirectUri + separator + suffix;
    }

    private boolean isAllowedRedirectUri(String redirectUri, HttpServletRequest request) {
        if (blank(redirectUri)) return false;
        try {
            URI uri = URI.create(redirectUri);
            String scheme = RequestUtils.trimOrNull(uri.getScheme());
            String host = RequestUtils.trimOrNull(uri.getHost());
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return false;
            }
            if (host == null) return false;
            List<String> allowedHosts = runtimeConfigService.currentConfig().allowedRedirectHosts();
            if (allowedHosts == null || allowedHosts.isEmpty()) {
                return host.equalsIgnoreCase(request.getServerName());
            }
            for (String allowed : allowedHosts) {
                String normalized = RequestUtils.trimOrNull(allowed);
                if (normalized != null && host.equalsIgnoreCase(normalized)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private String externalBaseUrl(HttpServletRequest request) {
        String configuredBase = RequestUtils.trimOrNull(runtimeConfigService.currentConfig().callbackBaseUrl());
        if (configuredBase != null) {
            return configuredBase.replaceAll("/+$", "");
        }

        String proto = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean standardPort = ("http".equalsIgnoreCase(proto) && port == 80)
                || ("https".equalsIgnoreCase(proto) && port == 443);
        String portPart = standardPort ? "" : ":" + port;
        return proto + "://" + host + portPart;
    }

    private OAuthProperties.Provider toPropertiesProvider(OAuthRuntimeConfigService.ProviderConfig provider) {
        OAuthProperties.Provider copy = new OAuthProperties.Provider();
        copy.setDisplayName(provider.displayName());
        copy.setClientId(provider.clientId());
        copy.setClientSecret(provider.clientSecret());
        copy.setAuthorizeUri(provider.authorizeUri());
        copy.setTokenUri(provider.tokenUri());
        copy.setUserInfoUri(provider.userInfoUri());
        copy.setEmailUri(provider.emailUri());
        copy.setScope(provider.scope());
        copy.setIcon(provider.icon());
        copy.setEnabled(provider.enabled());
        copy.setAllowUserLogin(provider.allowUserLogin());
        return copy;
    }

    private static String normalizeProvider(String provider) {
        String value = RequestUtils.trimOrNull(provider);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String capitalize(String value) {
        if (blank(value)) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
