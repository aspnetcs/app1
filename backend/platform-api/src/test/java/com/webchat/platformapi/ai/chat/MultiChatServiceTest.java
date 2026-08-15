package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiChatServiceTest {

    @Test
    void serviceExposesRuntimeDiscussionSnapshot() {
        MultiChatRuntimeConfigService runtimeConfigService = mock(MultiChatRuntimeConfigService.class);
        when(runtimeConfigService.snapshot()).thenReturn(new MultiChatRuntimeConfigService.Snapshot(
                true,
                4,
                true,
                6,
                18
        ));

        MultiChatService service = new MultiChatService(
                mock(AiChatService.class),
                mock(AgentService.class),
                runtimeConfigService,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                new ChatStreamContextRegistry(),
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class)
        );

        assertThat(service.isParallelEnabled()).isTrue();
        assertThat(service.getParallelMaxModels()).isEqualTo(4);
        assertThat(service.isMultiAgentDiscussionEnabled()).isTrue();
        assertThat(service.getMultiAgentDiscussionMaxAgents()).isEqualTo(6);
        assertThat(service.getMultiAgentDiscussionMaxRounds()).isEqualTo(18);
    }

    @Test
    void buildNextDiscussionTurnUsesCompletedAssistantHistoryForRoundLimit() {
        MultiChatRuntimeConfigService runtimeConfigService = mock(MultiChatRuntimeConfigService.class);
        when(runtimeConfigService.snapshot()).thenReturn(new MultiChatRuntimeConfigService.Snapshot(
                true,
                4,
                true,
                6,
                1
        ));

        MultiChatService service = new MultiChatService(
                mock(AiChatService.class),
                mock(AgentService.class),
                runtimeConfigService,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                new ChatStreamContextRegistry(),
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class)
        );

        MultiChatService.DiscussionSession session = new MultiChatService.DiscussionSession(
                "session-1",
                "Topic",
                List.of(
                        new MultiChatService.DiscussionAgent(UUID.randomUUID(), "Architect", "a.png", "sys-a", "gpt-4o"),
                        new MultiChatService.DiscussionAgent(UUID.randomUUID(), "Reviewer", "b.png", "sys-b", "gpt-4o-mini")
                ),
                0,
                1,
                List.of("kb-1")
        );

        assertThatThrownBy(() -> service.buildNextDiscussionTurn(
                UUID.randomUUID(),
                "trace_test_1",
                session,
                0,
                List.of(Map.of("role", "assistant", "content", "completed turn"))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("max rounds reached");
    }

    @Test
    void buildNextDiscussionTurnAppliesSkillAndKnowledgeContext() {
        MultiChatRuntimeConfigService runtimeConfigService = mock(MultiChatRuntimeConfigService.class);
        when(runtimeConfigService.snapshot()).thenReturn(new MultiChatRuntimeConfigService.Snapshot(
                true,
                4,
                true,
                6,
                2
        ));
        ChatSkillContextService skillContextService = mock(ChatSkillContextService.class);
        ChatKnowledgeContextService knowledgeContextService = mock(ChatKnowledgeContextService.class);
        MultiChatService service = new MultiChatService(
                mock(AiChatService.class),
                mock(AgentService.class),
                runtimeConfigService,
                mock(StringRedisTemplate.class),
                new ObjectMapper(),
                new ChatStreamContextRegistry(),
                skillContextService,
                knowledgeContextService
        );

        UUID userId = UUID.randomUUID();
        MultiChatService.DiscussionSession session = new MultiChatService.DiscussionSession(
                "session-1",
                "Topic",
                List.of(new MultiChatService.DiscussionAgent(UUID.randomUUID(), "Architect", "a.png", "sys-a", "gpt-4o")),
                0,
                2,
                List.of("kb-1")
        );

        ChatStreamTask task = service.buildNextDiscussionTurn(
                userId,
                "trace_test_2",
                session,
                0,
                List.of(Map.of("role", "user", "content", "hello"))
        );

        assertThat(task.requestBody()).containsEntry("model", "gpt-4o");
        verify(skillContextService).applySavedSkillContracts(userId, task.requestBody());
        verify(knowledgeContextService).applyKnowledgeContext(userId, task.requestBody());
    }

    @Test
    void discussionSessionsPersistInRedisAcrossLookups() {
        MultiChatRuntimeConfigService runtimeConfigService = mock(MultiChatRuntimeConfigService.class);
        when(runtimeConfigService.snapshot()).thenReturn(new MultiChatRuntimeConfigService.Snapshot(
                true,
                4,
                true,
                4,
                3
        ));
        AgentService agentService = mock(AgentService.class);
        UUID agentA = UUID.randomUUID();
        UUID agentB = UUID.randomUUID();
        when(agentService.loadAgents(List.of(agentA, agentB))).thenReturn(List.of(
                agent(agentA, "Architect", "gpt-4o"),
                agent(agentB, "Reviewer", "gpt-4o-mini")
        ));

        MultiChatService service = new MultiChatService(
                mock(AiChatService.class),
                agentService,
                runtimeConfigService,
                createRedisStub(),
                new ObjectMapper(),
                new ChatStreamContextRegistry(),
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class)
        );

        UUID userId = UUID.randomUUID();
        MultiChatService.DiscussionSession session = service.startMultiAgentDiscussion(
                userId,
                List.of(agentA, agentB),
                "Debate the roadmap",
                List.of("kb-1")
        );

        MultiChatService.DiscussionSession loaded = service.getDiscussionSessionForUser(session.sessionId(), userId);

        assertThat(loaded.sessionId()).isEqualTo(session.sessionId());
        assertThat(loaded.agents()).hasSize(2);
        assertThat(loaded.agents().get(0).modelId()).isEqualTo("gpt-4o");
        assertThat(loaded.knowledgeBaseIds()).containsExactly("kb-1");
    }

    @Test
    void completingTheFinalDiscussionRoundRemovesThePersistedSession() {
        MultiChatRuntimeConfigService runtimeConfigService = mock(MultiChatRuntimeConfigService.class);
        when(runtimeConfigService.snapshot()).thenReturn(new MultiChatRuntimeConfigService.Snapshot(
                true,
                4,
                true,
                4,
                1
        ));
        AgentService agentService = mock(AgentService.class);
        UUID agentA = UUID.randomUUID();
        UUID agentB = UUID.randomUUID();
        when(agentService.loadAgents(List.of(agentA, agentB))).thenReturn(List.of(
                agent(agentA, "Architect", "gpt-4o"),
                agent(agentB, "Reviewer", "gpt-4o-mini")
        ));

        MultiChatService service = new MultiChatService(
                mock(AiChatService.class),
                agentService,
                runtimeConfigService,
                createRedisStub(),
                new ObjectMapper(),
                new ChatStreamContextRegistry(),
                mock(ChatSkillContextService.class),
                mock(ChatKnowledgeContextService.class)
        );

        UUID userId = UUID.randomUUID();
        MultiChatService.DiscussionSession session = service.startMultiAgentDiscussion(
                userId,
                List.of(agentA, agentB),
                "Debate the roadmap",
                List.of()
        );

        service.completeDiscussionRound(session.sessionId(), userId);

        assertThatThrownBy(() -> service.getDiscussionSessionForUser(session.sessionId(), userId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("session not found");
    }

    private static AgentEntity agent(UUID id, String name, String modelId) {
        AgentEntity agent = new AgentEntity();
        agent.setId(id);
        agent.setName(name);
        agent.setModelId(modelId);
        return agent;
    }

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate createRedisStub() {
        Map<String, String> store = new HashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.remove(invocation.getArgument(0));
            return Boolean.TRUE;
        }).when(redis).delete(anyString());
        doAnswer(invocation -> Boolean.TRUE).when(redis).expire(anyString(), any(Duration.class));
        return redis;
    }
}
