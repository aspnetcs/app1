package com.webchat.platformapi.file;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileV1Controller {

    private final FileLibraryService fileLibraryService;
    private final boolean filesEnabled;

    public FileV1Controller(
            FileLibraryService fileLibraryService,
            @Value("${platform.files.enabled:false}") boolean filesEnabled
    ) {
        this.fileLibraryService = fileLibraryService;
        this.filesEnabled = filesEnabled;
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> upload(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!filesEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "files disabled");
        if (file == null || file.isEmpty()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file is required");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "failed to read file");
        }

        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();

        FileLibraryService.UploadResult result;
        try {
            result = fileLibraryService.uploadUserFile(userId, purpose, originalName, contentType, bytes);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage() == null ? "invalid file" : e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "upload failed");
        }

        FileEntity entity = result.entity();
        return ApiResponse.ok(Map.of(
                "fileId", entity.getId(),
                "originalName", entity.getOriginalName(),
                "size", entity.getSizeBytes(),
                "mimeType", entity.getMimeType(),
                "sha256", entity.getSha256(),
                "kind", entity.getKind(),
                "url", result.url(),
                "expiresIn", result.expiresInSeconds()
        ));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("fileId") String fileId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!filesEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "files disabled");

        FileEntity entity = fileLibraryService.getUserFileOrNull(userId, fileId);
        if (entity == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file not found");

        return ApiResponse.ok(Map.of(
                "fileId", entity.getId(),
                "originalName", entity.getOriginalName(),
                "size", entity.getSizeBytes(),
                "mimeType", entity.getMimeType(),
                "sha256", entity.getSha256(),
                "kind", entity.getKind(),
                "createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString()
        ));
    }

    @GetMapping("/{fileId}/url")
    public ApiResponse<Map<String, Object>> url(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "mode", required = false) String mode
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!filesEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "files disabled");

        FileEntity entity = fileLibraryService.getUserFileOrNull(userId, fileId);
        if (entity == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file not found");

        // mode is reserved for future (preview vs download). Currently both map to GET presign.
        Map<String, Object> out;
        try {
            out = fileLibraryService.presignUrl(entity);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "failed to create url");
        }

        return ApiResponse.ok(Map.of(
                "mode", mode == null ? "" : mode,
                "url", out.get("url"),
                "expiresIn", out.get("expiresIn")
        ));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("fileId") String fileId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!filesEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "files disabled");

        FileLibraryService.DeleteResult result;
        try {
            result = fileLibraryService.deleteUserFile(userId, fileId);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage() == null ? "invalid request" : e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "delete failed");
        }

        if (!result.deleted() && "referenced".equals(result.reason())) {
            return ApiResponse.ok(Map.of(
                    "deleted", false,
                    "reason", "referenced",
                    "refs", result.refs()
            ));
        }
        if (!result.deleted() && "not_found".equals(result.reason())) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file not found");
        }

        return ApiResponse.ok(Map.of(
                "deleted", result.deleted(),
                "mode", result.mode()
        ));
    }
}

