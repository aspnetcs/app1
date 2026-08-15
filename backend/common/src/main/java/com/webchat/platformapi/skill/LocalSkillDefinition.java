package com.webchat.platformapi.skill;

import java.util.Map;

public record LocalSkillDefinition(
        String sourceId,
        String name,
        String description,
        String absolutePath,
        String relativePath,
        String entryFile,
        String content,
        String contentPreview,
        long contentBytes,
        String usageMode,
        String aiUsageInstruction,
        Map<String, String> metadata
) {
}
