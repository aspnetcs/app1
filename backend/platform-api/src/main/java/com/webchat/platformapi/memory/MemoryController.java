package com.webchat.platformapi.memory;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private final MemoryRuntimeService memoryRuntimeService;

    public MemoryController(MemoryRuntimeService memoryRuntimeService) {
        this.memoryRuntimeService = memoryRuntimeService;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        return ApiResponse.ok(memoryRuntimeService.getRuntimeConfig(userId));
    }

    @PutMapping("/consent")
    public ApiResponse<Map<String, Object>> updateConsent(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) MemoryConsentRequest body
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        if (body == null || body.enabled() == null) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "enabled is required");
        }
        return ApiResponse.ok(memoryRuntimeService.updateConsent(userId, body.enabled()));
    }

    public record MemoryConsentRequest(Boolean enabled) {
    }
}
