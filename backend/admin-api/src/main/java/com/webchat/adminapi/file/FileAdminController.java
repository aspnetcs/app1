package com.webchat.adminapi.file;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.file.FileEntity;
import com.webchat.platformapi.file.FileLibraryService;
import com.webchat.platformapi.file.FileRefEntity;
import com.webchat.platformapi.file.FileRefRepository;
import com.webchat.platformapi.file.FileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/files")
public class FileAdminController {

    private final FileRepository fileRepo;
    private final FileRefRepository refRepo;
    private final FileLibraryService fileLibraryService;

    public FileAdminController(FileRepository fileRepo, FileRefRepository refRepo, FileLibraryService fileLibraryService) {
        this.fileRepo = fileRepo;
        this.refRepo = refRepo;
        this.fileLibraryService = fileLibraryService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String kind
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(200, size));

        Page<FileEntity> entries = fileRepo.adminSearch(
                RequestUtils.trimOrNull(keyword),
                normalize(kind),
                null,
                true,
                PageRequest.of(safePage, safeSize)
        );

        List<Map<String, Object>> items = entries.getContent().stream().map(FileAdminController::toListItem).toList();
        return ApiResponse.ok(Map.of(
                "items", items,
                "total", entries.getTotalElements(),
                "page", entries.getNumber(),
                "size", entries.getSize()
        ));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<Map<String, Object>> detail(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("fileId") String fileId
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        if (fileId == null || fileId.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "fileId is required");

        FileEntity entity = fileRepo.findById(fileId.trim()).orElse(null);
        if (entity == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file not found");

        return ApiResponse.ok(toDetailItem(entity));
    }

    @GetMapping("/{fileId}/refs")
    public ApiResponse<Map<String, Object>> refs(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("fileId") String fileId
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        if (fileId == null || fileId.isBlank()) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "fileId is required");

        long count = refRepo.countByFileId(fileId.trim());
        List<FileRefEntity> refs = refRepo.findTop200ByFileIdOrderByIdAsc(fileId.trim());
        List<Map<String, Object>> items = refs.stream().map(FileAdminController::toRefItem).toList();

        return ApiResponse.ok(Map.of(
                "items", items,
                "total", count
        ));
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Map<String, Object>> delete(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "mode", required = false) String mode
    ) {
        ApiResponse<?> authErr = requireAdmin(userId, role);
        if (authErr != null) return (ApiResponse<Map<String, Object>>) authErr;

        FileLibraryService.DeleteResult result;
        try {
            result = fileLibraryService.adminDelete(fileId, mode);
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

    private static Map<String, Object> toListItem(FileEntity entity) {
        return Map.of(
                "fileId", entity.getId(),
                "createdBy", String.valueOf(entity.getCreatedBy()),
                "purpose", entity.getPurpose(),
                "originalName", entity.getOriginalName(),
                "size", entity.getSizeBytes(),
                "mimeType", entity.getMimeType() == null ? "" : entity.getMimeType(),
                "sha256", entity.getSha256() == null ? "" : entity.getSha256(),
                "kind", entity.getKind(),
                "createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString(),
                "deletedAt", entity.getDeletedAt() == null ? "" : entity.getDeletedAt().toString()
        );
    }

    private static Map<String, Object> toDetailItem(FileEntity entity) {
        return Map.ofEntries(
                Map.entry("fileId", entity.getId()),
                Map.entry("createdBy", String.valueOf(entity.getCreatedBy())),
                Map.entry("purpose", entity.getPurpose()),
                Map.entry("originalName", entity.getOriginalName()),
                Map.entry("size", entity.getSizeBytes()),
                Map.entry("mimeType", entity.getMimeType() == null ? "" : entity.getMimeType()),
                Map.entry("sha256", entity.getSha256() == null ? "" : entity.getSha256()),
                Map.entry("kind", entity.getKind()),
                Map.entry("bucket", entity.getBucket()),
                Map.entry("objectKey", entity.getObjectKey()),
                Map.entry("createdAt", entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString()),
                Map.entry("deletedAt", entity.getDeletedAt() == null ? "" : entity.getDeletedAt().toString())
        );
    }

    private static Map<String, Object> toRefItem(FileRefEntity ref) {
        long id = ref.getId() == null ? 0L : ref.getId();
        return Map.of(
                "id", id,
                "refType", ref.getRefType(),
                "refId", ref.getRefId(),
                "createdAt", ref.getCreatedAt() == null ? "" : ref.getCreatedAt().toString()
        );
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toLowerCase(java.util.Locale.ROOT);
    }

    private static ApiResponse<?> requireAdmin(UUID userId, String role) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "permission denied");
        return null;
    }
}
