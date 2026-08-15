package com.webchat.platformapi.codetools;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/code-tools/tasks")
public class CodeToolsTaskController {

    private final CodeToolsTaskService service;

    public CodeToolsTaskController(CodeToolsTaskService service) {
        this.service = service;
    }

    public record CreateRequest(String kind, Map<String, Object> input) {}

    @PostMapping
    public ApiResponse<Map<String, Object>> create(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestBody(required = false) CreateRequest request
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            String kind = request != null ? request.kind : null;
            Map<String, Object> input = request != null ? request.input : null;
            return ApiResponse.ok(service.createTask(userId, kind, input));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        return ApiResponse.ok(service.listTasks(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID taskId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(service.getTask(userId, taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping("/{id}/logs")
    public ApiResponse<List<Map<String, Object>>> logs(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID taskId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(service.listLogs(userId, taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }

    @GetMapping("/{id}/artifacts")
    public ApiResponse<List<Map<String, Object>>> artifacts(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable("id") UUID taskId
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        try {
            return ApiResponse.ok(service.listArtifacts(userId, taskId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, e.getMessage());
        }
    }
}

