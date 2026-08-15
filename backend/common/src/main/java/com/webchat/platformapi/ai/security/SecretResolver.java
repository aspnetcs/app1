package com.webchat.platformapi.ai.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SecretResolver {
    private static final Logger log = Logger.getLogger(SecretResolver.class.getName());
    private SecretResolver() {}

    static String valueOrFileContents(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;

        try {
            Path p = Path.of(trimmed);
            if (Files.isRegularFile(p)) {
                return Files.readString(p, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.log(Level.FINE, "[secret] not a file path: " + trimmed, e);
        }

        return trimmed;
    }
}

