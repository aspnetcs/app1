package com.webchat.platformapi.auth.guest;

import com.webchat.platformapi.common.api.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GuestAuthControllerTest {

    @Mock
    private GuestAuthService guestAuthService;
    @Mock
    private GuestRateLimitService guestRateLimitService;

    private GuestAuthProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new GuestAuthProperties();
        GuestAuthController controller = new GuestAuthController(properties, guestAuthService, guestRateLimitService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void guestAuthFeatureIsEnabledByDefaultForGuestFirstFlow() {
        Assertions.assertTrue(new GuestAuthProperties().isEnabled());
    }

    @Test
    void guestLoginReturnsGuestSessionContract() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(guestRateLimitService.allowIssue("203.0.113.8")).thenReturn(true);
        when(guestAuthService.issueGuestSession("device-123", "recovery-123", "browser-fingerprint-123", "203.0.113.8", null)).thenReturn(Map.of(
                "token", "access-token",
                "accessToken", "access-token",
                "refreshToken", "refresh-token",
                "expiresIn", 3600,
                "refreshExpiresIn", 86400,
                "sessionId", sessionId.toString(),
                "guestRecoveryToken", "guest-recovery-token",
                "isNewUser", true,
                "sessionType", "guest",
                "userInfo", Map.of(
                        "userId", userId.toString(),
                        "phone", "",
                        "email", "",
                        "nickName", "访客",
                        "avatar", "",
                        "role", "guest",
                        "wxBound", false,
                        "createdAt", "2026-03-23T00:00:00Z"
                )
        ));

        mockMvc.perform(
                        post("/api/v1/auth/guest")
                                .with(remoteAddr("203.0.113.8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "deviceId": "device-123",
                                          "recoveryToken": "recovery-123",
                                          "deviceFingerprint": "browser-fingerprint-123"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionType").value("guest"))
                .andExpect(jsonPath("$.data.guestRecoveryToken").value("guest-recovery-token"))
                .andExpect(jsonPath("$.data.userInfo.role").value("guest"))
                .andExpect(jsonPath("$.data.userInfo.nickName").value("访客"))
                .andExpect(result -> {
                    String cookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
                    Assertions.assertNotNull(cookie);
                    Assertions.assertTrue(cookie.contains("guest_recovery="));
                    Assertions.assertTrue(cookie.contains("HttpOnly"));
                    Assertions.assertTrue(cookie.contains("SameSite=Lax"));
                });

        verify(guestRateLimitService).allowIssue("203.0.113.8");
        verify(guestAuthService).issueGuestSession("device-123", "recovery-123", "browser-fingerprint-123", "203.0.113.8", null);
    }

    @Test
    void guestLoginRejectsWhenFeatureDisabled() throws Exception {
        properties.setEnabled(false);

        mockMvc.perform(post("/api/v1/auth/guest").with(remoteAddr("203.0.113.9")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.SERVER_ERROR))
                .andExpect(jsonPath("$.message").value("guest auth is disabled"));
    }

    @Test
    void guestLoginRejectsWhenRateLimited() throws Exception {
        when(guestRateLimitService.allowIssue("203.0.113.10")).thenReturn(false);

        mockMvc.perform(post("/api/v1/auth/guest").with(remoteAddr("203.0.113.10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.RATE_LIMIT))
                .andExpect(jsonPath("$.message").value("guest auth rate limit exceeded"));
    }

    @Test
    void guestLoginStillSucceedsWhenRecoveryCookieWriteFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(guestRateLimitService.allowIssue("203.0.113.11")).thenReturn(true);
        when(guestAuthService.issueGuestSession("device-xyz", null, null, "203.0.113.11", null)).thenReturn(Map.of(
                "token", "access-token",
                "accessToken", "access-token",
                "refreshToken", "refresh-token",
                "expiresIn", 3600,
                "refreshExpiresIn", 86400,
                "sessionId", sessionId.toString(),
                "guestRecoveryToken", "guest-recovery-token",
                "isNewUser", true,
                "sessionType", "guest",
                "userInfo", Map.of(
                        "userId", userId.toString(),
                        "phone", "",
                        "email", "",
                        "nickName", "访客",
                        "avatar", "",
                        "role", "guest",
                        "wxBound", false,
                        "createdAt", "2026-03-23T00:00:00Z"
                )
        ));

        GuestAuthController controller = new GuestAuthController(properties, guestAuthService, guestRateLimitService) {
            @Override
            protected void doWriteGuestRecoveryCookie(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Map<String, Object> payload
            ) {
                throw new IllegalStateException("cookie write failed");
            }
        };
        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        localMockMvc.perform(
                        post("/api/v1/auth/guest")
                                .with(remoteAddr("203.0.113.11"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "deviceId": "device-xyz"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionType").value("guest"))
                .andExpect(jsonPath("$.data.guestRecoveryToken").value("guest-recovery-token"));
    }

    private static RequestPostProcessor remoteAddr(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
