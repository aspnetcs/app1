package com.webchat.platformapi.scheduler;

import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.scheduler.entity.AgentRunApprovalEntity;
import com.webchat.platformapi.scheduler.entity.AgentRunEntity;
import com.webchat.platformapi.scheduler.repository.AgentRunApprovalRepository;
import com.webchat.platformapi.scheduler.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

    @Mock
    private AgentRunRepository runRepo;

    @Mock
    private AgentRunApprovalRepository approvalRepo;

    @Mock
    private AgentRepository agentRepo;

    @Mock
    private AiChannelRepository channelRepo;

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(runRepo, approvalRepo, agentRepo, channelRepo);
    }

    @Test
    void createRunRejectsUnknownAgent() {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        when(agentRepo.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createRun(userId, agentId, null));
    }

    @Test
    void createRunCreatesApprovalPending() {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity agent = new AgentEntity();
        agent.setId(agentId);
        agent.setEnabled(true);
        when(agentRepo.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(agent));
        when(channelRepo.existsById(7L)).thenReturn(true);

        Map<String, Object> payload = service.createRun(userId, agentId, 7L);

        assertEquals("pending", payload.get("status"));
        assertNotNull(payload.get("id"));
        verify(runRepo).save(any(AgentRunEntity.class));
        verify(approvalRepo).save(any(AgentRunApprovalEntity.class));
    }

    @Test
    void startRunRequiresApprovedStatus() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setUserId(userId);
        run.setStatus("pending");
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));

        assertThrows(IllegalStateException.class, () -> service.startRun(userId, runId));
    }

    @Test
    void startRunTransitionsToRunning() {
        UUID userId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        AgentRunEntity run = new AgentRunEntity();
        run.setId(runId);
        run.setUserId(userId);
        run.setStatus("approved");
        when(runRepo.findById(runId)).thenReturn(Optional.of(run));
        when(approvalRepo.findByRunId(runId)).thenReturn(Optional.empty());

        Map<String, Object> payload = service.startRun(userId, runId);

        assertEquals("running", payload.get("status"));
        assertNotNull(payload.get("startedAt"));
        verify(runRepo).save(eq(run));
    }
}

