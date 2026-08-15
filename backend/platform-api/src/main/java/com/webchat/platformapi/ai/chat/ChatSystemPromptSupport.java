package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.AiChannelEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ChatSystemPromptSupport {

    static final String CONTEXT_SYSTEM_PROMPT_KEY = "__contextSystemPrompt";

    private ChatSystemPromptSupport() {
    }

    @SuppressWarnings("unchecked")
    static void applySystemPrompts(Map<String, Object> requestBody, String channelSystemPrompt) {
        if (requestBody == null) {
            return;
        }

        String contextSystemPrompt = pullPrompt(requestBody, CONTEXT_SYSTEM_PROMPT_KEY);
        String combinedSystemPrompt = joinPrompts(channelSystemPrompt, contextSystemPrompt);
        if (combinedSystemPrompt == null) {
            return;
        }

        Object messagesObject = requestBody.get("messages");
        if (!(messagesObject instanceof List<?> messagesList)) {
            return;
        }

        List<Object> newMessages = new ArrayList<>((List<Object>) messagesList);
        if (!newMessages.isEmpty()) {
            Object first = newMessages.get(0);
            if (first instanceof Map<?, ?> firstMap && "system".equals(firstMap.get("role"))) {
                String existingPrompt = normalizePrompt(firstMap.get("content"));
                if (existingPrompt != null) {
                    LinkedHashMap<String, Object> updatedFirstMessage = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : firstMap.entrySet()) {
                        if (entry.getKey() != null) {
                            updatedFirstMessage.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    updatedFirstMessage.put("content", joinPrompts(combinedSystemPrompt, existingPrompt));
                    newMessages.set(0, updatedFirstMessage);
                    requestBody.put("messages", newMessages);
                    return;
                }
            }
        }

        newMessages.add(0, Map.of("role", "system", "content", combinedSystemPrompt));
        requestBody.put("messages", newMessages);
    }

    static String resolveChannelSystemPrompt(AiChannelEntity channel) {
        if (channel == null) {
            return null;
        }
        Map<String, Object> extra = channel.getExtraConfig();
        if (extra == null) {
            return null;
        }
        return normalizePrompt(extra.get("system_prompt"));
    }

    static String joinPrompts(String... prompts) {
        StringBuilder builder = new StringBuilder();
        for (String prompt : prompts) {
            String normalized = normalizePrompt(prompt);
            if (normalized == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(normalized);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    static String normalizePrompt(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    private static String pullPrompt(Map<String, Object> requestBody, String key) {
        if (requestBody == null || key == null) {
            return null;
        }
        return normalizePrompt(requestBody.remove(key));
    }
}
