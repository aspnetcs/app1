package com.webchat.platformapi.ai.multimodal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@ConfigurationProperties(prefix = "platform.multimodal-upload")
public class MultimodalUploadProperties {

    private boolean enabled = false;
    private int maxImagesPerMessage = 4;
    private int maxDocumentChars = 50_000;
    private List<String> allowedMimeTypes = List.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/svg+xml",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/json",
            "application/xml",
            "application/rtf",
            "text/plain",
            "text/markdown",
            "text/csv",
            "text/html",
            "text/xml",
            "text/rtf"
    );

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxImagesPerMessage() { return maxImagesPerMessage; }
    public void setMaxImagesPerMessage(int maxImagesPerMessage) { this.maxImagesPerMessage = maxImagesPerMessage; }

    public int getMaxDocumentChars() { return maxDocumentChars; }
    public void setMaxDocumentChars(int maxDocumentChars) { this.maxDocumentChars = maxDocumentChars; }

    public List<String> getAllowedMimeTypes() { return allowedMimeTypes; }
    public void setAllowedMimeTypes(List<String> allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }

    public boolean isMimeTypeAllowed(String mimeType) {
        if (mimeType == null) return false;
        String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
        int semi = normalized.indexOf(';');
        if (semi >= 0) normalized = normalized.substring(0, semi).trim();
        if (normalized.startsWith("text/")) return true;
        return allowedMimeTypes.contains(normalized);
    }

    public boolean isImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
