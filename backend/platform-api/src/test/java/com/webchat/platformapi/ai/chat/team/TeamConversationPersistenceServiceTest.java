package com.webchat.platformapi.ai.chat.team;

import com.webchat.platformapi.ai.chat.team.dto.CaptainSelectionMode;
import com.webchat.platformapi.ai.chat.team.dto.CaptainSource;
import com.webchat.platformapi.ai.chat.team.dto.DebateTurnSession;
import com.webchat.platformapi.ai.chat.team.dto.TeamConversationContext;
import com.webchat.platformapi.ai.conversation.AiConversationEntity;
import com.webchat.platformapi.ai.conversation.AiConversationRepository;
import com.webchat.platformapi.ai.conversation.AiMessageEntity;
import com.webchat.platformapi.ai.conversation.AiMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamConversationPersistenceServiceTest {

    @Mock
    private AiConversationRepository conversationRepository;

    @Mock
    private AiMessageRepository messageRepository;

    @Test
    void ensureConversationRecordCreatesTeamConversationWhenMissing() {
        TeamConversationPersistenceService service = new TeamConversationPersistenceService(
                conversationRepository,
                messageRepository
        );
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.ensureConversationRecord(
                conversationId.toString(),
                userId.toString(),
                List.of("model-a", "model-b"),
                CaptainSelectionMode.FIXED_FIRST,
                "Team kickoff"
        );

        ArgumentCaptor<AiConversationEntity> savedConversation = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository).save(savedConversation.capture());
        AiConversationEntity entity = savedConversation.getValue();
        assertThat(entity.getId()).isEqualTo(conversationId);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getTitle()).isEqualTo("Team kickoff");
        assertThat(entity.getMode()).isEqualTo("team");
        assertThat(entity.getCaptainSelectionMode()).isEqualTo("fixed_first");
        assertThat(entity.getCompareModelsJson()).contains("model-a");
        assertThat(entity.getCompareModelsJson()).contains("model-b");
    }

    @Test
    void persistCompletedTurnStoresUserAndAssistantMessagesWithSharedMultiRoundId() {
        TeamConversationPersistenceService service = new TeamConversationPersistenceService(
                conversationRepository,
                messageRepository
        );
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();

        AiConversationEntity existing = new AiConversationEntity();
        existing.setId(conversationId);
        existing.setUserId(userId);
        existing.setMode("team");
        existing.setCompareModelsJson("[\"model-a\",\"model-b\"]");
        existing.setCaptainSelectionMode("auto");

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(existing));
        when(messageRepository.countByConversationIdAndMultiRoundId(conversationId, turnId)).thenReturn(0L);
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.save(any(AiConversationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamConversationContext context = new TeamConversationContext();
        context.setConversationId(conversationId.toString());
        context.setUserId(userId.toString());
        context.setCaptainSelectionMode(CaptainSelectionMode.AUTO);
        context.setSelectedModelIds(List.of("model-a", "model-b"));

        DebateTurnSession turn = new DebateTurnSession();
        turn.setTurnId(turnId.toString());
        turn.setConversationId(conversationId.toString());
        turn.setUserId(userId.toString());
        turn.setTurnNumber(1);
        turn.setCaptainModelId("model-a");
        turn.setCaptainSource(CaptainSource.AUTO_ELECTED);
        turn.setUserMessage("How should we ship this?");
        turn.setFinalAnswer("Ship it with gradual rollout.");
        turn.setStageTimestamps(new java.util.LinkedHashMap<>(java.util.Map.of(
                "COLLECTING", Instant.parse("2026-04-20T10:00:00Z"),
                "COMPLETED", Instant.parse("2026-04-20T10:00:12Z")
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiMessageEntity>> messageCaptor = ArgumentCaptor.forClass(List.class);

        service.persistCompletedTurn(context, turn);

        verify(messageRepository).saveAll(messageCaptor.capture());
        List<AiMessageEntity> savedMessages = messageCaptor.getValue();
        assertThat(savedMessages).hasSize(2);
        assertThat(savedMessages).extracting(AiMessageEntity::getRole).containsExactly("user", "assistant");
        assertThat(savedMessages).allSatisfy(message -> {
            assertThat(message.getConversationId()).isEqualTo(conversationId);
            assertThat(message.getMultiRoundId()).isEqualTo(turnId);
        });
        assertThat(savedMessages.get(0).getContent()).isEqualTo("How should we ship this?");
        assertThat(savedMessages.get(1).getContent()).isEqualTo("Ship it with gradual rollout.");
        assertThat(savedMessages.get(1).getModel()).isEqualTo("model-a");

        ArgumentCaptor<AiConversationEntity> conversationCaptor = ArgumentCaptor.forClass(AiConversationEntity.class);
        verify(conversationRepository, org.mockito.Mockito.atLeastOnce()).save(conversationCaptor.capture());
        AiConversationEntity savedConversation = conversationCaptor.getAllValues().get(conversationCaptor.getAllValues().size() - 1);
        assertThat(savedConversation.getTeamTurnsJson()).contains("How should we ship this?");
        assertThat(savedConversation.getTeamTurnsJson()).contains("Ship it with gradual rollout.");
        assertThat(savedConversation.getTeamTurnsJson()).contains("stageTimestamps");
        assertThat(savedConversation.getTeamTurnsJson()).contains("COLLECTING");
    }

    @Test
    void loadPersistedConversationReconstructsTurnHistory() {
        TeamConversationPersistenceService service = new TeamConversationPersistenceService(
                conversationRepository,
                messageRepository
        );
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roundOne = UUID.randomUUID();
        UUID roundTwo = UUID.randomUUID();

        AiConversationEntity existing = new AiConversationEntity();
        existing.setId(conversationId);
        existing.setUserId(userId);
        existing.setTitle("Persisted team chat");
        existing.setMode("team");
        existing.setCaptainSelectionMode("fixed_first");
        existing.setCompareModelsJson("[\"model-a\",\"model-b\"]");
        existing.setCreatedAt(Instant.parse("2026-04-20T09:00:00Z"));

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(existing));
        when(messageRepository.findByConversationIdAndParentMessageIdIsNullOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(
                        message(conversationId, "user", "Question 1", null, roundOne),
                        message(conversationId, "assistant", "Answer 1", "model-a", roundOne),
                        message(conversationId, "user", "Question 2", null, roundTwo),
                        message(conversationId, "assistant", "Answer 2", "model-b", roundTwo)
                ));

        Optional<TeamConversationPersistenceService.PersistedTeamConversationSnapshot> snapshot =
                service.loadPersistedConversation(conversationId.toString(), userId.toString());

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().completedTurns()).isEqualTo(2);
        assertThat(snapshot.orElseThrow().captainSelectionMode()).isEqualTo(CaptainSelectionMode.FIXED_FIRST);
        assertThat(snapshot.orElseThrow().modelIds()).containsExactly("model-a", "model-b");
        assertThat(snapshot.orElseThrow().turns()).hasSize(2);
        assertThat(snapshot.orElseThrow().turns().get(0).turnId()).isEqualTo(roundOne.toString());
        assertThat(snapshot.orElseThrow().turns().get(0).userQuestion()).isEqualTo("Question 1");
        assertThat(snapshot.orElseThrow().turns().get(0).finalAnswer()).isEqualTo("Answer 1");
        assertThat(snapshot.orElseThrow().turns().get(1).captainModelId()).isEqualTo("model-b");
    }

    @Test
    void loadPersistedConversationUsesStructuredTurnSnapshotsWhenPresent() {
        TeamConversationPersistenceService service = new TeamConversationPersistenceService(
                conversationRepository,
                messageRepository
        );
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiConversationEntity existing = new AiConversationEntity();
        existing.setId(conversationId);
        existing.setUserId(userId);
        existing.setTitle("Persisted team chat");
        existing.setMode("team");
        existing.setCaptainSelectionMode("auto");
        existing.setCompareModelsJson("[\"model-a\",\"model-b\"]");
        existing.setTeamTurnsJson("""
                [
                  {
                    "turnId": "turn-1",
                    "turnNumber": 1,
                    "captainModelId": "model-a",
                    "captainSource": "AUTO_ELECTED",
                    "userQuestion": "Question 1",
                    "finalAnswer": "Answer 1",
                    "issues": [
                      {
                        "issueId": "issue-1",
                        "title": "Issue A",
                        "resolved": true
                      }
                    ],
                    "memberStatuses": [
                      {
                        "modelId": "model-a",
                        "proposalStatus": "completed",
                        "debateStatus": "completed",
                        "summary": "Summary A",
                        "debateArguments": [
                          {
                            "issueId": "issue-1",
                            "argument": "Argument A",
                            "stance": "support"
                          }
                        ]
                      }
                    ],
                    "timestamp": "2026-04-20T10:00:00Z",
                    "stageTimestamps": {
                      "COLLECTING": "2026-04-20T09:59:40Z",
                      "COMPLETED": "2026-04-20T10:00:00Z"
                    }
                  }
                ]
                """);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(existing));

        Optional<TeamConversationPersistenceService.PersistedTeamConversationSnapshot> snapshot =
                service.loadPersistedConversation(conversationId.toString(), userId.toString());

        assertThat(snapshot).isPresent();
        assertThat(snapshot.orElseThrow().turns()).hasSize(1);
        assertThat(snapshot.orElseThrow().turns().get(0).issues()).hasSize(1);
        assertThat(snapshot.orElseThrow().turns().get(0).issues().get(0).title()).isEqualTo("Issue A");
        assertThat(snapshot.orElseThrow().turns().get(0).memberStatuses()).hasSize(1);
        assertThat(snapshot.orElseThrow().turns().get(0).memberStatuses().get(0).summary()).isEqualTo("Summary A");
        assertThat(snapshot.orElseThrow().turns().get(0).memberStatuses().get(0).debateArguments()).hasSize(1);
        assertThat(snapshot.orElseThrow().turns().get(0).memberStatuses().get(0).debateArguments().get(0).argument()).isEqualTo("Argument A");
        assertThat(snapshot.orElseThrow().turns().get(0).stageTimestamps())
                .containsEntry("COLLECTING", Instant.parse("2026-04-20T09:59:40Z"))
                .containsEntry("COMPLETED", Instant.parse("2026-04-20T10:00:00Z"));
    }

    private static AiMessageEntity message(
            UUID conversationId,
            String role,
            String content,
            String model,
            UUID multiRoundId
    ) {
        AiMessageEntity entity = new AiMessageEntity();
        entity.setId(UUID.randomUUID());
        entity.setConversationId(conversationId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setContentType("text");
        entity.setModel(model);
        entity.setVersion(1);
        entity.setBranchIndex(0);
        entity.setMultiRoundId(multiRoundId);
        return entity;
    }
}
