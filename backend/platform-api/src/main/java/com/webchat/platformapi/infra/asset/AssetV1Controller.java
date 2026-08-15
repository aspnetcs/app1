package com.webchat.platformapi.infra.asset;

import com.webchat.platformapi.asset.MinioAssetService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/asset")
public class AssetV1Controller {

    private static final Logger log = LoggerFactory.getLogger(AssetV1Controller.class);
    private static final Pattern SHA256_HEX = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final MinioAssetService assetService;

    public AssetV1Controller(MinioAssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping("/presign")
    public ApiResponse<Map<String, Object>> presign(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");

        String purpose = str(body, "purpose");
        String filename = str(body, "filename");
        if (purpose == null || purpose.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "purpose is required");
        if (filename == null || filename.isBlank()) filename = "file";

        MinioAssetService.PresignResult result;
        try {
            result = assetService.presignPut(userId, purpose, filename);
        } catch (MinioAssetService.AssetException e) {
            log.warn("[asset] presign failed for user {} purpose {}: {}", userId, purpose, e.getMessage());
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "presign failed");
        }

        return ApiResponse.ok(Map.of(
                "bucket", result.bucket(),
                "objectKey", result.objectKey(),
                "uploadUrl", result.uploadUrl(),
                "method", "PUT",
                "expiresIn", result.expiresInSeconds()
        ));
    }

    @PostMapping("/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody Map<String, Object> body
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");

        String purpose = str(body, "purpose");
        String objectKey = RequestUtils.firstNonBlank(str(body, "objectKey"), str(body, "object_key"));
        String mimeType = RequestUtils.firstNonBlank(str(body, "mimeType"), str(body, "mime_type"));
        String sha256 = normalizeSha256(str(body, "sha256"));
        Long size = longVal(body, "size");

        if (purpose == null || purpose.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "purpose is required");
        if (objectKey == null || objectKey.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "objectKey is required");
        if (sha256 == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "sha256 format is invalid");
        if (size != null && size < 0) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "invalid size");

        String expectedPrefix = "u/" + userId + "/" + purpose + "/";
        if (!objectKey.startsWith(expectedPrefix)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "illegal objectKey");
        }

        MinioAssetService.ConfirmResult result;
        try {
            result = assetService.confirmAndPresignGet(objectKey, size == null ? 0 : size, mimeType, sha256);
        } catch (MinioAssetService.AssetException e) {
            log.warn("[asset] confirm failed for user {} key {}: {}", userId, objectKey, e.getMessage());
            return mapAssetConfirmError(e);
        }

        return ApiResponse.ok(Map.of(
                "bucket", result.bucket(),
                "objectKey", result.objectKey(),
                "url", result.downloadUrl(),
                "expiresIn", result.expiresInSeconds(),
                "size", result.size(),
                "mimeType", result.mimeType(),
                "sha256", result.sha256()
        ));
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long longVal(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object value = body.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    static String normalizeSha256(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (!SHA256_HEX.matcher(text).matches()) {
            return null;
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static ApiResponse<Map<String, Object>> mapAssetConfirmError(MinioAssetService.AssetException e) {
        String message = e == null || e.getMessage() == null ? "" : e.getMessage();
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("size mismatch")
                || normalized.contains("mimetype mismatch")
                || normalized.contains("sha256 mismatch")
                || normalized.contains("sha256 is required")
                || normalized.contains("sha256 format is invalid")
                || normalized.contains("sha256 required")
                || normalized.contains("sha256 invalid")) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "asset verification failed, please upload again");
        }
        if (normalized.contains("object not found")) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "asset not found or expired");
        }
        return ApiResponse.error(ErrorCodes.SERVER_ERROR, "asset confirm failed");
    }
}
