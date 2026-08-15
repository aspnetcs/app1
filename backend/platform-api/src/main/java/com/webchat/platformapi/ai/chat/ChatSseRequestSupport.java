package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
final class ChatSseRequestSupport {

    Map<String, Object> prepareStreamRequestBody(Map<String, Object> body, String defaultModel) {
        Map<String, Object> requestBody = new HashMap<>();
        if (body != null) {
            requestBody.putAll(body);
        }
        requestBody.put("stream", true);
        if (hasModel(requestBody)) {
            return requestBody;
        }
        if (defaultModel != null && !defaultModel.isBlank()) {
            requestBody.put("model", defaultModel);
            return requestBody;
        }
        throw new IllegalStateException("model is required");
    }

    String resolveModel(Map<String, Object> requestBody) {
        Object modelValue = requestBody == null ? null : requestBody.get("model");
        if (modelValue == null) {
            return null;
        }
        String model = String.valueOf(modelValue).trim();
        return model.isEmpty() ? null : model;
    }

    Map<String, Object> buildUpstreamRequestBody(Map<String, Object> requestBody, ChannelSelection selection) {
        Map<String, Object> request = new HashMap<>();
        if (requestBody != null) {
            request.putAll(requestBody);
        }
        request.put("stream", true);
        if (selection != null && selection.actualModel() != null && !selection.actualModel().isBlank()) {
            request.put("model", selection.actualModel());
        }
        injectSystemPrompt(request, selection == null ? null : selection.channel());
        return request;
    }

    void applyStreamingQuotaLimit(Map<String, Object> requestBody, long reservedTokens) {
        if (requestBody == null || reservedTokens <= 0) {
            return;
        }
        int maxAllowed = reservedTokens > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) reservedTokens;
        Integer maxCompletionTokens = readPositiveInt(requestBody.get("max_completion_tokens"));
        if (maxCompletionTokens != null) {
            requestBody.put("max_completion_tokens", Math.min(maxCompletionTokens, maxAllowed));
            return;
        }
        Integer maxTokens = readPositiveInt(requestBody.get("max_tokens"));
        if (maxTokens != null) {
            requestBody.put("max_tokens", Math.min(maxTokens, maxAllowed));
            return;
        }
        requestBody.put("max_tokens", maxAllowed);
    }

    private static void injectSystemPrompt(Map<String, Object> requestBody, AiChannelEntity channel) {
        ChatSystemPromptSupport.applySystemPrompts(requestBody, ChatSystemPromptSupport.resolveChannelSystemPrompt(channel));
    }

    private static Integer readPositiveInt(Object value) {
        if (value instanceof Number number) {
            int parsed = number.intValue();
            return parsed > 0 ? parsed : null;
        }
        if (value instanceof String text) {
            try {
                int parsed = Integer.parseInt(text.trim());
                return parsed > 0 ? parsed : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasModel(Map<String, Object> requestBody) {
        if (requestBody == null) {
            return false;
        }
        Object value = requestBody.get("model");
        return value != null && !String.valueOf(value).trim().isEmpty();
    }
}
