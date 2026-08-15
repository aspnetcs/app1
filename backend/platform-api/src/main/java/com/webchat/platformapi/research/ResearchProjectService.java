package com.webchat.platformapi.research;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.pipeline.ResearchPipelineService;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;

/**
 * Core service for research project lifecycle management.
 */
@Service
public class ResearchProjectService {

    private static final Logger log = LoggerFactory.getLogger(ResearchProjectService.class);

    private final ResearchProjectRepository projectRepo;
    private final ResearchRunRepository runRepo;
    private final ResearchStageLogRepository stageLogRepo;
    private final ResearchProperties properties;
    private final ResearchPipelineService pipelineService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResearchStageOutputNormalizer stageOutputNormalizer = new ResearchStageOutputNormalizer();

    public ResearchProjectService(
            ResearchProjectRepository projectRepo,
            ResearchRunRepository runRepo,
            ResearchStageLogRepository stageLogRepo,
            ResearchProperties properties,
            @Lazy ResearchPipelineService pipelineService
    ) {
        this.projectRepo = projectRepo;
        this.runRepo = runRepo;
        this.stageLogRepo = stageLogRepo;
        this.properties = properties;
        this.pipelineService = pipelineService;
    }

    /** List user's projects, including archived ones for status filtering. */
    public List<Map<String, Object>> listProjects(UUID userId) {
        List<ResearchProjectEntity> projects =
            projectRepo.findByUserIdOrderByCreatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ResearchProjectEntity p : projects) {
            result.add(projectToMap(p, true));
        }
        return result;
    }

    /** Get project detail with the latest run only. */
    public Map<String, Object> getProject(UUID userId, UUID projectId) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }
        Map<String, Object> map = projectToMap(project, false);
        runRepo.findFirstByProjectIdOrderByRunNumberDesc(projectId)
            .ifPresent(run -> map.put("latestRun", runToMap(run)));
        return map;
    }

    /** Create a new research project. */
    @Transactional
    public Map<String, Object> createProject(UUID userId, Map<String, Object> body) {
        String name = (String) body.get("name");
        String topic = (String) body.get("topic");
        String mode = body.get("mode") instanceof String m ? m.trim() : "single";
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is required");
        if (!"single".equals(mode) && !"team".equals(mode)) {
            mode = "single";
        }

        long activeCount = projectRepo.countByUserIdAndStatusIn(userId, List.of("draft", "active"));
        if (activeCount >= 20) {
            throw new IllegalArgumentException("max 20 active projects allowed");
        }

        ResearchProjectEntity entity = new ResearchProjectEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setTopic(topic.trim());
        entity.setMode(mode);
        entity.setStatus("draft");

        // Store optional extended config in configJson
        Map<String, Object> config = new LinkedHashMap<>();
        if (body.containsKey("templateText") && body.get("templateText") instanceof String tpl && !tpl.isBlank()) {
            config.put("templateText", tpl.trim());
        }
        String execMode = body.get("executionMode") instanceof String em ? em.trim() : "auto";
        if (!"auto".equals(execMode) && !"manual".equals(execMode)) execMode = "auto";
        config.put("executionMode", execMode);
        if (body.get("pauseStages") instanceof List<?> ps) {
            List<Integer> stages = new ArrayList<>();
            for (Object s : ps) {
                if (s instanceof Number n) stages.add(n.intValue());
                else if (s instanceof String sv) {
                    try { stages.add(Integer.parseInt(sv)); } catch (NumberFormatException ignored) {}
                }
            }
            if (!stages.isEmpty()) config.put("pauseStages", stages);
        }
        if (!config.isEmpty()) {
            try {
                entity.setConfigJson(objectMapper.writeValueAsString(config));
            } catch (Exception e) {
                log.warn("[research] failed to serialize config: {}", e.getMessage());
            }
        }

        projectRepo.save(entity);

        log.info("[research] project created: id={}, userId={}, name={}", entity.getId(), userId, name);
        return projectToMap(entity, false);
    }

    /** Soft-delete (archive) a project. */
    @Transactional
    public void archiveProject(UUID userId, UUID projectId) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }
        project.setStatus("archived");
        projectRepo.save(project);
        log.info("[research] project archived: id={}", projectId);
    }

    /** Update project template and pause config. */
    @Transactional
    public Map<String, Object> updateProject(UUID userId, UUID projectId, Map<String, Object> body) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }

        Map<String, Object> config = parseConfigJson(project.getConfigJson());
        if (body.containsKey("templateText") && body.get("templateText") instanceof String tpl) {
            config.put("templateText", tpl.trim());
        }
        if (body.containsKey("executionMode") && body.get("executionMode") instanceof String em) {
            String mode = em.trim();
            if ("auto".equals(mode) || "manual".equals(mode)) config.put("executionMode", mode);
        }
        if (body.containsKey("pauseStages") && body.get("pauseStages") instanceof List<?> ps) {
            List<Integer> stages = new ArrayList<>();
            for (Object s : ps) {
                if (s instanceof Number n) stages.add(n.intValue());
            }
            config.put("pauseStages", stages);
        }
        try {
            project.setConfigJson(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            log.warn("[research] failed to serialize config: {}", e.getMessage());
        }
        projectRepo.save(project);
        return projectToMap(project, false);
    }

    /** Start a new pipeline run. */
    @Transactional
    public Map<String, Object> startRun(UUID userId, UUID projectId) {
        return startRun(userId, projectId, Map.of());
    }

    /** Start a new pipeline run with optional branch restart params. */
    @Transactional
    public Map<String, Object> startRun(UUID userId, UUID projectId, Map<String, Object> body) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }

        Optional<ResearchRunEntity> activeRun =
            runRepo.findFirstByProjectIdAndStatusOrderByRunNumberDesc(projectId, "running");
        if (activeRun.isPresent()) {
            throw new IllegalStateException("a run is already in progress");
        }

        int nextRunNumber = runRepo.countByProjectId(projectId) + 1;
        ResearchRunEntity run = new ResearchRunEntity();
        run.setId(UUID.randomUUID());
        run.setProjectId(projectId);
        run.setRunNumber(nextRunNumber);
        run.setStatus("running");
        run.setCurrentStage(1);
        run.setStartedAt(Instant.now());
        run.setSummaryJson(new LinkedHashMap<>());

        // Store branch restart metadata if provided
        if (body != null) {
            if (body.get("sourceRunId") instanceof String srcId && !srcId.isBlank()) {
                run.getSummaryJson().put("sourceRunId", srcId);
            }
            if (body.get("restartFromStage") instanceof Number stage) {
                int fromStage = stage.intValue();
                if (fromStage >= 1 && fromStage <= 23) {
                    run.setCurrentStage(fromStage);
                    run.getSummaryJson().put("restartFromStage", fromStage);
                }
            }
            if (body.get("branchPrompt") instanceof String prompt && !prompt.isBlank()) {
                run.getSummaryJson().put("branchPrompt", prompt.trim());
            }
        }

        if (!pipelineService.tryReservePipeline(run.getId())) {
            throw new IllegalStateException("research pipeline is busy, please retry later");
        }
        runRepo.save(run);
        copyRunContextIfNeeded(projectId, run, body);

        project.setStatus("active");
        projectRepo.save(project);

        log.info("[research] run started: runId={}, projectId={}, runNumber={}",
            run.getId(), projectId, nextRunNumber);

        activatePipelineAfterCommit(run.getId());

        return runToMap(run);
    }

    /** Get run status with stage logs. */
    public Map<String, Object> getRunStatus(UUID userId, UUID projectId, UUID runId) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }
        ResearchRunEntity run = runRepo.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("run not found");
        }

        Map<String, Object> map = runToMap(run);
        List<ResearchStageLogEntity> logs = stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId);
        List<Map<String, Object>> logList = new ArrayList<>();
        for (ResearchStageLogEntity stageLog : logs) {
            backfillStageOutputIfNeeded(stageLog);
            logList.add(stageLogToMap(stageLog));
        }
        map.put("stageLogs", logList);
        return map;
    }

    /** Approve a gate stage. Placeholder for Phase 3 pipeline logic. */
    @Transactional
    public Map<String, Object> approveGate(UUID userId, UUID projectId, UUID runId, Map<String, Object> body) {
        ResearchProjectEntity project = projectRepo.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("project not found"));
        if (!project.getUserId().equals(userId)) {
            throw new IllegalArgumentException("project not found");
        }
        ResearchRunEntity run = runRepo.findById(runId)
            .orElseThrow(() -> new IllegalArgumentException("run not found"));
        if (!run.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("run not found");
        }
        if (!"waiting_approval".equals(run.getStatus())) {
            throw new IllegalStateException("run is not waiting for approval");
        }

        String decision = (String) body.getOrDefault("decision", "approve");
        log.info("[research] gate approved: runId={}, stage={}, decision={}",
            runId, run.getCurrentStage(), decision);

        if (!pipelineService.tryReservePipeline(run.getId())) {
            throw new IllegalStateException("research pipeline is busy, please retry later");
        }

        stageLogRepo.findFirstByRunIdAndStageNumberOrderByCreatedAtDesc(runId, run.getCurrentStage()).ifPresent(stageLog -> {
            stageLog.setStatus("completed");
            stageLog.setDecision(decision);
            if (stageLog.getCompletedAt() == null) {
                stageLog.setCompletedAt(Instant.now());
            }
            stageLogRepo.save(stageLog);
        });

        // Advance to next stage and resume pipeline
        run.setStatus("running");
        run.setCurrentStage(run.getCurrentStage() + 1);
        runRepo.save(run);

        activatePipelineAfterCommit(run.getId());

        return runToMap(run);
    }

    private void activatePipelineAfterCommit(UUID runId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    activateReservedPipeline(runId);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        pipelineService.releaseReservation(runId);
                    }
                }
            });
            return;
        }

        activateReservedPipeline(runId);
    }

    private void activateReservedPipeline(UUID runId) {
        try {
            pipelineService.activateReservedPipeline(runId);
        } catch (RuntimeException e) {
            pipelineService.releaseReservation(runId);
            pipelineService.markActivationFailure(runId, e.getMessage());
            throw e;
        }
    }

    // -- mapping helpers --

    private Map<String, Object> projectToMap(ResearchProjectEntity p, boolean summary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId().toString());
        map.put("name", p.getName());
        map.put("topic", p.getTopic());
        map.put("status", p.getStatus());
        map.put("mode", p.getMode());
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);

        // Include extended config fields
        Map<String, Object> config = parseConfigJson(p.getConfigJson());
        map.put("executionMode", config.getOrDefault("executionMode", "auto"));
        if (config.containsKey("templateText")) {
            map.put("templateApplied", true);
        }
        if (config.containsKey("pauseStages")) {
            map.put("pauseStages", config.get("pauseStages"));
        }

        if (summary) {
            runRepo.findFirstByProjectIdOrderByRunNumberDesc(p.getId()).ifPresent(run -> {
                map.put("currentStage", run.getCurrentStage());
                map.put("totalStages", 23);
                map.put("runStatus", run.getStatus());
            });
        }
        return map;
    }

    private Map<String, Object> runToMap(ResearchRunEntity r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId().toString());
        map.put("runNumber", r.getRunNumber());
        map.put("currentStage", r.getCurrentStage());
        map.put("totalStages", 23);
        map.put("status", r.getStatus());
        map.put("iteration", r.getIteration());
        map.put("qualityScore", r.getQualityScore());
        map.put("startedAt", r.getStartedAt() != null ? r.getStartedAt().toString() : null);
        map.put("completedAt", r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);

        // Branch/source metadata from summaryJson
        Map<String, Object> summary = r.getSummaryJson();
        if (summary != null) {
            if (summary.containsKey("sourceRunId")) map.put("sourceRunId", summary.get("sourceRunId"));
            if (summary.containsKey("restartFromStage")) map.put("restartFromStage", summary.get("restartFromStage"));
            if (summary.containsKey("branchPrompt")) map.put("branchPrompt", summary.get("branchPrompt"));
        }

        return map;
    }

    private Map<String, Object> stageLogToMap(ResearchStageLogEntity s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId().toString());
        map.put("stageNumber", s.getStageNumber());
        map.put("stageName", s.getStageName());
        map.put("status", s.getStatus());
        map.put("decision", s.getDecision());
        map.put("elapsedMs", s.getElapsedMs());
        map.put("tokensUsed", s.getTokensUsed());
        map.put("errorMessage", s.getErrorMessage());
        map.put("outputJson", s.getOutputJson());
        map.put("renderMode", "markdown");
        map.put("canBranch", "completed".equals(s.getStatus()));
        map.put("startedAt", s.getStartedAt() != null ? s.getStartedAt().toString() : null);
        map.put("completedAt", s.getCompletedAt() != null ? s.getCompletedAt().toString() : null);
        return map;
    }

    private void backfillStageOutputIfNeeded(ResearchStageLogEntity stageLog) {
        if (stageLog == null) return;
        if (!"completed".equals(stageLog.getStatus()) && !"waiting_approval".equals(stageLog.getStatus())) {
            return;
        }
        String normalized = stageOutputNormalizer.normalizeStoredOutputJson(stageLog.getOutputJson());
        if (Objects.equals(normalized, stageLog.getOutputJson())) {
            return;
        }
        stageLog.setOutputJson(normalized);
        stageLogRepo.save(stageLog);
    }

    private void copyRunContextIfNeeded(UUID projectId, ResearchRunEntity targetRun, Map<String, Object> body) {
        if (body == null) return;
        Object sourceRunIdValue = body.get("sourceRunId");
        Object restartStageValue = body.get("restartFromStage");
        if (!(sourceRunIdValue instanceof String sourceRunIdText) || sourceRunIdText.isBlank()) return;
        if (!(restartStageValue instanceof Number restartStageNumber)) return;

        UUID sourceRunId;
        try {
            sourceRunId = UUID.fromString(sourceRunIdText.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid sourceRunId");
        }

        ResearchRunEntity sourceRun = runRepo.findById(sourceRunId)
            .orElseThrow(() -> new IllegalArgumentException("source run not found"));
        if (!sourceRun.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("source run not found");
        }

        int restartStage = restartStageNumber.intValue();
        if (restartStage <= 1) return;

        List<ResearchStageLogEntity> sourceLogs = stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(sourceRunId);
        for (ResearchStageLogEntity sourceLog : sourceLogs) {
            if (sourceLog.getStageNumber() >= restartStage) {
                break;
            }
            if (!"completed".equals(sourceLog.getStatus()) && !"waiting_approval".equals(sourceLog.getStatus())) {
                continue;
            }

            ResearchStageLogEntity copied = new ResearchStageLogEntity();
            copied.setRunId(targetRun.getId());
            copied.setStageNumber(sourceLog.getStageNumber());
            copied.setStageName(sourceLog.getStageName());
            copied.setStatus("completed");
            copied.setStartedAt(sourceLog.getStartedAt());
            copied.setCompletedAt(sourceLog.getCompletedAt());
            copied.setElapsedMs(sourceLog.getElapsedMs());
            copied.setInputJson(sourceLog.getInputJson());
            copied.setOutputJson(sourceLog.getOutputJson());
            copied.setArtifactsJson(sourceLog.getArtifactsJson());
            copied.setErrorMessage(null);
            copied.setDecision(sourceLog.getDecision());
            copied.setTokensUsed(sourceLog.getTokensUsed());
            stageLogRepo.save(copied);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[research] failed to parse configJson: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}
