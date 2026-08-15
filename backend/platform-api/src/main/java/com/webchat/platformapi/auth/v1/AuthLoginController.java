package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.v1.dto.CheckIdentifierRequest;
import com.webchat.platformapi.auth.v1.dto.EmailLoginRequest;
import com.webchat.platformapi.auth.v1.dto.PasswordLoginRequest;
import com.webchat.platformapi.auth.v1.dto.SmsLoginRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthLoginController {

    private final AuthV1Controller authV1Controller;

    public AuthLoginController(AuthV1Controller authV1Controller) {
        this.authV1Controller = authV1Controller;
    }

    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> smsLogin(@RequestBody SmsLoginRequest req, HttpServletRequest request) {
        return authV1Controller.smsLogin(req, request);
    }

    @PostMapping("/sms-login")
    public ApiResponse<Map<String, Object>> smsLoginCompat(@RequestBody SmsLoginRequest req, HttpServletRequest request) {
        return authV1Controller.smsLoginCompat(req, request);
    }

    @PostMapping("/email/login")
    public ApiResponse<Map<String, Object>> emailLogin(@RequestBody EmailLoginRequest req, HttpServletRequest request) {
        return authV1Controller.emailLogin(req, request);
    }

    @PostMapping("/email-login")
    public ApiResponse<Map<String, Object>> emailLoginCompat(@RequestBody EmailLoginRequest req, HttpServletRequest request) {
        return authV1Controller.emailLoginCompat(req, request);
    }

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> passwordLogin(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        return authV1Controller.passwordLogin(req, request);
    }

    @PostMapping("/pwd-login")
    public ApiResponse<Map<String, Object>> passwordLoginCompat(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        return authV1Controller.passwordLoginCompat(req, request);
    }

    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody PasswordLoginRequest req, HttpServletRequest request) {
        return authV1Controller.adminLogin(req, request);
    }

    @PostMapping("/check-identifier")
    public ApiResponse<Map<String, Object>> checkIdentifier(
            @RequestBody CheckIdentifierRequest req,
            HttpServletRequest request
    ) {
        return authV1Controller.checkIdentifier(req.identifier(), request);
    }
}
