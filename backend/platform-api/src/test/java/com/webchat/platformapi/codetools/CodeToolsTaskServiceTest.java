package com.webchat.platformapi.codetools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskApprovalEntity;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskEntity;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskLogEntity;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskApprovalRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskArtifactRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskLogRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeToolsTaskServiceTest {

    @Mock
    private CodeToolsTaskRepository taskRepo;

    @Mock
    private CodeToolsTaskApprovalRepository approvalRepo;

    @Mock
    private CodeToolsTaskLogRepository logRepo;

    @Mock
    private CodeToolsTaskArtifactRepository artifactRepo;

    private CodeToolsTaskService service;

    @BeforeEach
    void setUp() {
        service = new CodeToolsTaskService(taskRepo, approvalRepo, logRepo, artifactRepo, new ObjectMapper());
    }

    @Test
    void createTaskRejectsMissingKind() {
        UUID userId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.createTask(userId, "  ", Map.of()));
    }

    @Test
    void createTaskCreatesApprovalPendingAndLog() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> payload = service.createTask(userId, "shell", Map.of("cmd", "echo 1"));

        assertEquals("pending", payload.get("status"));
        assertNotNull(payload.get("id"));
        verify(taskRepo).save(any(CodeToolsTaskEntity.class));
        verify(approvalRepo).save(any(CodeToolsTaskApprovalEntity.class));
        verify(logRepo).save(any(CodeToolsTaskLogEntity.class));
    }

    @Test
    void getTaskRejectsUnknownTask() {
        UUID userId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(taskRepo.findById(taskId)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getTask(userId, taskId));
    }
}

