package com.webchat.platformapi.backup;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backup")
public class UserBackupController {

    private final UserBackupService userBackupService;
    private final boolean backupEnabled;

    public UserBackupController(
            UserBackupService userBackupService,
            @Value("${platform.backup.enabled:true}") boolean backupEnabled
    ) {
        this.userBackupService = userBackupService;
        this.backupEnabled = backupEnabled;
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> export(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "modules", required = false) String modules,
            @RequestParam(name = "conversationLimit", required = false) Integer conversationLimit,
            @RequestParam(name = "messageLimit", required = false) Integer messageLimit
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!backupEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "backup disabled");

        List<String> requestedModules = UserBackupModules.parse(modules);
        UserBackupLimits limits = new UserBackupLimits(conversationLimit, messageLimit);
        return ApiResponse.ok(userBackupService.exportUserPayload(userId, requestedModules, limits));
    }
}

