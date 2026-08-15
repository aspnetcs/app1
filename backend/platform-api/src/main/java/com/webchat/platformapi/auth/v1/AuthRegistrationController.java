package com.webchat.platformapi.auth.v1;

import com.webchat.platformapi.auth.v1.dto.RegisterRequest;
import com.webchat.platformapi.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthRegistrationController {

    private final AuthV1Controller authV1Controller;

    public AuthRegistrationController(AuthV1Controller authV1Controller) {
        this.authV1Controller = authV1Controller;
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest req, HttpServletRequest request) {
        return authV1Controller.register(req, request);
    }
}
