package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.audit.AuditService;
import com.webchat.platformapi.auth.credential.UserCredentialEntity;
import com.webchat.platformapi.auth.credential.UserCredentialRepository;
import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.auth.v1.dto.EmailSendCodeRequest;
import com.webchat.platformapi.auth.v1.dto.PasswordLoginRequest;
import com.webchat.platformapi.auth.v1.dto.SmsSendCodeRequest;
import com.webchat.platformapi.auth.verification.RedisVerificationService;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.UserService;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthV1ControllerTest {

    @Mock
    private RedisVerificationService verificationService;
    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserCredentialRepository credentialRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private AuthTokenService authTokenService;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private AuditService auditService;
    @Mock
    private GuestHistoryLoginSupport guestHistoryLoginSupport;

    private AuthV1Controller controller;
    private PasswordLoginService passwordLoginService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordLoginService = new PasswordLoginService(
                verificationService,
                userRepository,
                credentialRepository,
                redis
        );
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        controller = new AuthV1Controller(
                verificationService,
                userService,
                userRepository,
                credentialRepository,
                userIdentityRepository,
                deviceSessionRepository,
                authTokenService,
                auditService,
                guestHistoryLoginSupport,
                passwordLoginService,
                redis
        );
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AuthVerificationController(controller),
                new AuthLoginController(controller),
                new AuthRegistrationController(controller),
                new AuthSessionController(controller),
                new AuthAccountController(controller)
        ).build();
    }

    @Test
    void smsSendCodeUsesRequesterIpForChallengeToken() {
        MockHttpServletRequest request = requestWithRemoteAddr("203.0.113.10");
        SmsSendCodeRequest req = new SmsSendCodeRequest("13812345678", "login", "challenge");
        when(verificationService.consumeChallengeToken("challenge", "203.0.113.10")).thenReturn(false);

        var response = controller.smsSendCode(req, request);

        assertEquals(ErrorCodes.CAPTCHA_FAILED, response.code());
        verify(verificationService).consumeChallengeToken("challenge", "203.0.113.10");
        verify(verificationService, never()).consumeChallengeToken("challenge");
    }

    @Test
    void emailSendCodeUsesRequesterIpForChallengeToken() {
        MockHttpServletRequest request = requestWithRemoteAddr("198.51.100.12");
        EmailSendCodeRequest req = new EmailSendCodeRequest("user@example.com", "login", "challenge");
        when(verificationService.consumeChallengeToken("challenge", "198.51.100.12")).thenReturn(false);

        var response = controller.emailSendCode(req, request);

        assertEquals(ErrorCodes.CAPTCHA_FAILED, response.code());
        verify(verificationService).consumeChallengeToken("challenge", "198.51.100.12");
        verify(verificationService, never()).consumeChallengeToken("challenge");
    }

    @Test
    void passwordLoginUsesRequesterIpForChallengeToken() {
        MockHttpServletRequest request = requestWithRemoteAddr("198.51.100.24");
        PasswordLoginRequest req = new PasswordLoginRequest("13812345678", null, null, "secret", "challenge");
        when(verificationService.consumeChallengeToken("challenge", "198.51.100.24")).thenReturn(false);

        var response = controller.passwordLogin(req, request);

        assertEquals(ErrorCodes.CAPTCHA_FAILED, response.code());
        verify(verificationService).consumeChallengeToken("challenge", "198.51.100.24");
        verify(verificationService, never()).consumeChallengeToken("challenge");
        verify(userRepository, never()).findByPhoneAndDeletedAtIsNull("13812345678");
    }

    @Test
    void adminLoginRejectsInvalidChallengeTokenUsingRequesterIp() {
        MockHttpServletRequest request = requestWithRemoteAddr("198.51.100.31");
        PasswordLoginRequest req = new PasswordLoginRequest("13812345678", null, null, "secret", "challenge");
        when(verificationService.consumeChallengeToken("challenge", "198.51.100.31")).thenReturn(false);

        var response = controller.adminLogin(req, request);

        assertEquals(ErrorCodes.CAPTCHA_FAILED, response.code());
        verify(verificationService).consumeChallengeToken("challenge", "198.51.100.31");
        verify(verificationService, never()).consumeChallengeToken("challenge");
        verify(userRepository, never()).findByPhoneAndDeletedAtIsNull("13812345678");
    }

    @Test
    void adminLoginUsesSameGenericFailureForNonAdminUser() {
        MockHttpServletRequest request = requestWithRemoteAddr("198.51.100.42");
        PasswordLoginRequest req = new PasswordLoginRequest("13812345678", null, null, "secret", "challenge");
        UserEntity nonAdminUser = new UserEntity();
        nonAdminUser.setId(UUID.randomUUID());
        nonAdminUser.setPhone("13812345678");
        nonAdminUser.setRole("user");

        when(verificationService.consumeChallengeToken("challenge", "198.51.100.42")).thenReturn(true);
        when(userRepository.findByPhoneAndDeletedAtIsNull("13812345678"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(nonAdminUser));

        var missingUserResponse = controller.adminLogin(req, request);
        var nonAdminResponse = controller.adminLogin(req, request);

        assertEquals(ErrorCodes.WRONG_PASSWORD, nonAdminResponse.code());
        assertEquals(missingUserResponse.code(), nonAdminResponse.code());
        assertEquals(missingUserResponse.message(), nonAdminResponse.message());
    }

    @Test
    void passwordLoginRouteContractMatchesAdminLoginFlowEndpoint() throws Exception {
        when(verificationService.consumeChallengeToken("challenge", "198.51.100.25")).thenReturn(false);

        mockMvc.perform(
                        post("/api/v1/auth/password/login")
                                .with(remoteAddr("198.51.100.25"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "identifier": "13812345678",
                                          "password": "secret",
                                          "challengeToken": "challenge"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.CAPTCHA_FAILED));

        verify(verificationService).consumeChallengeToken("challenge", "198.51.100.25");
        verify(userRepository, never()).findByPhoneAndDeletedAtIsNull("13812345678");
    }

    @Test
    void checkIdentifierRouteReturnsHasPasswordForExistingUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhone("13812345678");
        UserCredentialEntity credential = new UserCredentialEntity();
        credential.setUserId(userId);

        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(userRepository.findByPhoneAndDeletedAtIsNull("13812345678")).thenReturn(Optional.of(user));
        when(credentialRepository.findById(userId)).thenReturn(Optional.of(credential));

        mockMvc.perform(
                        post("/api/v1/auth/check-identifier")
                                .with(remoteAddr("198.51.100.77"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "identifier": "13812345678"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.type").value("phone"))
                .andExpect(jsonPath("$.data.hasPassword").value(true));
    }

    @Test
    void checkIdentifierFailsClosedWhenRateLimitStoreUnavailable() {
        MockHttpServletRequest request = requestWithRemoteAddr("198.51.100.99");
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis unavailable"));

        var response = controller.checkIdentifier("user@example.com", request);

        assertEquals(ErrorCodes.RATE_LIMIT, response.code());
        verify(userRepository, never()).findByEmailAndDeletedAtIsNull("user@example.com");
    }

    @Test
    void smsLoginMigratesGuestHistoryOnSuccessfulLogin() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setPhone("13812345678");
        AuthTokenService.IssuedTokens tokens = new AuthTokenService.IssuedTokens(
                "access-token",
                "refresh-token",
                UUID.randomUUID(),
                3600,
                86400
        );
        MockHttpServletRequest request = requestWithRemoteAddr("203.0.113.88");

        when(verificationService.verifySmsCode("13812345678", "login", "123456")).thenReturn(true);
        when(userService.findOrCreateByPhone("13812345678"))
                .thenReturn(new UserService.FindOrCreateResult(user, false));
        when(userIdentityRepository.existsByUserIdAndProvider(userId, "wechat")).thenReturn(false);
        when(authTokenService.issue(user, null, "203.0.113.88", null)).thenReturn(tokens);

        var response = controller.smsLogin(new com.webchat.platformapi.auth.v1.dto.SmsLoginRequest("13812345678", "123456", "login"), request);

        assertEquals(0, response.code());
        verify(guestHistoryLoginSupport).migrateQuietly(request, userId, "auth");
    }

    private static MockHttpServletRequest requestWithRemoteAddr(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static RequestPostProcessor remoteAddr(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
