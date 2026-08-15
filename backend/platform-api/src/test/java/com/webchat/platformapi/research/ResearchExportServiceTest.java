package com.webchat.platformapi.research;

import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchExportServiceTest {

    @Mock
    private ResearchProjectRepository projectRepo;

    @Mock
    private ResearchRunRepository runRepo;

    @Mock
    private ResearchStageLogRepository stageLogRepo;

    private ResearchExportService service;

    @BeforeEach
    void setUp() {
        service = new ResearchExportService(projectRepo, runRepo, stageLogRepo);
    }

    @Test
    void exportProjectResultPrefersFinalDocumentWithoutStageDetails() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = run(projectId, runId);
        ResearchStageLogEntity stage17 = log(runId, 17, "paper_draft", "completed",
                "{\"format\":\"markdown\",\"content\":\"# Draft\"}");
        ResearchStageLogEntity stage22 = log(runId, 22, "export_publish", "completed",
                "{\"document\":\"# Final Paper\\n\\nOnly final result.\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stage17, stage22));

        Map<String, Object> result = service.exportProject(userId, projectId, "result");
        String content = readDocxText(result);

        assertTrue(content.contains("Final Paper"));
        assertTrue(content.contains("Only final result."));
        assertFalse(content.contains("### Stage 17 - paper_draft"));
        assertFalse(content.contains("### Stage 22 - export_publish"));
        assertEquals("Test Project.docx", result.get("filename"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.get("mimeType"));
    }

    @Test
    void exportProjectResultReadsDocumentFieldFromStage22Payload() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = run(projectId, runId);
        ResearchStageLogEntity stage22 = log(runId, 22, "export_publish", "completed",
                "{\"format\":\"markdown\",\"document\":\"# 正式标题\\n\\n## 摘要\\n\\n第一段内容。\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stage22));

        Map<String, Object> result = service.exportProject(userId, projectId, "result");
        String content = readDocxText(result);

        assertTrue(content.contains("正式标题"));
        assertTrue(content.contains("摘要"));
        assertTrue(content.contains("第一段内容。"));
        assertFalse(content.contains("\"format\""));
        assertFalse(content.contains("\"document\""));
    }

    @Test
    void exportProjectResultUnwrapsNestedJsonStoredInContentField() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = run(projectId, runId);
        ResearchStageLogEntity stage22 = log(runId, 22, "export_publish", "completed",
                "{\"format\":\"text\",\"content\":\"{\\\"format\\\":\\\"markdown\\\",\\\"document\\\":\\\"# 嵌套标题\\\\n\\\\n## 嵌套摘要\\\\n\\\\n嵌套正文。\\\"}\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stage22));

        Map<String, Object> result = service.exportProject(userId, projectId, "result");
        String content = readDocxText(result);

        assertTrue(content.contains("嵌套标题"));
        assertTrue(content.contains("嵌套摘要"));
        assertTrue(content.contains("嵌套正文。"));
        assertFalse(content.contains("\"format\""));
        assertFalse(content.contains("\"document\""));
    }

    @Test
    void exportProjectResultUsesDraftOnlyWithoutFullStageList() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = run(projectId, runId);
        ResearchStageLogEntity stage17 = log(runId, 17, "paper_draft", "completed",
                "{\"format\":\"markdown\",\"content\":\"# Draft body\"}");
        ResearchStageLogEntity stage18 = log(runId, 18, "peer_review", "completed",
                "{\"format\":\"markdown\",\"content\":\"review notes\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stage17, stage18));

        Map<String, Object> result = service.exportProject(userId, projectId, "result");
        String content = readDocxText(result);

        assertTrue(content.contains("Draft body"));
        assertFalse(content.contains("### Stage 18 - peer_review"));
        assertFalse(content.contains("review notes"));
    }

    @Test
    void exportProjectFullIncludesStageDetails() {
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();

        ResearchProjectEntity project = project(userId, projectId);
        ResearchRunEntity run = run(projectId, runId);
        ResearchStageLogEntity stage17 = log(runId, 17, "paper_draft", "completed",
                "{\"format\":\"markdown\",\"content\":\"# Draft body\"}");

        when(projectRepo.findById(projectId)).thenReturn(Optional.of(project));
        when(runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)).thenReturn(Optional.of(run));
        when(stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId)).thenReturn(List.of(stage17));

        Map<String, Object> result = service.exportProject(userId, projectId, "full");
        String content = readDocxText(result);
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> stageOutputs = (Map<String, Map<String, String>>) result.get("stageOutputs");

        assertTrue(content.contains("Stage 17 - paper_draft"));
        assertTrue(content.contains("Draft body"));
        assertEquals("markdown", stageOutputs.get("17").get("format"));
        assertTrue(stageOutputs.get("17").get("content").contains("# Draft body"));
    }

    private static String readDocxText(Map<String, Object> payload) {
        String contentBase64 = String.valueOf(payload.get("contentBase64"));
        byte[] bytes = Base64.getDecoder().decode(contentBase64);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            document.getParagraphs().forEach(paragraph -> {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(paragraph.getText());
            });
            return builder.toString();
        } catch (IOException exception) {
            throw new AssertionError("failed to read docx", exception);
        }
    }

    private static ResearchProjectEntity project(UUID userId, UUID projectId) {
        ResearchProjectEntity project = new ResearchProjectEntity();
        project.setId(projectId);
        project.setUserId(userId);
        project.setName("Test Project");
        project.setTopic("Test Topic");
        return project;
    }

    private static ResearchRunEntity run(UUID projectId, UUID runId) {
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(runId);
        run.setProjectId(projectId);
        run.setRunNumber(3);
        run.setCurrentStage(22);
        run.setStatus("completed");
        return run;
    }

    private static ResearchStageLogEntity log(UUID runId, int stageNumber, String stageName, String status, String outputJson) {
        ResearchStageLogEntity log = new ResearchStageLogEntity();
        log.setRunId(runId);
        log.setStageNumber(stageNumber);
        log.setStageName(stageName);
        log.setStatus(status);
        log.setOutputJson(outputJson);
        return log;
    }
}
