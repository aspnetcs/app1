package com.webchat.platformapi.codetools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskApprovalEntity;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskArtifactEntity;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskEntity;
import com.webchat.platformapi.codetools.entity.CodeToolsTaskLogEntity;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskApprovalRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskArtifactRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskLogRepository;
import com.webchat.platformapi.codetools.repository.CodeToolsTaskRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CodeToolsTaskService {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    public static final String APPROVAL_PENDING = "pending";
    public static final String APPROVAL_APPROVED = "approved";
    public static final String APPROVAL_REJECTED = "rejected";

    private final CodeToolsTaskRepository taskRepo;
    private final CodeToolsTaskApprovalRepository approvalRepo;
    private final CodeToolsTaskLogRepository logRepo;
    private final CodeToolsTaskArtifactRepository artifactRepo;
    private final ObjectMapper objectMapper;

    public CodeToolsTaskService(
            CodeToolsTaskRepository taskRepo,
            CodeToolsTaskApprovalRepository approvalRepo,
            CodeToolsTaskLogRepository logRepo,
            CodeToolsTaskArtifactRepository artifactRepo,
            ObjectMapper objectMapper
    ) {
        this.taskRepo = taskRepo;
        this.approvalRepo = approvalRepo;
        this.logRepo = logRepo;
        this.artifactRepo = artifactRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createTask(UUID userId, String kind, Map<String, Object> input) {
        if (userId == null) throw new IllegalArgumentException("userId required");
        String normalizedKind = kind == null ? "" : kind.trim();
        if (normalizedKind.isEmpty()) throw new IllegalArgumentException("kind required");
        if (normalizedKind.length() > 64) throw new IllegalArgumentException("kind too long");

        String inputJson = null;
        if (input != null && !input.isEmpty()) {
            try {
                inputJson = objectMapper.writeValueAsString(input);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("input is not serializable");
            }
        }

        CodeToolsTaskEntity task = new CodeToolsTaskEntity();
        task.setId(UUID.randomUUID());
        task.setUserId(userId);
        task.setKind(normalizedKind);
        task.setStatus(STATUS_PENDING);
        task.setInputJson(inputJson);
        taskRepo.save(task);

        CodeToolsTaskApprovalEntity approval = new CodeToolsTaskApprovalEntity();
        approval.setId(UUID.randomUUID());
        approval.setTaskId(task.getId());
        approval.setStatus(APPROVAL_PENDING);
        approvalRepo.save(approval);

        appendLog(task.getId(), "INFO", "task created");

        return toMap(task, approval);
    }

    public Map<String, Object> getTask(UUID userId, UUID taskId) {
        CodeToolsTaskEntity task = taskRepo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("task not found"));
        if (!task.getUserId().equals(userId)) throw new IllegalArgumentException("task not found");
        CodeToolsTaskApprovalEntity approval = approvalRepo.findByTaskId(taskId).orElse(null);
        return toMap(task, approval);
    }

    public Map<String, Object> listTasks(UUID userId, int page, int size) {
        int resolvedSize = Math.min(Math.max(size, 1), 100);
        int resolvedPage = Math.max(page, 0);
        Page<CodeToolsTaskEntity> result = taskRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(resolvedPage, resolvedSize));
        List<CodeToolsTaskEntity> tasks = result.getContent();

        Map<UUID, CodeToolsTaskApprovalEntity> approvals = tasks.isEmpty()
                ? Map.of()
                : indexByTaskId(approvalRepo.findByTaskIdIn(tasks.stream().map(CodeToolsTaskEntity::getId).toList()));
        return Map.of(
                "items", tasks.stream().map(task -> toMap(task, approvals.get(task.getId()))).toList(),
                "total", result.getTotalElements(),
                "page", result.getNumber(),
                "size", result.getSize()
        );
    }

    public List<Map<String, Object>> listLogs(UUID userId, UUID taskId) {
        // This call is user-scoped: the existence check must validate ownership.
        CodeToolsTaskEntity task = taskRepo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("task not found"));
        if (!task.getUserId().equals(userId)) throw new IllegalArgumentException("task not found");
        return logRepo.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(CodeToolsTaskService::logToMap).toList();
    }

    public List<Map<String, Object>> listArtifacts(UUID userId, UUID taskId) {
        CodeToolsTaskEntity task = taskRepo.findById(taskId).orElseThrow(() -> new IllegalArgumentException("task not found"));
        if (!task.getUserId().equals(userId)) throw new IllegalArgumentException("task not found");
        return artifactRepo.findByTaskIdOrderByCreatedAtDesc(taskId).stream().map(CodeToolsTaskService::artifactToMap).toList();
    }

    private void appendLog(UUID taskId, String level, String message) {
        CodeToolsTaskLogEntity log = new CodeToolsTaskLogEntity();
        log.setTaskId(taskId);
        log.setLevel(level != null ? level.trim() : "INFO");
        log.setMessage(message != null ? message : "");
        logRepo.save(log);
    }

    private static Map<UUID, CodeToolsTaskApprovalEntity> indexByTaskId(Collection<CodeToolsTaskApprovalEntity> approvals) {
        if (approvals == null || approvals.isEmpty()) return Map.of();
        return approvals.stream().filter(a -> a.getTaskId() != null).collect(Collectors.toMap(CodeToolsTaskApprovalEntity::getTaskId, Function.identity(), (a, b) -> a));
    }

    private static Map<String, Object> toMap(CodeToolsTaskEntity task, CodeToolsTaskApprovalEntity approval) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.getId() != null ? task.getId().toString() : null);
        map.put("userId", task.getUserId() != null ? task.getUserId().toString() : null);
        map.put("kind", task.getKind());
        map.put("status", task.getStatus());
        map.put("inputJson", task.getInputJson());
        map.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        map.put("updatedAt", task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : null);

        if (approval != null) {
            Map<String, Object> approvalMap = new LinkedHashMap<>();
            approvalMap.put("status", approval.getStatus());
            approvalMap.put("decidedBy", approval.getDecidedBy() != null ? approval.getDecidedBy().toString() : null);
            approvalMap.put("decidedAt", approval.getDecidedAt() != null ? approval.getDecidedAt().toString() : null);
            approvalMap.put("note", approval.getNote());
            map.put("approval", approvalMap);
        } else {
            map.put("approval", null);
        }
        return map;
    }

    private static Map<String, Object> logToMap(CodeToolsTaskLogEntity log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("taskId", log.getTaskId() != null ? log.getTaskId().toString() : null);
        map.put("level", log.getLevel());
        map.put("message", log.getMessage());
        map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        return map;
    }

    private static Map<String, Object> artifactToMap(CodeToolsTaskArtifactEntity artifact) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", artifact.getId());
        map.put("taskId", artifact.getTaskId() != null ? artifact.getTaskId().toString() : null);
        map.put("artifactType", artifact.getArtifactType());
        map.put("name", artifact.getName());
        map.put("mime", artifact.getMime());
        map.put("contentText", artifact.getContentText());
        map.put("contentUrl", artifact.getContentUrl());
        map.put("createdAt", artifact.getCreatedAt() != null ? artifact.getCreatedAt().toString() : null);
        return map;
    }
}
