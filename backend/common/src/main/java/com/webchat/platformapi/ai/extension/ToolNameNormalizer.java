package com.webchat.platformapi.ai.extension;

import java.util.List;
import java.util.Locale;

final class ToolNameNormalizer {

    private ToolNameNormalizer() {
    }

    static List<String> normalize(List<String> requestedToolNames) {
        if (requestedToolNames == null) {
            return List.of();
        }
        return requestedToolNames.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
