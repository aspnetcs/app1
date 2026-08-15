package com.webchat.platformapi.research.literature;

import java.util.List;
import java.util.Map;

/**
 * A single literature search result from any academic source.
 */
public record LiteratureResult(
    String source,
    String externalId,
    String title,
    List<String> authors,
    String abstractText,
    int year,
    String doi,
    String url,
    int citationCount,
    Map<String, Object> raw
) {
    /** Dedup key: prefer DOI, fallback to lowercase title. */
    public String dedupKey() {
        if (doi != null && !doi.isBlank()) return "doi:" + doi.toLowerCase();
        if (title != null) return "title:" + title.toLowerCase().replaceAll("\\s+", " ").trim();
        return "id:" + source + ":" + externalId;
    }
}
