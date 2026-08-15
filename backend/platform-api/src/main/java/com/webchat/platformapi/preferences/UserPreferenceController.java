package com.webchat.platformapi.preferences;

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
@RequestMapping("/api/v1/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    public UserPreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getPreferences(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        return ApiResponse.ok(preferenceService.getPreferences(userId));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> updatePreferences(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            return ApiResponse.ok(preferenceService.updatePreferences(userId, body));
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        } catch (IllegalStateException ex) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, ex.getMessage());
        }
    }
}
