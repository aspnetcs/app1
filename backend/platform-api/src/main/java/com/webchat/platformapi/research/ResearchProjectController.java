package com.webchat.platformapi.research;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-facing Research Assistant API.
 * All endpoints gated by ResearchFeatureChecker.
 */
@RestController
@RequestMapping("/api/v1/research")
public class ResearchProjectController {

    private static final Logger log = LoggerFactory.getLogger(ResearchProjectController.class);

    private final ResearchProjectService projectService;
    private final ResearchExportService exportService;
    private final ResearchRuntimeConfigService runtimeConfigService;
    private final ResearchFeatureChecker featureChecker;

    public ResearchProjectController(
            ResearchProjectService projectService,
            ResearchExportService exportService,
            ResearchRuntimeConfigService runtimeConfigService,
            ResearchFeatureChecker featureChecker
    ) {
        this.projectService = projectService;
        this.exportService = exportService;
        this.runtimeConfigService = runtimeConfigService;
        this.featureChecker = featureChecker;
    }

    /** Public feature status for user-side gating and UX fallback. */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> getConfig() {
        ResearchProperties properties = runtimeConfigService.snapshot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", properties.isEnabled());
        payload.put("literatureEnabled", properties.getLiterature().isEnabled());
        payload.put("experimentEnabled", properties.getExperiment().isEnabled());
        payload.put("paperEnabled", properties.getPaper().isEnabled());
        payload.put("message", properties.isEnabled() ? "ok" : "research assistant is disabled");
        return ApiResponse.ok(payload);
    }

    /** List user's research projects. */
    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> listProjects(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        return ApiResponse.ok(projectService.listProjects(userId));
    }

    /** Get project detail with latest run info only; run history is not exposed. */
    @GetMapping("/projects/{id}")
    public ApiResponse<Map<String, Object>> getProject(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.getProject(userId, id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Create a new research project. */
    @PostMapping("/projects")
    public ApiResponse<Map<String, Object>> createProject(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody Map<String, Object> body
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.createProject(userId, body));
        } catch (IllegalArgumentException e) {
            log.warn("[research] create rejected for user {}: {}", userId, e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        } catch (Exception e) {
            log.error("[research] create failed for user {}", userId, e);
            return ApiResponse.error(ErrorCodes.SERVER_ERROR, "project creation failed: " + e.getMessage());
        }
    }

    /** Delete (archive) a project. */
    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            projectService.archiveProject(userId, id);
            return ApiResponse.ok("ok", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Update project template and pause config. */
    @PutMapping("/projects/{id}")
    public ApiResponse<Map<String, Object>> updateProject(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.updateProject(userId, id, body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Start a new pipeline run for the project. */
    @PostMapping("/projects/{id}/runs")
    public ApiResponse<Map<String, Object>> startRun(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.startRun(userId, id, body != null ? body : Map.of()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("[research] start run rejected: {}", e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Get the latest run status and stage logs. */
    @GetMapping("/projects/{projectId}/runs/{runId}")
    public ApiResponse<Map<String, Object>> getRunStatus(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID projectId,
            @PathVariable UUID runId
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.getRunStatus(userId, projectId, runId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Approve a gate stage to continue the pipeline. */
    @PostMapping("/projects/{projectId}/runs/{runId}/approve")
    public ApiResponse<Map<String, Object>> approveGate(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID projectId,
            @PathVariable UUID runId,
            @RequestBody Map<String, Object> body
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(projectService.approveGate(userId, projectId, runId, body));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("[research] approve rejected: {}", e.getMessage());
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    /** Export the research report. mode=result (default) or mode=full. */
    @GetMapping("/projects/{id}/export")
    public ApiResponse<Map<String, Object>> exportProject(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID id,
            @RequestParam(name = "mode", defaultValue = "result") String mode
    ) {
        featureChecker.checkEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(exportService.exportProject(userId, id, mode));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}
