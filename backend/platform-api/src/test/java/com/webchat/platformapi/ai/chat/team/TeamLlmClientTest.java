package com.webchat.platformapi.ai.chat.team;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ai.chat.ChatKnowledgeContextService;
import com.webchat.platformapi.ai.chat.ChatSkillContextService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.ai.usage.AiUsageService;
import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamLlmClientTest {

    @Test
    void parseResponseReadsOpenAiUsage() {
        TeamLlmClient client = client();

        TeamLlmClient.TeamLlmResult result = client.parseResponse(
                """
                {
                  "choices":[{"message":{"content":"hello"}}],
                  "usage":{"prompt_tokens":12,"completion_tokens":34,"total_tokens":46}
                }
                """,
                1L,
                "openai",
                "gpt-4o",
                55L
        );

        assertThat(result.content()).isEqualTo("hello");
        assertThat(result.promptTokens()).isEqualTo(12);
        assertThat(result.completionTokens()).isEqualTo(34);
        assertThat(result.channelId()).isEqualTo(1L);
        assertThat(result.channelType()).isEqualTo("openai");
        assertThat(result.model()).isEqualTo("gpt-4o");
        assertThat(result.latencyMs()).isEqualTo(55L);
    }

    @Test
    void parseResponseReadsGeminiUsageMetadata() {
        TeamLlmClient client = client();

        TeamLlmClient.TeamLlmResult result = client.parseResponse(
                """
                {
                  "candidates":[{"content":{"parts":[{"text":"hi"}]}}],
                  "usageMetadata":{"promptTokenCount":9,"candidatesTokenCount":6,"totalTokenCount":15}
                }
                """,
                2L,
                "gemini",
                "gemini-2.5",
                21L
        );

        assertThat(result.content()).isEqualTo("hi");
        assertThat(result.promptTokens()).isEqualTo(9);
        assertThat(result.completionTokens()).isEqualTo(6);
    }

    @Test
    void parseResponseReadsAnthropicUsageShape() {
        TeamLlmClient client = client();

        TeamLlmClient.TeamLlmResult result = client.parseResponse(
                """
                {
                  "content":"summary",
                  "usage":{"input_tokens":7,"output_tokens":5}
                }
                """,
                3L,
                "anthropic",
                "claude-sonnet",
                33L
        );

        assertThat(result.content()).isEqualTo("summary");
        assertThat(result.promptTokens()).isEqualTo(7);
        assertThat(result.completionTokens()).isEqualTo(5);
    }

    @Test
    void prepareRequestBodyAppliesSkillAndKnowledgeContext() {
        ChatSkillContextService skillContextService = mock(ChatSkillContextService.class);
        ChatKnowledgeContextService knowledgeContextService = mock(ChatKnowledgeContextService.class);
        TeamLlmClient client = client(skillContextService, knowledgeContextService);
        UUID userId = UUID.randomUUID();

        Map<String, Object> body = client.prepareRequestBody(
                userId,
                "gpt-4o",
                "system",
                "question",
                4096,
                0.3,
                List.of("kb-1", "kb-2")
        );

        assertThat(body.get("knowledgeBaseIds")).isEqualTo(List.of("kb-1", "kb-2"));
        verify(skillContextService).applySavedSkillContracts(userId, body);
        verify(knowledgeContextService).applyKnowledgeContext(userId, body);
    }

    private TeamLlmClient client() {
        return client(mock(ChatSkillContextService.class), mock(ChatKnowledgeContextService.class));
    }

    private TeamLlmClient client(ChatSkillContextService skillContextService, ChatKnowledgeContextService knowledgeContextService) {
        return new TeamLlmClient(
                mock(AdapterFactory.class),
                mock(ChannelRouter.class),
                mock(AiCryptoService.class),
                mock(SsrfGuard.class),
                mock(AiUsageService.class),
                skillContextService,
                knowledgeContextService,
                HttpClient.newHttpClient(),
                new ObjectMapper()
        );
    }
}
