package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.v1.dto.TokenRefreshRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthSessionController {

    private final AuthV1Controller authV1Controller;

    public AuthSessionController(AuthV1Controller authV1Controller) {
        this.authV1Controller = authV1Controller;
    }

    @PostMapping("/token/refresh")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody TokenRefreshRequest req, HttpServletRequest request) {
        return authV1Controller.refresh(req, request);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody TokenRefreshRequest req) {
        return authV1Controller.logout(req);
    }
}
