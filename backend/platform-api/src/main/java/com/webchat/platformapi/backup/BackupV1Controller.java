package com.webchat.platformapi.backup;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/backup")
@ConditionalOnProperty(name = "platform.backup.enabled", havingValue = "true", matchIfMissing = true)
public class BackupV1Controller {

    private final BackupManifestService backupManifestService;

    public BackupV1Controller(BackupManifestService backupManifestService) {
        this.backupManifestService = backupManifestService;
    }

    @GetMapping("/manifest")
    public ApiResponse<Map<String, Object>> manifest(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "limit", defaultValue = "200") int limit
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(backupManifestService.buildUserManifest(userId, limit));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}

