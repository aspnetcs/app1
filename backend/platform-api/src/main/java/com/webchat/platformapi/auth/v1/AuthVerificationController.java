package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.v1.dto.BindEmailRequest;
import com.webchat.platformapi.auth.v1.dto.BindEmailSendCodeRequest;
import com.webchat.platformapi.auth.v1.dto.EmailSendCodeRequest;
import com.webchat.platformapi.auth.v1.dto.SmsSendCodeRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthVerificationController {

    private final AuthV1Controller authV1Controller;

    public AuthVerificationController(AuthV1Controller authV1Controller) {
        this.authV1Controller = authV1Controller;
    }

    @PostMapping("/sms/send-code")
    public ApiResponse<Void> smsSendCode(@RequestBody SmsSendCodeRequest req, HttpServletRequest request) {
        return authV1Controller.smsSendCode(req, request);
    }

    @PostMapping("/bind-email-send-code")
    public ApiResponse<Void> bindEmailSendCode(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody BindEmailSendCodeRequest req
    ) {
        return authV1Controller.bindEmailSendCode(userId, req);
    }

    @PostMapping("/bind-email")
    public ApiResponse<Void> bindEmail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody BindEmailRequest req
    ) {
        return authV1Controller.bindEmail(userId, req);
    }

    @PostMapping("/email/send-code")
    public ApiResponse<Void> emailSendCode(@RequestBody EmailSendCodeRequest req, HttpServletRequest request) {
        return authV1Controller.emailSendCode(req, request);
    }
}
