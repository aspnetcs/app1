package com.webchat.platformapi.auth.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CaptchaV1ControllerTest {

    @Mock
    private CaptchaService captchaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CaptchaV1Controller(captchaService)).build();
    }

    @Test
    void generateRouteMatchesAdminLoginFlowEndpoint() throws Exception {
        when(captchaService.allowGenerate("203.0.113.10")).thenReturn(true);
        when(captchaService.generateCaptcha()).thenReturn(Map.of(
                "captchaId", "captcha-1",
                "type", "math",
                "question", "1 + 2 = ?"
        ));

        mockMvc.perform(get("/api/v1/risk/captcha/generate").with(remoteAddr("203.0.113.10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.captchaId").value("captcha-1"))
                .andExpect(jsonPath("$.data.type").value("math"));

        verify(captchaService).allowGenerate("203.0.113.10");
    }

    @Test
    void verifyRouteMatchesAdminLoginFlowEndpoint() throws Exception {
        when(captchaService.allowVerify("198.51.100.8")).thenReturn(true);
        when(captchaService.verifyCaptchaAndIssueChallenge(eq("captcha-1"), anyMap(), eq("198.51.100.8")))
                .thenReturn("challenge-token");

        mockMvc.perform(
                        post("/api/v1/risk/captcha/verify")
                                .with(remoteAddr("198.51.100.8"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "captchaId": "captcha-1",
                                          "data": {
                                            "answer": 3
                                          }
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("challenge-token"));

        verify(captchaService).allowVerify("198.51.100.8");
        verify(captchaService).verifyCaptchaAndIssueChallenge(eq("captcha-1"), anyMap(), eq("198.51.100.8"));
    }

    private static RequestPostProcessor remoteAddr(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
