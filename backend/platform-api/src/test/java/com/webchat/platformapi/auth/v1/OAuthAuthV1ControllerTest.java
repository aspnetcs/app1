package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.auth.oauth.OAuthLoginService;
import com.webchat.platformapi.auth.oauth.OAuthLoginStateService;
import com.webchat.platformapi.auth.oauth.OAuthLoginTicketService;
import com.webchat.platformapi.auth.oauth.OAuthProviderClient;
import com.webchat.platformapi.auth.oauth.OAuthRateLimitService;
import com.webchat.platformapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthAuthV1ControllerTest {

    @Mock
    private OAuthRuntimeConfigService runtimeConfigService;
    @Mock
    private OAuthProviderClient providerClient;
    @Mock
    private OAuthLoginStateService stateService;
    @Mock
    private OAuthLoginTicketService ticketService;
    @Mock
    private OAuthLoginService loginService;
    @Mock
    private OAuthRateLimitService rateLimitService;
    @Mock
    private AuditService auditService;
    @Mock
    private GuestHistoryLoginSupport guestHistoryLoginSupport;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(providerClient.providerKey()).thenReturn("github");
        OAuthAuthV1Controller controller = new OAuthAuthV1Controller(
                runtimeConfigService,
                List.of(providerClient),
                stateService,
                ticketService,
                loginService,
                rateLimitService,
                auditService,
                guestHistoryLoginSupport
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        clearInvocations(
                providerClient,
                runtimeConfigService,
                stateService,
                ticketService,
                loginService,
                rateLimitService,
                auditService,
                guestHistoryLoginSupport
        );
    }

    @Test
    void providersRouteMatchesAdminLoginFlowEndpoint() throws Exception {
        when(runtimeConfigService.currentConfig()).thenReturn(runtimeConfig());

        mockMvc.perform(get("/api/v1/auth/oauth/providers").queryParam("scene", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.providers[0].provider").value("github"))
                .andExpect(jsonPath("$.data.providers[0].displayName").value("GitHub"));
    }

    @Test
    void startRouteMatchesAdminLoginFlowEndpoint() throws Exception {
        when(rateLimitService.allowStart(anyString(), eq("github"))).thenReturn(true);
        when(runtimeConfigService.currentConfig()).thenReturn(runtimeConfig());
        when(stateService.issue(any())).thenReturn("state-token");
        when(providerClient.buildAuthorizeUrl(any(), eq("state-token"), contains("/api/v1/auth/oauth/github/callback")))
                .thenReturn("https://github.com/login/oauth/authorize?state=state-token");

        mockMvc.perform(
                        post("/api/v1/auth/oauth/github/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "redirectUri": "http://localhost/admin/login"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.provider").value("github"))
                .andExpect(jsonPath("$.data.authorizeUrl").value("https://github.com/login/oauth/authorize?state=state-token"));
    }

    @Test
    void startRouteReturnsRateLimitWhenStartQuotaExceeded() throws Exception {
        when(rateLimitService.allowStart(anyString(), eq("github"))).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/auth/oauth/github/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "redirectUri": "http://localhost/admin/login"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("oauth start rate limited"));

        verifyNoInteractions(runtimeConfigService, stateService, providerClient);
    }

    @Test
    void startRouteReturnsUnauthorizedWhenProviderDisabled() throws Exception {
        when(rateLimitService.allowStart(anyString(), eq("github"))).thenReturn(true);
        when(runtimeConfigService.currentConfig()).thenReturn(runtimeConfig(false));

        mockMvc.perform(
                        post("/api/v1/auth/oauth/github/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "redirectUri": "http://localhost/admin/login"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("oauth login disabled"));

        verifyNoInteractions(stateService, providerClient);
    }

    @Test
    void consumeTicketRouteMatchesAdminLoginFlowEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        when(rateLimitService.allowConsumeTicket(anyString())).thenReturn(true);
        when(ticketService.consume("ticket-123"))
                .thenReturn(new OAuthLoginTicketService.LoginTicket("github", Map.of(
                        "accessToken", "jwt-token",
                        "userInfo", Map.of("userId", userId.toString())
                )));

        mockMvc.perform(
                        post("/api/v1/auth/oauth/consume-ticket")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "ticket": "ticket-123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"));

        verify(guestHistoryLoginSupport).migrateQuietly(any(), any(Map.class), eq("oauth"));
    }

    @Test
    void consumeTicketReturnsRateLimitWhenQuotaExceeded() throws Exception {
        when(rateLimitService.allowConsumeTicket(anyString())).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/auth/oauth/consume-ticket")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "ticket": "ticket-123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("oauth consume rate limited"));

        verifyNoInteractions(ticketService, guestHistoryLoginSupport);
    }

    @Test
    void callbackReturnsBadRequestWhenStateReadFails() throws Exception {
        when(stateService.consume(anyString())).thenThrow(new OAuthLoginStateService.OAuthStateException("state read failed"));

        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .queryParam("state", "bad-state")
                        .queryParam("code", "oauth-code"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("oauth state invalid"));

        verifyNoInteractions(runtimeConfigService, providerClient, loginService, ticketService, auditService, guestHistoryLoginSupport);
    }

    @Test
    void callbackRedirectsWithDisabledErrorWhenProviderLoginDisabled() throws Exception {
        when(stateService.consume("state-token")).thenReturn(new OAuthLoginStateService.LoginState(
                "github",
                "http://localhost/admin/login",
                "http://localhost/api/v1/auth/oauth/github/callback",
                null,
                null,
                null
        ));
        when(runtimeConfigService.currentConfig()).thenReturn(runtimeConfig(true, false));

        mockMvc.perform(get("/api/v1/auth/oauth/github/callback")
                        .queryParam("state", "state-token")
                        .queryParam("code", "oauth-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("oauthError=disabled")))
                .andExpect(header().string("Location", containsString("oauthMessage=oauth+login+disabled")));

        verifyNoInteractions(providerClient, loginService, ticketService, auditService, guestHistoryLoginSupport);
    }

    private static OAuthRuntimeConfigService.RuntimeConfig runtimeConfig() {
        return runtimeConfig(true, true);
    }

    private static OAuthRuntimeConfigService.RuntimeConfig runtimeConfig(boolean providerEnabled) {
        return runtimeConfig(providerEnabled, true);
    }

    private static OAuthRuntimeConfigService.RuntimeConfig runtimeConfig(boolean providerEnabled, boolean allowUserLogin) {
        return new OAuthRuntimeConfigService.RuntimeConfig(
                true,
                true,
                600,
                120,
                null,
                List.of("localhost"),
                Map.of(
                        "github",
                        new OAuthRuntimeConfigService.ProviderConfig(
                                "GitHub",
                                "client-id",
                                "client-secret",
                                "https://github.com/login/oauth/authorize",
                                "https://github.com/login/oauth/access_token",
                                "https://api.github.com/user",
                                "https://api.github.com/user/emails",
                                "user:email",
                                "github",
                                providerEnabled,
                                allowUserLogin
                        )
                )
        );
    }
}
