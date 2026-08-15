package com.webchat.platformapi.file;

import java.util.Locale;
import java.util.Map;

public final class FileKind {
    private FileKind() {}

    public static final String IMAGE = "image";
    public static final String DOCUMENT = "document";
    public static final String AUDIO = "audio";
    public static final String VIDEO = "video";
    public static final String OTHER = "other";

    private static final Map<String, String> MIME_BY_EXTENSION = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("md", "text/markdown"),
            Map.entry("markdown", "text/markdown"),
            Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"),
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("xml", "application/xml"),
            Map.entry("rtf", "application/rtf")
    );

    public static String fromMimeType(String mimeType) {
        String t = normalizeMimeType(mimeType);
        if (t.isEmpty()) return OTHER;
        if (t.startsWith("image/")) return IMAGE;
        if (t.startsWith("audio/")) return AUDIO;
        if (t.startsWith("video/")) return VIDEO;
        if (t.startsWith("text/")) return DOCUMENT;
        if (t.equals("application/pdf")) return DOCUMENT;
        if (t.equals("application/json") || t.equals("application/xml") || t.equals("application/rtf")) return DOCUMENT;
        if (t.contains("msword")) return DOCUMENT;
        if (t.contains("ms-excel")) return DOCUMENT;
        if (t.contains("ms-powerpoint")) return DOCUMENT;
        if (t.contains("officedocument")) return DOCUMENT;
        return OTHER;
    }

    public static String fromMimeTypeOrName(String mimeType, String filename) {
        String byMime = fromMimeType(mimeType);
        if (!OTHER.equals(byMime)) return byMime;
        return fromFilename(filename);
    }

    public static String fromFilename(String filename) {
        String mime = mimeTypeFromFilename(filename);
        return fromMimeType(mime);
    }

    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null) return "";
        String t = mimeType.trim().toLowerCase(Locale.ROOT);
        int semi = t.indexOf(';');
        if (semi >= 0) t = t.substring(0, semi).trim();
        return t;
    }

    public static String normalizeMimeType(String mimeType, String filename) {
        String normalized = normalizeMimeType(mimeType);
        if (normalized.isEmpty() || "application/octet-stream".equals(normalized) || "binary/octet-stream".equals(normalized)) {
            String fromName = mimeTypeFromFilename(filename);
            if (!fromName.isEmpty()) return fromName;
        }
        return normalized.isEmpty() ? "application/octet-stream" : normalized;
    }

    public static String mimeTypeFromFilename(String filename) {
        if (filename == null || filename.isBlank()) return "";
        String name = filename.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        return MIME_BY_EXTENSION.getOrDefault(name.substring(dot + 1), "");
    }
}

