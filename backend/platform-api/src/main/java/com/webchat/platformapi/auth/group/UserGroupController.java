package com.webchat.platformapi.auth.group;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups")
@ConditionalOnProperty(name = "platform.user-groups.enabled", havingValue = "true")
public class UserGroupController {

    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        UserGroupService.GroupProfile profile = userGroupService.resolveProfile(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("groups", profile.groups());
        data.put("allowedModels", profile.allowedModels());
        data.put("featureFlags", profile.featureFlags());
        data.put("chatRateLimitPerMinute", profile.chatRateLimitPerMinute() == null ? 0 : profile.chatRateLimitPerMinute());
        return ApiResponse.ok(data);
    }
}
