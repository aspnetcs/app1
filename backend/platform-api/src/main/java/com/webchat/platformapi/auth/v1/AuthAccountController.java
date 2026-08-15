package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.v1.dto.ChangePasswordRequest;
import com.webchat.platformapi.auth.v1.dto.SetPasswordRequest;
import com.webchat.platformapi.auth.v1.dto.UpdateEmailRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthAccountController {

    private final AuthV1Controller authV1Controller;

    public AuthAccountController(AuthV1Controller authV1Controller) {
        this.authV1Controller = authV1Controller;
    }

    @PostMapping("/set-password")
    public ApiResponse<Void> setPassword(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody SetPasswordRequest req
    ) {
        return authV1Controller.setPassword(userId, req);
    }

    @PostMapping("/update-email")
    public ApiResponse<Void> updateEmail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody UpdateEmailRequest req
    ) {
        return authV1Controller.updateEmail(userId, req);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody ChangePasswordRequest req
    ) {
        return authV1Controller.changePassword(userId, req);
    }

    @PostMapping("/delete-account")
    public ApiResponse<Void> deleteAccount(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        return authV1Controller.deleteAccount(userId);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        return authV1Controller.me(userId);
    }
}
