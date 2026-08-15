package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.AiChannelEntity;

import java.util.Map;

final class ChatMessagePreparer {

    void injectSystemPrompt(Map<String, Object> req, AiChannelEntity channel) {
        ChatSystemPromptSupport.applySystemPrompts(req, ChatSystemPromptSupport.resolveChannelSystemPrompt(channel));
    }
}
