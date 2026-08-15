package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatMessagePreparerTest {

    @Test
    void injectSystemPromptPrependsCombinedChannelAndSkillPrompt() {
        ChatMessagePreparer preparer = new ChatMessagePreparer();
        AiChannelEntity channel = mock(AiChannelEntity.class);
        when(channel.getExtraConfig()).thenReturn(Map.of("system_prompt", "channel prompt"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", new ArrayList<>(List.of(Map.of("role", "user", "content", "hello"))));
        request.put(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY, "skill contracts");

        preparer.injectSystemPrompt(request, channel);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(0).get("content")).isEqualTo("channel prompt\n\nskill contracts");
        assertThat(request).doesNotContainKey(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY);
    }

    @Test
    void injectSystemPromptMergesIntoExistingSystemMessage() {
        ChatMessagePreparer preparer = new ChatMessagePreparer();
        AiChannelEntity channel = mock(AiChannelEntity.class);
        when(channel.getExtraConfig()).thenReturn(Map.of("system_prompt", "channel prompt"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", new ArrayList<>(List.of(
                Map.of("role", "system", "content", "existing system"),
                Map.of("role", "user", "content", "hello")
        )));
        request.put(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY, "skill contracts");

        preparer.injectSystemPrompt(request, channel);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("content"))
                .isEqualTo("channel prompt\n\nskill contracts\n\nexisting system");
    }
}
