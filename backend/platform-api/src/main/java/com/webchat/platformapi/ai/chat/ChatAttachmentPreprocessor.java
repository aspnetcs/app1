package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.model.AiModelCapabilityResolver;
import com.webchat.platformapi.ai.multimodal.MultimodalUploadProperties;
import com.webchat.platformapi.ai.texttransform.FileTextExtractorService;
import com.webchat.platformapi.asset.MinioAssetService;
import com.webchat.platformapi.file.FileEntity;
import com.webchat.platformapi.file.FileKind;
import com.webchat.platformapi.file.FileLibraryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Preprocesses chat request attachments: resolves file references, extracts
 * document text, and converts image attachments into provider-neutral content
 * parts that adapters can translate to their native format.
 */
@Service
public class ChatAttachmentPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentPreprocessor.class);

    private static final long MAX_FILE_DOWNLOAD_BYTES = 50 * 1024 * 1024; // 50 MB

    private final MultimodalUploadProperties properties;
    private final FileLibraryService fileLibrary;
    private final MinioAssetService minio;
    private final FileTextExtractorService textExtractor;
    private final AiModelCapabilityResolver capabilityResolver;

    public ChatAttachmentPreprocessor(
            MultimodalUploadProperties properties,
            FileLibraryService fileLibrary,
            MinioAssetService minio,
            FileTextExtractorService textExtractor,
            AiModelCapabilityResolver capabilityResolver
    ) {
        this.properties = properties;
        this.fileLibrary = fileLibrary;
        this.minio = minio;
        this.textExtractor = textExtractor;
        this.capabilityResolver = capabilityResolver;
    }

    /**
     * Result of preprocessing attachments for a chat request.
     */
    public record PreprocessResult(
            boolean success,
            Map<String, Object> processedRequest,
            String error
    ) {
        public static PreprocessResult ok(Map<String, Object> request) {
            return new PreprocessResult(true, request, null);
        }

        public static PreprocessResult error(String error) {
            return new PreprocessResult(false, null, error);
        }
    }

    /**
     * Process attachments in a chat request. Resolves file references,
     * validates model capabilities, extracts document text, and converts
     * images to content parts.
     */
    public PreprocessResult process(UUID userId, Map<String, Object> requestBody) {
        if (!properties.isEnabled()) {
            return removeAttachmentsAndReturn(requestBody);
        }

        Object messagesObj = requestBody.get("messages");
        if (!(messagesObj instanceof List<?> messagesList) || messagesList.isEmpty()) {
            return PreprocessResult.ok(requestBody);
        }

        // Find the last user message with attachments
        int lastUserIdx = -1;
        List<Map<String, Object>> attachments = null;
        for (int i = messagesList.size() - 1; i >= 0; i--) {
            Object item = messagesList.get(i);
            if (!(item instanceof Map<?, ?> msg)) continue;
            Object roleObj = msg.get("role");
            String role = roleObj == null ? "" : String.valueOf(roleObj).trim();
            if (!"user".equals(role)) continue;

            Object atts = msg.get("attachments");
            if (atts instanceof List<?> attList && !attList.isEmpty()) {
                lastUserIdx = i;
                attachments = new ArrayList<>();
                for (Object a : attList) {
                    if (a instanceof Map<?, ?> m) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> am = (Map<String, Object>) m;
                        attachments.add(am);
                    }
                }
                break;
            }
        }

        if (attachments == null || attachments.isEmpty()) {
            return removeAttachmentsAndReturn(requestBody);
        }

        // Separate images and documents
        List<Map<String, Object>> imageAtts = new ArrayList<>();
        List<Map<String, Object>> docAtts = new ArrayList<>();
        for (Map<String, Object> att : attachments) {
            String kind = String.valueOf(att.getOrDefault("kind", "")).trim();
            if ("image".equals(kind)) {
                imageAtts.add(att);
            } else {
                docAtts.add(att);
            }
        }

        // Validate image count
        if (imageAtts.size() > properties.getMaxImagesPerMessage()) {
            return PreprocessResult.error("too many images: max " + properties.getMaxImagesPerMessage());
        }

        // Validate model image capability
        String model = String.valueOf(requestBody.getOrDefault("model", "")).trim();
        if (!imageAtts.isEmpty()) {
            var capability = capabilityResolver.resolve(model, null);
            if (!capability.supportsImageParsing()) {
                return PreprocessResult.error("model " + model + " does not support image parsing");
            }
        }

        // Build the processed message content
        Map<String, Object> result = new LinkedHashMap<>(requestBody);
        List<Object> newMessages = new ArrayList<>(messagesList);

        @SuppressWarnings("unchecked")
        Map<String, Object> originalUserMsg = (Map<String, Object>) newMessages.get(lastUserIdx);
        Map<String, Object> newUserMsg = new LinkedHashMap<>(originalUserMsg);
        newUserMsg.remove("attachments");

        // Build content parts for the user message
        Object originalContent = newUserMsg.get("content");
        String textContent = originalContent instanceof String s ? s : String.valueOf(originalContent);

        // Process document attachments - extract text and prepend
        StringBuilder docContext = new StringBuilder();
        for (Map<String, Object> att : docAtts) {
            String fileId = String.valueOf(att.getOrDefault("fileId", "")).trim();
            if (fileId.isEmpty()) continue;

            FileEntity file = fileLibrary.getUserFileOrNull(userId, fileId);
            if (file == null) {
                return PreprocessResult.error("attachment file not found: " + fileId);
            }
            String normalizedMimeType = FileKind.normalizeMimeType(file.getMimeType(), file.getOriginalName());
            if (!properties.isMimeTypeAllowed(normalizedMimeType)) {
                return PreprocessResult.error("unsupported file type: " + normalizedMimeType);
            }

            try {
                byte[] bytes = minio.downloadBytes(file.getObjectKey(), MAX_FILE_DOWNLOAD_BYTES);
                var extractResult = textExtractor.extract(bytes, file.getOriginalName(), normalizedMimeType);
                if (!extractResult.success()) {
                    return PreprocessResult.error("failed to extract text from " + file.getOriginalName() + ": " + extractResult.error());
                }
                if (!docContext.isEmpty()) docContext.append("\n\n");
                docContext.append("[Attached document: ").append(file.getOriginalName()).append("]\n");
                docContext.append(extractResult.text());
            } catch (Exception e) {
                log.warn("[chat-attachment] download/extract failed for fileId={}: {}", fileId, e.getMessage());
                return PreprocessResult.error("failed to process attachment: " + file.getOriginalName());
            }
        }

        // If we have document context, prepend it to the user message text
        if (!docContext.isEmpty()) {
            textContent = docContext + "\n\n" + textContent;
        }

        // Build content array with text and image parts
        if (!imageAtts.isEmpty()) {
            List<Map<String, Object>> contentParts = new ArrayList<>();
            // Add text part first
            contentParts.add(Map.of("type", "text", "text", textContent));

            // Add image parts
            for (Map<String, Object> att : imageAtts) {
                String fileId = String.valueOf(att.getOrDefault("fileId", "")).trim();
                if (fileId.isEmpty()) continue;

                FileEntity file = fileLibrary.getUserFileOrNull(userId, fileId);
                if (file == null) {
                    return PreprocessResult.error("image file not found: " + fileId);
                }
                String normalizedMimeType = FileKind.normalizeMimeType(file.getMimeType(), file.getOriginalName());
                if (!properties.isImageMimeType(normalizedMimeType)) {
                    return PreprocessResult.error("not an image: " + file.getOriginalName());
                }

                // Generate presigned URL for the image
                try {
                    Map<String, Object> urlInfo = fileLibrary.presignUrl(file);
                    String imageUrl = String.valueOf(urlInfo.get("url"));
                    contentParts.add(Map.of(
                            "type", "image_url",
                            "image_url", Map.of("url", imageUrl)
                    ));
                } catch (Exception e) {
                    log.warn("[chat-attachment] presign failed for fileId={}: {}", fileId, e.getMessage());
                    return PreprocessResult.error("failed to resolve image: " + file.getOriginalName());
                }
            }
            newUserMsg.put("content", contentParts);
        } else {
            newUserMsg.put("content", textContent);
        }

        newMessages.set(lastUserIdx, newUserMsg);
        result.put("messages", newMessages);
        result.remove("attachments");
        return PreprocessResult.ok(result);
    }

    private static PreprocessResult removeAttachmentsAndReturn(Map<String, Object> requestBody) {
        // Strip attachments from all messages silently
        Object messagesObj = requestBody.get("messages");
        if (messagesObj instanceof List<?> messagesList) {
            List<Object> cleaned = new ArrayList<>();
            for (Object item : messagesList) {
                if (item instanceof Map<?, ?> msg) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) msg);
                    m.remove("attachments");
                    cleaned.add(m);
                } else {
                    cleaned.add(item);
                }
            }
            Map<String, Object> result = new LinkedHashMap<>(requestBody);
            result.put("messages", cleaned);
            return PreprocessResult.ok(result);
        }
        return PreprocessResult.ok(requestBody);
    }
}
