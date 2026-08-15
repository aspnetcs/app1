package com.webchat.platformapi.research.literature;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import com.webchat.platformapi.research.ResearchFeatureChecker;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Literature search endpoint for the research assistant.
 */
@RestController
@RequestMapping("/api/v1/research/literature")
public class LiteratureController {

    private final LiteratureSearchService searchService;
    private final ResearchFeatureChecker featureChecker;

    public LiteratureController(
            LiteratureSearchService searchService,
            ResearchFeatureChecker featureChecker
    ) {
        this.searchService = searchService;
        this.featureChecker = featureChecker;
    }

    /** Search academic literature across all configured sources. */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit
    ) {
        featureChecker.checkLiteratureEnabled();
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "user not authenticated");
        if (query == null || query.isBlank()) {
            return ApiResponse.error(ErrorCodes.PARAM_MISSING, "query is required");
        }

        List<LiteratureResult> results = searchService.search(query, Math.min(limit, 50));
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (LiteratureResult r : results) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("source", r.source());
            map.put("externalId", r.externalId());
            map.put("title", r.title());
            map.put("authors", r.authors());
            map.put("abstract", r.abstractText());
            map.put("year", r.year());
            map.put("doi", r.doi());
            map.put("url", r.url());
            map.put("citationCount", r.citationCount());
            mapped.add(map);
        }
        return ApiResponse.ok(mapped);
    }
}
