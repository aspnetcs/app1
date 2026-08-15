package com.webchat.platformapi.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResearchExportService {

    private static final String WORD_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final ResearchProjectRepository projectRepo;
    private final ResearchRunRepository runRepo;
    private final ResearchStageLogRepository stageLogRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchExportService(
            ResearchProjectRepository projectRepo,
            ResearchRunRepository runRepo,
            ResearchStageLogRepository stageLogRepo
    ) {
        this.projectRepo = projectRepo;
        this.runRepo = runRepo;
        this.stageLogRepo = stageLogRepo;
    }

    public Map<String, Object> exportProject(UUID userId, UUID projectId) {
        return exportProject(userId, projectId, "result");
    }

    public Map<String, Object> exportProject(UUID userId, UUID projectId, String mode) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }

        ResearchRunEntity run = runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)
                .orElseThrow(() -> new IllegalArgumentException("run not found"));
        List<ResearchStageLogEntity> logs = stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(run.getId());

        String markdown = "full".equals(mode)
                ? buildFullMarkdown(project, run, logs)
                : buildResultMarkdown(project, run, logs);
        byte[] documentBytes = buildDocx(markdown);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filename", sanitizeFilename(project.getName()) + ".docx");
        payload.put("mimeType", WORD_MIME_TYPE);
        payload.put("contentBase64", Base64.getEncoder().encodeToString(documentBytes));
        payload.put("stageOutputs", buildStageOutputs(logs));
        payload.put("runId", run.getId().toString());
        payload.put("projectId", project.getId().toString());
        return payload;
    }

    private String buildResultMarkdown(ResearchProjectEntity project, ResearchRunEntity run, List<ResearchStageLogEntity> logs) {
        String exportedDocument = extractStage22Document(logs);
        if (!exportedDocument.isBlank()) {
            return exportedDocument;
        }

        String draft = findStageOutput(logs, 19);
        if (draft.isBlank()) {
            draft = findStageOutput(logs, 17);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(project.getName()).append("\n\n");
        builder.append("## 项目信息\n\n");
        builder.append("- 课题: ").append(project.getTopic()).append("\n");
        builder.append("- 运行编号: ").append(run.getRunNumber()).append("\n");
        builder.append("- 当前状态: ").append(run.getStatus()).append("\n");
        builder.append("- 当前阶段: ").append(run.getCurrentStage()).append("/23\n\n");

        if (!draft.isBlank()) {
            builder.append("## 论文正文\n\n");
            builder.append(draft).append("\n");
            return builder.toString().trim();
        }

        builder.append("## 结果摘要\n\n");
        builder.append(buildResultSummary(logs)).append("\n");
        return builder.toString().trim();
    }

    private String buildFullMarkdown(ResearchProjectEntity project, ResearchRunEntity run, List<ResearchStageLogEntity> logs) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(project.getName()).append("\n\n");
        builder.append("## 项目信息\n\n");
        builder.append("- 课题: ").append(project.getTopic()).append("\n");
        builder.append("- 运行编号: ").append(run.getRunNumber()).append("\n");
        builder.append("- 当前状态: ").append(run.getStatus()).append("\n");
        builder.append("- 当前阶段: ").append(run.getCurrentStage()).append("/23\n\n");

        builder.append("## 全部阶段详情\n\n");
        for (ResearchStageLogEntity log : logs) {
            builder.append("### Stage ").append(log.getStageNumber()).append(" - ").append(log.getStageName()).append("\n\n");
            builder.append("- 状态: ").append(log.getStatus()).append("\n");
            if (log.getElapsedMs() != null) {
                builder.append("- 耗时: ").append(log.getElapsedMs()).append("ms\n");
            }
            if (log.getTokensUsed() > 0) {
                builder.append("- Tokens: ").append(log.getTokensUsed()).append("\n");
            }
            if (log.getErrorMessage() != null && !log.getErrorMessage().isBlank()) {
                builder.append("- 错误: ").append(log.getErrorMessage()).append("\n");
            }
            String output = sanitizeStageOutput(log.getOutputJson());
            if (!output.isBlank()) {
                builder.append("\n").append(output).append("\n");
            }
            builder.append("\n");
        }
        return builder.toString().trim();
    }

    private String buildResultSummary(List<ResearchStageLogEntity> logs) {
        StringBuilder builder = new StringBuilder();
        for (ResearchStageLogEntity log : logs) {
            if (!"completed".equals(log.getStatus())) {
                continue;
            }
            String output = sanitizeStageOutput(log.getOutputJson());
            if (output.isBlank()) {
                continue;
            }
            builder.append("### Stage ").append(log.getStageNumber()).append(" - ").append(log.getStageName()).append("\n\n");
            builder.append(output).append("\n\n");
        }
        if (builder.isEmpty()) {
            return "当前没有可导出的结果内容。";
        }
        return builder.toString().trim();
    }

    private Map<String, Map<String, String>> buildStageOutputs(List<ResearchStageLogEntity> logs) {
        Map<String, Map<String, String>> stageOutputs = new LinkedHashMap<>();
        for (ResearchStageLogEntity log : logs) {
            if (!"completed".equals(log.getStatus())) {
                continue;
            }
            String output = sanitizeStageOutput(log.getOutputJson());
            if (output.isBlank()) {
                continue;
            }
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("format", detectStageOutputFormat(output));
            entry.put("content", output);
            stageOutputs.put(String.valueOf(log.getStageNumber()), entry);
        }
        return stageOutputs;
    }

    private String detectStageOutputFormat(String output) {
        if (output == null || output.isBlank()) {
            return "text";
        }
        String normalized = output.strip();
        if (normalized.startsWith("#")
                || normalized.startsWith("##")
                || normalized.startsWith("###")
                || normalized.startsWith("- ")
                || normalized.startsWith("* ")) {
            return "markdown";
        }
        return "text";
    }

    private String extractStage22Document(List<ResearchStageLogEntity> logs) {
        String rawStage22 = logs.stream()
                .filter(log -> log.getStageNumber() == 22)
                .map(ResearchStageLogEntity::getOutputJson)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
        if (rawStage22.isBlank()) {
            return "";
        }
        return extractStructuredText(rawStage22);
    }

    private String findStageOutput(List<ResearchStageLogEntity> logs, int stageNumber) {
        return logs.stream()
                .filter(log -> log.getStageNumber() == stageNumber)
                .map(ResearchStageLogEntity::getOutputJson)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .map(this::sanitizeStageOutput)
                .orElse("");
    }

    private String sanitizeStageOutput(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String structured = extractStructuredText(raw);
        if (!structured.isBlank()) {
            return structured;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isTextual()) {
                return root.asText("");
            }
            JsonNode documentNode = root.get("document");
            if (documentNode != null && documentNode.isTextual()) {
                return documentNode.asText("");
            }
            JsonNode contentNode = root.get("content");
            if (contentNode != null && contentNode.isTextual()) {
                return contentNode.asText("");
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String extractStructuredText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return extractStructuredText(objectMapper.readTree(raw), 0);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractStructuredText(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 4) {
            return "";
        }
        if (node.isTextual()) {
            String text = node.asText("");
            if (text.isBlank()) {
                return "";
            }
            try {
                JsonNode nested = objectMapper.readTree(text);
                String nestedText = extractStructuredText(nested, depth + 1);
                if (!nestedText.isBlank()) {
                    return nestedText;
                }
            } catch (Exception ignored) {
                // Plain text, not nested JSON.
            }
            return text;
        }
        if (node.isObject()) {
            String document = extractStructuredText(node.get("document"), depth + 1);
            if (!document.isBlank()) {
                return document;
            }
            String content = extractStructuredText(node.get("content"), depth + 1);
            if (!content.isBlank()) {
                return content;
            }
            String summary = extractStructuredText(node.get("summary"), depth + 1);
            if (!summary.isBlank()) {
                return summary;
            }
            String result = extractStructuredText(node.get("result"), depth + 1);
            if (!result.isBlank()) {
                return result;
            }
            String text = extractStructuredText(node.get("text"), depth + 1);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private String sanitizeFilename(String raw) {
        String base = raw == null ? "research-paper" : raw.trim();
        if (base.isBlank()) {
            return "research-paper";
        }
        return base.replaceAll("[\\\\/:*?\"<>|]+", "-");
    }

    private byte[] buildDocx(String markdown) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").trim();
            if (normalized.isEmpty()) {
                createParagraph(document, "", null, 12, false, ParagraphAlignment.LEFT);
            } else {
                for (String line : normalized.split("\n")) {
                    appendMarkdownLine(document, line);
                }
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to build research docx", exception);
        }
    }

    private void appendMarkdownLine(XWPFDocument document, String rawLine) {
        String line = rawLine == null ? "" : rawLine.stripTrailing();
        if (line.isBlank()) {
            createParagraph(document, "", null, 12, false, ParagraphAlignment.LEFT);
            return;
        }
        if (line.startsWith("# ")) {
            createParagraph(document, line.substring(2).trim(), "Title", 18, true, ParagraphAlignment.CENTER);
            return;
        }
        if (line.startsWith("## ")) {
            createParagraph(document, line.substring(3).trim(), "Heading1", 15, true, ParagraphAlignment.LEFT);
            return;
        }
        if (line.startsWith("### ")) {
            createParagraph(document, line.substring(4).trim(), "Heading2", 13, true, ParagraphAlignment.LEFT);
            return;
        }
        if (line.startsWith("- ") || line.startsWith("* ")) {
            createParagraph(document, "• " + line.substring(2).trim(), null, 12, false, ParagraphAlignment.LEFT);
            return;
        }
        createParagraph(document, line, null, 12, false, ParagraphAlignment.LEFT);
    }

    private void createParagraph(
            XWPFDocument document,
            String text,
            String style,
            int fontSize,
            boolean bold,
            ParagraphAlignment alignment
    ) {
        XWPFParagraph paragraph = document.createParagraph();
        if (style != null) {
            paragraph.setStyle(style);
        }
        paragraph.setAlignment(alignment);
        XWPFRun run = paragraph.createRun();
        run.setFontFamily("Microsoft YaHei");
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setText(text == null ? "" : text);
    }
}
