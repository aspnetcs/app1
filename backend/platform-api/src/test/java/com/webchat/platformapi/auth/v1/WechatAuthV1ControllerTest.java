package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.guest.GuestHistoryLoginSupport;
import com.webchat.platformapi.auth.oauth.wechat.WechatService;
import com.webchat.platformapi.auth.session.AuthTokenService;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.infra.config.BrandResolver;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import com.webchat.platformapi.user.identity.UserIdentityEntity;
import com.webchat.platformapi.user.identity.UserIdentityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WechatAuthV1ControllerTest {

    @Mock
    private WechatService wechatService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private GuestHistoryLoginSupport guestHistoryLoginSupport;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BrandResolver brandResolver = new BrandResolver();
        brandResolver.setAllowExplicitRequestBrand(true);
        WechatAuthV1Controller controller = new WechatAuthV1Controller(
                wechatService,
                brandResolver,
                userRepository,
                userIdentityRepository,
                authTokenService,
                guestHistoryLoginSupport
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void wxLoginUsesExactRouteAndReturnsIssuedTokens() throws Exception {
        UserEntity user = new UserEntity();
        UUID userId = UUID.randomUUID();
        user.setId(userId);

        when(wechatService.jscode2session("mini", "login-code"))
                .thenReturn(new WechatService.WechatSession("openid-1", "union-1", "session-key"));
        when(userIdentityRepository.findFirstByProviderAndProviderScopeAndTypeAndIdentifier(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);
        when(userIdentityRepository.save(any(UserIdentityEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authTokenService.issue(eq(user), any(), eq("203.0.113.8"), eq("JUnit")))
                .thenReturn(new AuthTokenService.IssuedTokens(
                        "access-token",
                        "refresh-token",
                        UUID.randomUUID(),
                        3600,
                        86400
                ));

        mockMvc.perform(
                        post("/api/v1/auth/wx-login")
                                .with(remoteAddr("203.0.113.8"))
                                .header("User-Agent", "JUnit")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "brand": "mini",
                                          "code": "login-code",
                                          "deviceType": "wechat",
                                          "deviceName": "iPhone"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("access-token"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.isNewUser").value(true));

        verify(wechatService).jscode2session("mini", "login-code");
        verify(authTokenService).issue(eq(user), any(), eq("203.0.113.8"), eq("JUnit"));
        verify(guestHistoryLoginSupport).migrateQuietly(any(), eq(userId), eq("wechat-auth"));
    }

    @Test
    void wxLoginRejectsMissingCode() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/wx-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "brand": "mini"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.PARAM_MISSING))
                .andExpect(jsonPath("$.message").value("参数缺失"));
    }

    @Test
    void wxPhoneLoginAcceptsPhoneCodeAliasAndValidatesPhone() throws Exception {
        when(wechatService.jscode2session("mini", "login-code"))
                .thenReturn(new WechatService.WechatSession("openid-1", "union-1", "session-key"));
        when(wechatService.getPhoneNumber("mini", "phone-code")).thenReturn("bad-phone");

        mockMvc.perform(
                        post("/api/v1/auth/wx-phone-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "brand": "mini",
                                          "code": "login-code",
                                          "phone_code": "phone-code"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.INVALID_PHONE))
                .andExpect(jsonPath("$.message").value("手机号无效"));

        verify(wechatService).jscode2session("mini", "login-code");
        verify(wechatService).getPhoneNumber("mini", "phone-code");
    }

    private static RequestPostProcessor remoteAddr(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
