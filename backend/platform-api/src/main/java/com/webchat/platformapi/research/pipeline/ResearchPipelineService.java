package com.webchat.platformapi.research.pipeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.research.ResearchStage;
import com.webchat.platformapi.research.ResearchStageOutputNormalizer;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates the 23-stage research pipeline.
 * Runs stages sequentially, pauses at gates for human approval,
 * and handles rollback on failure.
 */
@Service
public class ResearchPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ResearchPipelineService.class);

    private final ResearchStageExecutor stageExecutor;
    private final ResearchRunRepository runRepo;
    private final ResearchProjectRepository projectRepo;
    private final ResearchStageLogRepository stageLogRepo;
    private final ResearchRuntimeConfigService runtimeConfigService;

    private final ExecutorService pipelineExecutor;
    private final Set<UUID> reservedPipelineSlots = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingRestarts = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Future<?>> activePipelines = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResearchStageOutputNormalizer stageOutputNormalizer = new ResearchStageOutputNormalizer();

    @jakarta.annotation.PreDestroy
    void shutdown() {
        pipelineExecutor.shutdown();
        try {
            if (!pipelineExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                pipelineExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pipelineExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public ResearchPipelineService(
            ResearchStageExecutor stageExecutor,
            ResearchRunRepository runRepo,
            ResearchProjectRepository projectRepo,
            ResearchStageLogRepository stageLogRepo,
            ResearchRuntimeConfigService runtimeConfigService
    ) {
        this.stageExecutor = stageExecutor;
        this.runRepo = runRepo;
        this.projectRepo = projectRepo;
        this.stageLogRepo = stageLogRepo;
        this.runtimeConfigService = runtimeConfigService;
        this.pipelineExecutor = Executors.newCachedThreadPool();
    }

    /**
     * Start or resume executing the pipeline for a run.
     * Execution happens asynchronously in a background thread.
     */
    public boolean startPipeline(UUID runId) {
        if (!tryReservePipeline(runId)) {
            return false;
        }
        activateReservedPipeline(runId);
        return true;
    }

    public boolean tryReservePipeline(UUID runId) {
        synchronized (activePipelines) {
            if (reservedPipelineSlots.contains(runId)) {
                log.info("[pipeline] run {} already reserved, skipping", runId);
                return false;
            }

            int maxPipelines = runtimeConfigService.snapshot().getMaxConcurrentPipelines();
            if (reservedPipelineSlots.size() >= maxPipelines) {
                log.warn("[pipeline] max concurrent pipelines reached, cannot start run {}", runId);
                return false;
            }

            reservedPipelineSlots.add(runId);
            return true;
        }
    }

    public void activateReservedPipeline(UUID runId) {
        synchronized (activePipelines) {
            if (!reservedPipelineSlots.contains(runId) && !tryReservePipeline(runId)) {
                throw new IllegalStateException("pipeline slot unavailable");
            }
            if (activePipelines.containsKey(runId)) {
                log.info("[pipeline] run {} already active, skipping", runId);
                return;
            }
            submitPipelineLocked(runId);
        }
    }

    public void releaseReservation(UUID runId) {
        synchronized (activePipelines) {
            if (!activePipelines.containsKey(runId)) {
                reservedPipelineSlots.remove(runId);
            }
            pendingRestarts.remove(runId);
        }
    }

    /**
     * Resume a pipeline from a gate approval.
     */
    public void resumeFromGate(UUID runId) {
        startPipeline(runId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOrphanedRunsOnStartup() {
        List<ResearchRunEntity> runningRuns = runRepo.findByStatusOrderByStartedAtAsc("running");
        if (runningRuns.isEmpty()) {
            return;
        }

        log.warn("[pipeline] found {} orphaned running research runs on startup; resuming", runningRuns.size());
        for (ResearchRunEntity run : runningRuns) {
            UUID runId = run.getId();
            if (!tryReservePipeline(runId)) {
                log.warn("[pipeline] could not reserve startup recovery slot for run {}", runId);
                continue;
            }
            try {
                activateReservedPipeline(runId);
            } catch (RuntimeException e) {
                releaseReservation(runId);
                markActivationFailure(runId, "startup recovery failed: " + e.getMessage());
            }
        }
    }

    /**
     * Core pipeline execution loop.
     * Runs stages sequentially from the current stage to the end.
     */
    private void executePipeline(UUID runId) {
        ResearchRunEntity run = runRepo.findById(runId).orElse(null);
        if (run == null) {
            log.warn("[pipeline] run not found: {}", runId);
            return;
        }

        ResearchProjectEntity project = projectRepo.findById(run.getProjectId()).orElse(null);
        if (project == null) {
            log.warn("[pipeline] project not found for run: {}", runId);
            return;
        }

        log.info("[pipeline] starting pipeline for run {} at stage {}", runId, run.getCurrentStage());
        String accumulatedContext = project.getTopic();

        // Determine project mode (team or single)
        String projectMode = project.getMode();
        if (projectMode == null || "semi-auto".equals(projectMode)) {
            projectMode = "single";
        }
        log.info("[pipeline] run {} using mode: {}", runId, projectMode);

        // Parse execution config from project
        Set<Integer> pauseStages = parsePauseStages(project.getConfigJson());
        String executionMode = parseExecutionMode(project.getConfigJson());

        // Load any previous stage outputs to rebuild context
        List<ResearchStageLogEntity> previousLogs =
            stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(runId);
        for (ResearchStageLogEntity prevLog : previousLogs) {
            if ("completed".equals(prevLog.getStatus()) && prevLog.getOutputJson() != null) {
                String restoredOutput = restoreStageOutputContent(prevLog.getOutputJson());
                if (restoredOutput == null || restoredOutput.isBlank()) {
                    continue;
                }
                accumulatedContext += "\n\n--- " + prevLog.getStageName() + " ---\n"
                    + restoredOutput;
            }
        }

        ResearchStage[] stages = ResearchStage.values();
        int startStage = run.getCurrentStage();

        for (int i = startStage - 1; i < stages.length; i++) {
            ResearchStage stage = stages[i];

            // Check if run was cancelled
            run = runRepo.findById(runId).orElse(null);
            if (run == null || "cancelled".equals(run.getStatus())) {
                log.info("[pipeline] run {} was cancelled at stage {}", runId, stage.getNumber());
                return;
            }

            log.info("[pipeline] executing stage {}: {}", stage.getNumber(), stage.getKey());
            updateRunStage(runId, stage.getNumber(), "running");

            boolean pauseAfterStage = shouldPauseAfterStage(stage, executionMode, pauseStages);
            StageResult result = stageExecutor.execute(run, stage, accumulatedContext, projectMode, pauseAfterStage);

            if (result.isFailed()) {
                handleFailure(runId, stage, result);
                return;
            }

            if (result.requiresApproval()) {
                updateRunStage(runId, stage.getNumber(), "waiting_approval");
                log.info("[pipeline] run {} paused at stage {} (gate={}, pauseConfig={})",
                    runId, stage.getNumber(), stage.isGate(), pauseStages.contains(stage.getNumber()));
                return;  // Will resume when user approves
            }

            // Stage succeeded, append output to context and advance
            if (result.output() != null) {
                accumulatedContext += "\n\n--- " + stage.getKey() + " ---\n" + result.output();
            }
        }

        // All stages completed
        completePipeline(runId);
    }

    @Transactional
    void updateRunStage(UUID runId, int stageNumber, String status) {
        runRepo.findById(runId).ifPresent(run -> {
            run.setCurrentStage(stageNumber);
            run.setStatus(status);
            runRepo.save(run);
        });
    }

    @Transactional
    void completePipeline(UUID runId) {
        runRepo.findById(runId).ifPresent(run -> {
            run.setStatus("completed");
            run.setCompletedAt(Instant.now());
            runRepo.save(run);

            projectRepo.findById(run.getProjectId()).ifPresent(project -> {
                project.setStatus("completed");
                projectRepo.save(project);
            });
        });
        log.info("[pipeline] run {} completed successfully", runId);
    }

    @Transactional
    void handleFailure(UUID runId, ResearchStage failedStage, StageResult result) {
        log.error("[pipeline] run {} failed at stage {}: {}",
            runId, failedStage.getNumber(), result.errorMessage());

        runRepo.findById(runId).ifPresent(run -> {
            ResearchStage rollbackTarget = ResearchStage.ROLLBACK_TARGETS.get(failedStage);
            if (rollbackTarget != null && run.getIteration() < 3) {
                // Rollback: set stage back and increment iteration
                run.setCurrentStage(rollbackTarget.getNumber());
                run.setStatus("running");
                run.setIteration(run.getIteration() + 1);
                runRepo.save(run);
                log.info("[pipeline] rolling back run {} from stage {} to stage {}",
                    runId, failedStage.getNumber(), rollbackTarget.getNumber());
                pendingRestarts.add(runId);
            } else {
                // No rollback or max iterations reached
                run.setStatus("failed");
                run.setCompletedAt(Instant.now());
                runRepo.save(run);
            }
        });
    }

    /** Check if a pipeline is currently running. */
    public boolean isRunning(UUID runId) {
        return activePipelines.containsKey(runId);
    }

    @Transactional
    public void markActivationFailure(UUID runId, String reason) {
        runRepo.findById(runId).ifPresent(run -> {
            run.setStatus("failed");
            run.setCompletedAt(Instant.now());
            runRepo.save(run);
            log.error("[pipeline] activation failed for run {}: {}", runId, reason);
        });
    }

    private void submitPipelineLocked(UUID runId) {
        Future<?> future = pipelineExecutor.submit(() -> {
            try {
                executePipeline(runId);
            } catch (Throwable error) {
                handleUnhandledPipelineThrowable(runId, error);
            } finally {
                finalizePipelineRun(runId);
            }
        });
        activePipelines.put(runId, future);
    }

    void handleUnhandledPipelineThrowable(UUID runId, Throwable error) {
        String reason = describeThrowable(error);
        log.error("[pipeline] unhandled fatal error in run {}: {}", runId, reason, error);
        try {
            markActivationFailure(runId, reason);
        } catch (Exception markError) {
            log.error("[pipeline] failed to mark run {} after fatal error: {}", runId, markError.getMessage(), markError);
        }
    }

    private String describeThrowable(Throwable error) {
        if (error == null) {
            return "unknown fatal error";
        }
        String simpleName = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return simpleName;
        }
        return simpleName + ": " + message;
    }

    private void finalizePipelineRun(UUID runId) {
        synchronized (activePipelines) {
            activePipelines.remove(runId);
            if (pendingRestarts.remove(runId)) {
                submitPipelineLocked(runId);
                return;
            }
            reservedPipelineSlots.remove(runId);
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Integer> parsePauseStages(String configJson) {
        if (configJson == null || configJson.isBlank()) return Set.of();
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            Object ps = config.get("pauseStages");
            if (ps instanceof List<?> list) {
                Set<Integer> result = new HashSet<>();
                for (Object item : list) {
                    if (item instanceof Number n) result.add(n.intValue());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("[pipeline] failed to parse pauseStages from config: {}", e.getMessage());
        }
        return Set.of();
    }

    private String parseExecutionMode(String configJson) {
        if (configJson == null || configJson.isBlank()) return "auto";
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            Object mode = config.get("executionMode");
            if (mode instanceof String m && ("auto".equals(m) || "manual".equals(m))) return m;
        } catch (Exception e) {
            log.warn("[pipeline] failed to parse executionMode from config: {}", e.getMessage());
        }
        return "auto";
    }

    boolean shouldPauseAfterStage(ResearchStage stage, String executionMode, Set<Integer> pauseStages) {
        if (!"manual".equals(executionMode)) {
            return false;
        }
        return stage.isGate() || pauseStages.contains(stage.getNumber());
    }

    String restoreStageOutputContent(String storedOutputJson) {
        return stageOutputNormalizer.extractDisplayContent(storedOutputJson);
    }
}
