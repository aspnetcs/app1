package com.webchat.platformapi.research.literature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.research.ResearchProperties;
import com.webchat.platformapi.research.ResearchRuntimeConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Multi-source academic literature search.
 * Supports OpenAlex, Semantic Scholar, and arXiv.
 * Results are deduplicated by DOI/title.
 */
@Service
public class LiteratureSearchService {

    private static final Logger log = LoggerFactory.getLogger(LiteratureSearchService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResearchRuntimeConfigService runtimeConfigService;

    public LiteratureSearchService(ResearchRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * Search all configured sources and return deduplicated results.
     */
    public List<LiteratureResult> search(String query, int maxPerSource) {
        if (query == null || query.isBlank()) return List.of();
        ResearchProperties properties = runtimeConfigService.snapshot();
        int limit = maxPerSource > 0 ? maxPerSource : properties.getLiterature().getMaxResultsPerSource();
        String sources = properties.getLiterature().getSources();

        List<LiteratureResult> all = new ArrayList<>();
        if (sources.contains("openalex")) {
            all.addAll(searchOpenAlex(query, limit));
        }
        if (sources.contains("semantic_scholar")) {
            all.addAll(searchSemanticScholar(query, limit));
        }
        if (sources.contains("arxiv")) {
            all.addAll(searchArxiv(query, limit));
        }

        return deduplicate(all);
    }

    // -- OpenAlex --

    private List<LiteratureResult> searchOpenAlex(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.openalex.org/works?search=" + encoded
                + "&per_page=" + limit + "&sort=cited_by_count:desc";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ResearchAssistant/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[literature] OpenAlex returned {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            List<LiteratureResult> list = new ArrayList<>();
            for (JsonNode work : results) {
                list.add(parseOpenAlexWork(work));
            }
            log.info("[literature] OpenAlex returned {} results for query: {}", list.size(), query);
            return list;
        } catch (Exception e) {
            log.error("[literature] OpenAlex search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private LiteratureResult parseOpenAlexWork(JsonNode work) {
        String id = work.path("id").asText("");
        String title = work.path("title").asText("Untitled");
        String doi = work.path("doi").asText(null);
        int year = work.path("publication_year").asInt(0);
        int citations = work.path("cited_by_count").asInt(0);

        List<String> authors = new ArrayList<>();
        for (JsonNode authorship : work.path("authorships")) {
            String name = authorship.path("author").path("display_name").asText("");
            if (!name.isBlank()) authors.add(name);
        }

        String abstractText = "";
        JsonNode inverted = work.path("abstract_inverted_index");
        if (inverted.isObject()) {
            abstractText = reconstructAbstract(inverted);
        }

        return new LiteratureResult("openalex", id, title, authors, abstractText,
            year, doi, work.path("id").asText(""), citations, Map.of());
    }

    /** Reconstruct abstract from OpenAlex inverted index format. */
    private String reconstructAbstract(JsonNode invertedIndex) {
        TreeMap<Integer, String> positionMap = new TreeMap<>();
        var fields = invertedIndex.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String word = entry.getKey();
            for (JsonNode pos : entry.getValue()) {
                positionMap.put(pos.asInt(), word);
            }
        }
        return String.join(" ", positionMap.values());
    }

    // -- Semantic Scholar --

    private List<LiteratureResult> searchSemanticScholar(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.semanticscholar.org/graph/v1/paper/search"
                + "?query=" + encoded + "&limit=" + limit
                + "&fields=title,authors,abstract,year,citationCount,externalIds,url";

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET();

            String apiKey = runtimeConfigService.snapshot().getLiterature().getS2ApiKey();
            if (apiKey != null && !apiKey.isBlank()) {
                builder.header("x-api-key", apiKey);
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[literature] Semantic Scholar returned {}", response.statusCode());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.path("data");
            List<LiteratureResult> list = new ArrayList<>();
            for (JsonNode paper : data) {
                list.add(parseS2Paper(paper));
            }
            log.info("[literature] Semantic Scholar returned {} results for query: {}", list.size(), query);
            return list;
        } catch (Exception e) {
            log.error("[literature] Semantic Scholar search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private LiteratureResult parseS2Paper(JsonNode paper) {
        String paperId = paper.path("paperId").asText("");
        String title = paper.path("title").asText("Untitled");
        String abstractText = paper.path("abstract").asText("");
        int year = paper.path("year").asInt(0);
        int citations = paper.path("citationCount").asInt(0);
        String url = paper.path("url").asText("");

        String doi = null;
        JsonNode externalIds = paper.path("externalIds");
        if (externalIds.has("DOI")) {
            doi = externalIds.path("DOI").asText(null);
        }

        List<String> authors = new ArrayList<>();
        for (JsonNode author : paper.path("authors")) {
            String name = author.path("name").asText("");
            if (!name.isBlank()) authors.add(name);
        }

        return new LiteratureResult("semantic_scholar", paperId, title, authors,
            abstractText, year, doi, url, citations, Map.of());
    }

    // -- arXiv --

    private List<LiteratureResult> searchArxiv(String query, int limit) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "http://export.arxiv.org/api/query?search_query=all:" + encoded
                + "&start=0&max_results=" + limit + "&sortBy=relevance&sortOrder=descending";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[literature] arXiv returned {}", response.statusCode());
                return List.of();
            }

            return parseArxivAtom(response.body());
        } catch (Exception e) {
            log.error("[literature] arXiv search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Minimal Atom XML parser for arXiv. */
    private List<LiteratureResult> parseArxivAtom(String xml) {
        List<LiteratureResult> results = new ArrayList<>();
        String[] entries = xml.split("<entry>");
        for (int i = 1; i < entries.length; i++) {
            String entry = entries[i];
            String id = extractXmlTag(entry, "id");
            String title = extractXmlTag(entry, "title").replaceAll("\\s+", " ").trim();
            String summary = extractXmlTag(entry, "summary").replaceAll("\\s+", " ").trim();
            String published = extractXmlTag(entry, "published");
            int year = 0;
            if (published.length() >= 4) {
                try { year = Integer.parseInt(published.substring(0, 4)); } catch (NumberFormatException ignored) {}
            }

            List<String> authors = new ArrayList<>();
            String[] authorBlocks = entry.split("<author>");
            for (int j = 1; j < authorBlocks.length; j++) {
                String name = extractXmlTag(authorBlocks[j], "name");
                if (!name.isBlank()) authors.add(name);
            }

            String arxivId = id.contains("abs/") ? id.substring(id.lastIndexOf("abs/") + 4) : id;
            results.add(new LiteratureResult("arxiv", arxivId, title, authors,
                summary, year, null, id, 0, Map.of()));
        }
        log.info("[literature] arXiv returned {} results", results.size());
        return results;
    }

    private String extractXmlTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag);
        if (start < 0) return "";
        start = xml.indexOf(">", start) + 1;
        int end = xml.indexOf("</" + tag + ">", start);
        if (end < 0) return "";
        return xml.substring(start, end);
    }

    // -- Deduplication --

    private List<LiteratureResult> deduplicate(List<LiteratureResult> all) {
        Map<String, LiteratureResult> seen = new LinkedHashMap<>();
        for (LiteratureResult r : all) {
            String key = r.dedupKey();
            LiteratureResult existing = seen.get(key);
            if (existing == null || r.citationCount() > existing.citationCount()) {
                seen.put(key, r);
            }
        }
        return new ArrayList<>(seen.values());
    }
}
