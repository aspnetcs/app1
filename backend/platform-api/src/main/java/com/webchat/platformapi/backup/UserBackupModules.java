package com.webchat.platformapi.backup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class UserBackupModules {

    private UserBackupModules() {
    }

    static final List<String> SUPPORTED = List.of(
            "conversations",
            "messages",
            "messageBlocks"
    );

    static List<String> parse(String modules) {
        if (modules == null || modules.isBlank()) {
            return List.of("conversations", "messages");
        }

        Set<String> out = new LinkedHashSet<>();
        for (String raw : modules.split(",")) {
            if (raw == null) continue;
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if (SUPPORTED.contains(normalized)) {
                out.add(normalized);
            }
        }
        if (out.isEmpty()) {
            return List.of("conversations", "messages");
        }
        return new ArrayList<>(out);
    }
}

