package com.webchat.platformapi.ai.extension.tool;

import com.webchat.platformapi.ai.extension.ToolCatalogService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.group.UserGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolV1Controller {

    private final ToolCatalogService toolCatalogService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public ToolV1Controller(
            ToolCatalogService toolCatalogService,
            UserGroupService userGroupService,
            @Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.toolCatalogService = toolCatalogService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "function_calling")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "function calling not allowed by group policy");
        }
        return ApiResponse.ok(toolCatalogService.userConfig(userId));
    }
}
