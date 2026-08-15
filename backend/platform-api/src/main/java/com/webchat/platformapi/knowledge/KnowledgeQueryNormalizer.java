package com.webchat.platformapi.knowledge;

public final class KnowledgeQueryNormalizer {

    private KnowledgeQueryNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        normalized = normalized.replace('\u3000', ' ');
        normalized = normalized.replaceAll("\\s+", " ").trim();
        normalized = stripKnownPrefix(normalized);
        normalized = stripKnownSuffix(normalized);
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? raw.trim() : normalized;
    }

    private static String stripKnownPrefix(String text) {
        String normalized = text;
        String[] prefixPatterns = {
                "^(?:请)?基于已选知识库回答[:：]\\s*",
                "^(?:请)?根据已选知识库回答[:：]\\s*",
                "^(?:请)?基于知识库回答[:：]\\s*",
                "^(?:请)?根据知识库回答[:：]\\s*",
                "^(?:please\\s+)?answer\\s+based\\s+on\\s+the\\s+selected\\s+knowledge\\s+base[:：]?\\s*",
                "^(?:please\\s+)?answer\\s+from\\s+the\\s+selected\\s+knowledge\\s+base[:：]?\\s*",
                "^(?:please\\s+)?according\\s+to\\s+the\\s+selected\\s+knowledge\\s+base[:：]?\\s*"
        };
        for (String pattern : prefixPatterns) {
            normalized = normalized.replaceFirst("(?i)" + pattern, "");
        }
        return normalized;
    }

    private static String stripKnownSuffix(String text) {
        String normalized = text;
        String[] suffixPatterns = {
                "\\s*(?:如果|若)信息不足请明确指出[。.!?？]*$",
                "\\s*(?:如果|若)信息不够请明确指出[。.!?？]*$",
                "\\s*if\\s+information\\s+is\\s+insufficient[, ]*(?:please\\s+)?(?:say\\s+so|state\\s+it\\s+clearly)[.?!]*$",
                "\\s*if\\s+the\\s+information\\s+is\\s+insufficient[, ]*(?:please\\s+)?(?:say\\s+so|state\\s+it\\s+clearly)[.?!]*$"
        };
        for (String pattern : suffixPatterns) {
            normalized = normalized.replaceFirst("(?i)" + pattern, "");
        }
        return normalized;
    }
}
