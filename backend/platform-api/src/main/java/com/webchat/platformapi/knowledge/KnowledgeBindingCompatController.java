package com.webchat.platformapi.knowledge;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge/conversations")
public class KnowledgeBindingCompatController {

    private final KnowledgeService knowledgeService;

    public KnowledgeBindingCompatController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<Map<String, Object>> getBinding(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID conversationId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        return ApiResponse.ok(buildBindingPayload(conversationId, knowledgeService.listBindings(userId, conversationId)));
    }

    @PutMapping("/{conversationId}")
    public ApiResponse<Map<String, Object>> updateBinding(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID conversationId,
            @RequestBody(required = false) KnowledgeBindingUpdateRequest body
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        List<String> nextIds = body == null || body.knowledgeBaseIds() == null ? List.of() : body.knowledgeBaseIds();
        List<String> currentIds = extractKnowledgeBaseIds(knowledgeService.listBindings(userId, conversationId));
        for (String currentId : currentIds) {
            if (!nextIds.contains(currentId)) {
                knowledgeService.bindConversation(userId, UUID.fromString(currentId), conversationId, false);
            }
        }
        for (String nextId : nextIds) {
            if (!currentIds.contains(nextId)) {
                knowledgeService.bindConversation(userId, UUID.fromString(nextId), conversationId, true);
            }
        }
        return ApiResponse.ok(buildBindingPayload(conversationId, knowledgeService.listBindings(userId, conversationId)));
    }

    private static Map<String, Object> buildBindingPayload(UUID conversationId, List<Map<String, Object>> bindings) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId == null ? null : conversationId.toString());
        payload.put("knowledgeBaseIds", extractKnowledgeBaseIds(bindings));
        payload.put("bindings", bindings);
        return payload;
    }

    private static List<String> extractKnowledgeBaseIds(List<Map<String, Object>> bindings) {
        List<String> ids = new ArrayList<>();
        for (Map<String, Object> binding : bindings) {
            Object raw = binding.get("baseId");
            if (raw == null) {
                raw = binding.get("id");
            }
            if (raw == null) {
                continue;
            }
            String value = String.valueOf(raw).trim();
            if (!value.isEmpty()) {
                ids.add(value);
            }
        }
        return ids;
    }

    public record KnowledgeBindingUpdateRequest(List<String> knowledgeBaseIds) {
    }
}
