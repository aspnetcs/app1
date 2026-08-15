package com.webchat.platformapi.social.banner;

import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.auth.group.UserGroupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/banners")
@ConditionalOnProperty(name = "platform.banner.enabled", havingValue = "true")
public class BannerController {

    private final BannerService bannerService;
    private final UserGroupService userGroupService;
    private final boolean userGroupsEnabled;

    public BannerController(
            BannerService bannerService,
            UserGroupService userGroupService,
            @Value("${platform.user-groups.enabled:false}") boolean userGroupsEnabled
    ) {
        this.bannerService = bannerService;
        this.userGroupService = userGroupService;
        this.userGroupsEnabled = userGroupsEnabled;
    }

    @GetMapping("/active")
    public ApiResponse<List<Map<String, Object>>> active(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (userGroupsEnabled && !userGroupService.isFeatureAllowed(userId, "banner")) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "banner not allowed by group policy");
        }
        return ApiResponse.ok(bannerService.listActive());
    }
}
