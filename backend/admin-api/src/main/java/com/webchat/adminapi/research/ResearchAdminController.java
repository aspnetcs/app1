package com.webchat.adminapi.research;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.research.ResearchProperties;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import com.webchat.platformapi.research.entity.ResearchProjectEntity;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.repository.ResearchProjectRepository;
import com.webchat.platformapi.research.repository.ResearchRunRepository;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin-facing Research Assistant management API.
 * Provides config overview, project/run listing, and usage stats.
 */
@RestController
@RequestMapping("/api/v1/admin/research")
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true", matchIfMissing = true)
public class ResearchAdminController {
    private final ResearchRuntimeConfigService runtimeConfigService;
    private final ResearchProjectRepository projectRepo;
    private final ResearchRunRepository runRepo;
    private final ResearchStageLogRepository stageLogRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResearchAdminController(
            ResearchRuntimeConfigService runtimeConfigService,
            ResearchProjectRepository projectRepo,
            ResearchRunRepository runRepo,
            ResearchStageLogRepository stageLogRepo
    ) {
        this.runtimeConfigService = runtimeConfigService;
        this.projectRepo = projectRepo;
        this.runRepo = runRepo;
        this.stageLogRepo = stageLogRepo;
    }

    /** Get current research assistant configuration. */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        return ApiResponse.ok(buildConfigPayload(runtimeConfigService.snapshot()));
    }

    @PutMapping("/config")
    public ApiResponse<Map<String, Object>> updateConfig(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestBody Map<String, Object> body
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }
        try {
            ResearchProperties updated = runtimeConfigService.apply(body);
            return ApiResponse.ok(buildConfigPayload(updated));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** List all research projects across all users. */
    @GetMapping("/projects")
    public ApiResponse<Map<String, Object>> listProjects(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }

        List<ResearchProjectEntity> all = projectRepo.findAll();
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        List<ResearchProjectEntity> pageItems = all.subList(from, to);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ResearchProjectEntity p : pageItems) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId().toString());
            m.put("userId", p.getUserId().toString());
            m.put("name", p.getName());
            m.put("topic", truncate(p.getTopic(), 100));
            m.put("status", p.getStatus());
            m.put("mode", p.getMode());
            m.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
            // attach project config info
            Map<String, Object> cfg = parseConfigJson(p.getConfigJson());
            m.put("executionMode", cfg.getOrDefault("executionMode", "auto"));
            m.put("templateApplied", cfg.containsKey("templateText"));
            m.put("pauseStages", cfg.getOrDefault("pauseStages", List.of()));
            // attach latest run info
            runRepo.findFirstByProjectIdOrderByRunNumberDesc(p.getId()).ifPresent(run -> {
                m.put("currentStage", run.getCurrentStage());
                m.put("runStatus", run.getStatus());
            });
            items.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.ok(result);
    }

    /** Get runs for a specific project. */
    @GetMapping("/projects/{projectId}/runs")
    public ApiResponse<List<Map<String, Object>>> getProjectRuns(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role,
            @PathVariable UUID projectId
    ) {
        ApiResponse<List<Map<String, Object>>> authError = requireAdminList(userId, role);
        if (authError != null) {
            return authError;
        }

        List<ResearchRunEntity> runs = runRepo.findByProjectIdOrderByRunNumberDesc(projectId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ResearchRunEntity r : runs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId().toString());
            m.put("runNumber", r.getRunNumber());
            m.put("currentStage", r.getCurrentStage());
            m.put("status", r.getStatus());
            m.put("iteration", r.getIteration());
            m.put("startedAt", r.getStartedAt() != null ? r.getStartedAt().toString() : null);
            m.put("completedAt", r.getCompletedAt() != null ? r.getCompletedAt().toString() : null);

            // Branch/source metadata
            Map<String, Object> summary = r.getSummaryJson();
            if (summary != null) {
                if (summary.containsKey("sourceRunId")) m.put("sourceRunId", summary.get("sourceRunId"));
                if (summary.containsKey("restartFromStage")) m.put("restartFromStage", summary.get("restartFromStage"));
            }

            List<ResearchStageLogEntity> logs = stageLogRepo.findByRunIdOrderByStageNumberAscCreatedAtAsc(r.getId());
            m.put("stageCount", logs.size());
            m.put("totalTokens", logs.stream().mapToLong(ResearchStageLogEntity::getTokensUsed).sum());
            items.add(m);
        }
        return ApiResponse.ok(items);
    }

    /** Usage statistics overview. */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        ApiResponse<Map<String, Object>> authError = requireAdmin(userId, role);
        if (authError != null) {
            return authError;
        }

        long totalProjects = projectRepo.count();
        List<ResearchRunEntity> runs = runRepo.findAll();
        long totalRuns = runs.size();

        List<ResearchProjectEntity> activeProjects = projectRepo.findAll().stream()
                .filter(p -> "active".equals(p.getStatus()))
                .toList();
        long activeRuns = runs.stream()
                .filter(run -> "running".equalsIgnoreCase(run.getStatus()) || "waiting_approval".equalsIgnoreCase(run.getStatus()))
                .count();
        List<Long> completedDurations = runs.stream()
                .filter(run -> run.getStartedAt() != null && run.getCompletedAt() != null)
                .map(run -> java.time.Duration.between(run.getStartedAt(), run.getCompletedAt()).getSeconds())
                .toList();
        double avgCompletionSeconds = completedDurations.isEmpty()
                ? 0.0
                : completedDurations.stream().mapToLong(Long::longValue).average().orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enabled", runtimeConfigService.snapshot().isEnabled());
        stats.put("totalProjects", totalProjects);
        stats.put("activeProjects", activeProjects.size());
        stats.put("totalRuns", totalRuns);
        stats.put("activeRuns", activeRuns);
        stats.put("completedRuns", completedDurations.size());
        stats.put("avgCompletionSeconds", avgCompletionSeconds);
        return ApiResponse.ok(stats);
    }

    private Map<String, Object> buildConfigPayload(ResearchProperties properties) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("maxConcurrentPipelines", properties.getMaxConcurrentPipelines());
        config.put("stageTimeoutMinutes", properties.getStageTimeoutMinutes());

        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("model", properties.getLlm().getModel());
        llm.put("maxTokens", properties.getLlm().getMaxTokens());
        llm.put("temperature", properties.getLlm().getTemperature());
        config.put("llm", llm);

        Map<String, Object> literature = new LinkedHashMap<>();
        literature.put("enabled", properties.getLiterature().isEnabled());
        literature.put("sources", properties.getLiterature().getSources());
        literature.put("maxResultsPerSource", properties.getLiterature().getMaxResultsPerSource());
        config.put("literature", literature);

        Map<String, Object> experiment = new LinkedHashMap<>();
        experiment.put("enabled", properties.getExperiment().isEnabled());
        experiment.put("mode", properties.getExperiment().getMode());
        experiment.put("timeBudgetSec", properties.getExperiment().getTimeBudgetSec());
        config.put("experiment", experiment);

        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("enabled", properties.getPaper().isEnabled());
        paper.put("maxIterations", properties.getPaper().getMaxIterations());
        paper.put("qualityThreshold", properties.getPaper().getQualityThreshold());
        config.put("paper", paper);
        return config;
    }

    private static <T> ApiResponse<T> requireAdmin(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return null;
    }

    private static ApiResponse<List<Map<String, Object>>> requireAdminList(UUID userId, String role) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin authentication required");
        }
        if (!"admin".equalsIgnoreCase(role)) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "admin required");
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfigJson(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
}
