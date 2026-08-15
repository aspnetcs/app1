package com.webchat.platformapi.memory;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/memory/entries")
public class MemoryEntryController {

    private final MemoryEntryService memoryEntryService;

    public MemoryEntryController(MemoryEntryService memoryEntryService) {
        this.memoryEntryService = memoryEntryService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listEntries(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        return ApiResponse.ok(memoryEntryService.listEntries(userId, limit));
    }

    @DeleteMapping("/{entryId}")
    public ApiResponse<Void> deleteEntry(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @PathVariable UUID entryId
    ) {
        if (userId == null) {
            return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        }
        try {
            memoryEntryService.deleteEntry(userId, entryId);
            return ApiResponse.ok(null);
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, ex.getMessage());
        }
    }
}
