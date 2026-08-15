package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthVerificationControllerTest {

    @Mock
    private AuthV1Controller authV1Controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthVerificationController(authV1Controller)).build();
    }

    @Test
    void smsSendCodeUsesExactRouteAndBindsBody() throws Exception {
        when(authV1Controller.smsSendCode(any(), any()))
                .thenReturn(ApiResponse.ok("sent", null));

        mockMvc.perform(
                        post("/api/v1/auth/sms/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "phone": "13812345678",
                                          "purpose": "login",
                                          "challengeToken": "challenge"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("sent"));

        verify(authV1Controller).smsSendCode(
                argThat(req -> req != null
                        && "13812345678".equals(req.phone())
                        && "login".equals(req.purpose())
                        && "challenge".equals(req.challengeToken())),
                any()
        );
    }

    @Test
    void bindEmailSendCodeBindsBodyWhenUserIdIsMissing() throws Exception {
        when(authV1Controller.bindEmailSendCode(eq(null), any()))
                .thenReturn(ApiResponse.ok("sent", null));

        mockMvc.perform(
                        post("/api/v1/auth/bind-email-send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "phone": "13812345678",
                                          "email": "user@example.com",
                                          "purpose": "bind"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("sent"));

        verify(authV1Controller).bindEmailSendCode(
                eq(null),
                argThat(req -> req != null
                        && "13812345678".equals(req.phone())
                        && "user@example.com".equals(req.email())
                        && "bind".equals(req.purpose()))
        );
    }

    @Test
    void bindEmailAllowsMissingUserIdAndDelegatesNullPath() throws Exception {
        when(authV1Controller.bindEmail(eq(null), any()))
                .thenReturn(ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated"));

        mockMvc.perform(
                        post("/api/v1/auth/bind-email")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "phone": "13812345678",
                                          "email": "user@example.com",
                                          "code": "123456",
                                          "purpose": "bind"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED))
                .andExpect(jsonPath("$.message").value("user not authenticated"));

        verify(authV1Controller).bindEmail(
                eq(null),
                argThat(req -> req != null
                        && "13812345678".equals(req.phone())
                        && "user@example.com".equals(req.email())
                        && "123456".equals(req.code())
                        && "bind".equals(req.purpose()))
        );
    }

    @Test
    void emailSendCodeUsesExactRouteAndBindsBody() throws Exception {
        when(authV1Controller.emailSendCode(any(), any()))
                .thenReturn(ApiResponse.ok("sent", null));

        mockMvc.perform(
                        post("/api/v1/auth/email/send-code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "user@example.com",
                                          "purpose": "login",
                                          "challengeToken": "challenge"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("sent"));

        verify(authV1Controller).emailSendCode(
                argThat(req -> req != null
                        && "user@example.com".equals(req.email())
                        && "login".equals(req.purpose())
                        && "challenge".equals(req.challengeToken())),
                any()
        );
    }

}
