package com.webchat.platformapi.file;

import com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties;
import com.webchat.platformapi.ai.texttransform.FileTextExtractorService;
import com.webchat.platformapi.asset.MinioAssetService;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileRecognitionController {

    private static final long MAX_FILE_DOWNLOAD_BYTES = 50L * 1024L * 1024L;
    private static final int DEFAULT_PREVIEW_CHARS = 8_000;

    private final FileLibraryService fileLibraryService;
    private final MinioAssetService minioAssetService;
    private final FileTextExtractorService textExtractorService;
    private final MultimodalUploadProperties multimodalUploadProperties;
    private final boolean filesEnabled;

    public FileRecognitionController(
            FileLibraryService fileLibraryService,
            MinioAssetService minioAssetService,
            FileTextExtractorService textExtractorService,
            MultimodalUploadProperties multimodalUploadProperties,
            @Value("${platform.files.enabled:false}") boolean filesEnabled
    ) {
        this.fileLibraryService = fileLibraryService;
        this.minioAssetService = minioAssetService;
        this.textExtractorService = textExtractorService;
        this.multimodalUploadProperties = multimodalUploadProperties;
        this.filesEnabled = filesEnabled;
    }

    @GetMapping("/{fileId}/recognition")
    public ApiResponse<Map<String, Object>> recognize(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("fileId") String fileId,
            @RequestParam(value = "maxChars", required = false) Integer maxChars
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (!filesEnabled) return ApiResponse.error(ErrorCodes.SERVER_ERROR, "files disabled");

        FileEntity entity = fileLibraryService.getUserFileOrNull(userId, fileId);
        if (entity == null) return ApiResponse.error(ErrorCodes.PARAM_MISSING, "file not found");

        String normalizedMimeType = FileKind.normalizeMimeType(entity.getMimeType(), entity.getOriginalName());
        String kind = FileKind.fromMimeTypeOrName(normalizedMimeType, entity.getOriginalName());

        Map<String, Object> out = basePayload(entity, normalizedMimeType, kind);
        if (FileKind.IMAGE.equals(kind)) {
            try {
                Map<String, Object> urlInfo = fileLibraryService.presignUrl(entity);
                out.put("supported", true);
                out.put("recognitionType", "image");
                out.put("status", "vision_ready");
                out.put("previewUrl", urlInfo.get("url"));
                out.put("expiresIn", urlInfo.get("expiresIn"));
                out.put("text", "");
                out.put("message", "image can be recognized by a vision-capable model in chat");
                return ApiResponse.ok(out);
            } catch (Exception e) {
                return ApiResponse.error(ErrorCodes.SERVER_ERROR, "failed to create image preview url");
            }
        }

        if (!FileKind.DOCUMENT.equals(kind)) {
            out.put("supported", false);
            out.put("recognitionType", kind);
            out.put("status", "unsupported");
            out.put("text", "");
            return ApiResponse.ok(out);
        }

        if (!multimodalUploadProperties.isMimeTypeAllowed(normalizedMimeType)) {
            out.put("supported", false);
            out.put("recognitionType", "document");
            out.put("status", "unsupported_mime");
            out.put("text", "");
            out.put("message", "unsupported file type: " + normalizedMimeType);
            return ApiResponse.ok(out);
        }

        try {
            byte[] bytes = minioAssetService.downloadBytes(entity.getObjectKey(), MAX_FILE_DOWNLOAD_BYTES);
            FileTextExtractorService.ExtractResult extracted =
                    textExtractorService.extract(bytes, entity.getOriginalName(), normalizedMimeType);
            if (!extracted.success()) {
                out.put("supported", false);
                out.put("recognitionType", "document");
                out.put("status", "extract_failed");
                out.put("text", "");
                out.put("message", extracted.error());
                return ApiResponse.ok(out);
            }

            String text = extracted.text() == null ? "" : extracted.text();
            int limit = resolvePreviewLimit(maxChars);
            boolean truncated = text.length() > limit;
            out.put("supported", true);
            out.put("recognitionType", "document");
            out.put("status", "extracted");
            out.put("text", truncated ? text.substring(0, limit) : text);
            out.put("charCount", extracted.charCount());
            out.put("truncated", truncated);
            return ApiResponse.ok(out);
        } catch (Exception e) {
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "failed to recognize file");
        }
    }

    private int resolvePreviewLimit(Integer maxChars) {
        int requested = maxChars == null ? DEFAULT_PREVIEW_CHARS : maxChars;
        int bounded = Math.max(500, Math.min(requested, multimodalUploadProperties.getMaxDocumentChars()));
        return Math.min(bounded, 50_000);
    }

    private static Map<String, Object> basePayload(FileEntity entity, String mimeType, String kind) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileId", entity.getId());
        out.put("originalName", entity.getOriginalName());
        out.put("sizeBytes", entity.getSizeBytes());
        out.put("size", entity.getSizeBytes());
        out.put("mimeType", mimeType);
        out.put("kind", kind);
        return out;
    }
}
