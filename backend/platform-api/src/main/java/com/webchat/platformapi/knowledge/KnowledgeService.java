package com.webchat.platformapi.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.util.RequestUtils;
import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.market.ContextAssetContract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeService {

    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeIngestJobRepository ingestJobRepository;
    private final KnowledgeConversationBindingRepository bindingRepository;
    private final DeterministicKnowledgeEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final SysConfigService sysConfigService;
    private final boolean enabled;
    private final int defaultMaxBasesPerUser;
    private final int defaultMaxChunksPerDocument;

    public KnowledgeService(KnowledgeBaseRepository baseRepository,
                            KnowledgeDocumentRepository documentRepository,
                            KnowledgeChunkRepository chunkRepository,
                            KnowledgeIngestJobRepository ingestJobRepository,
                            KnowledgeConversationBindingRepository bindingRepository,
                            DeterministicKnowledgeEmbeddingService embeddingService,
                            ObjectMapper objectMapper,
                            SysConfigService sysConfigService,
                            @Value("${platform.knowledge.enabled:true}") boolean enabled,
                            @Value("${platform.knowledge.max-bases-per-user:12}") int defaultMaxBasesPerUser,
                            @Value("${platform.knowledge.max-chunks-per-document:200}") int defaultMaxChunksPerDocument) {
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestJobRepository = ingestJobRepository;
        this.bindingRepository = bindingRepository;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.sysConfigService = sysConfigService;
        this.enabled = enabled;
        this.defaultMaxBasesPerUser = Math.max(1, defaultMaxBasesPerUser);
        this.defaultMaxChunksPerDocument = Math.max(10, defaultMaxChunksPerDocument);
    }

    public List<Map<String, Object>> listBases(UUID userId) {
        requireEnabled();
        requireUser(userId);
        return baseRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream()
                .map(this::toBaseDto)
                .toList();
    }

    public Map<String, Object> getBase(UUID userId, UUID baseId) {
        KnowledgeBaseEntity base = requireOwnedBase(userId, baseId);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(toBaseDto(base));
        out.put("documents", documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(baseId).stream().map(this::toDocumentDto).toList());
        out.put("jobs", ingestJobRepository.findTop50ByBaseIdOrderByCreatedAtDesc(baseId).stream().map(this::toJobDto).toList());
        return out;
    }

    @Transactional
    public Map<String, Object> createBase(UUID userId, Map<String, Object> body) {
        requireEnabled();
        requireUser(userId);
        if (baseRepository.findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).size() >= resolveMaxBasesPerUser()) {
            throw new IllegalStateException("knowledge base limit reached");
        }
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setOwnerUserId(userId);
        entity.setName(requireKnowledgeBaseName(body));
        entity.setDescription(sanitizeKnowledgeBaseDescription(body == null ? null : body.get("description")));
        entity.setChunkSize(clampInt(body == null ? null : body.get("chunkSize"), 200, 4000, 800));
        entity.setChunkOverlap(clampInt(body == null ? null : body.get("chunkOverlap"), 0, 500, 120));
        entity.setRetrievalLimit(clampInt(body == null ? null : body.get("retrievalLimit"), 1, 20, 6));
        entity.setSimilarityThreshold(clampDouble(body == null ? null : body.get("similarityThreshold"), 0.05d, 0.99d, 0.55d));
        entity.setEmbeddingModel(defaultString(body == null ? null : body.get("embeddingModel"), "hash-local-v1"));
        entity.setRerankModel(trim(body == null ? null : body.get("rerankModel")));
        return toBaseDto(baseRepository.save(entity));
    }

    @Transactional
    public Map<String, Object> ingestDocument(UUID userId, UUID baseId, Map<String, Object> body) {
        KnowledgeBaseEntity base = requireOwnedBase(userId, baseId);
        String sourceType = defaultString(body == null ? null : body.get("sourceType"), "note");
        String title = requireText(body, "title", 255);
        String content = requireText(body, "content", 200000);
        String sourceUri = trim(body == null ? null : body.get("sourceUri"));
        Map<String, Object> metadata = mapValue(body == null ? null : body.get("metadata"));

        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setBaseId(base.getId());
        document.setSourceType(sourceType);
        document.setTitle(title);
        document.setSourceUri(sourceUri);
        document.setContentHash(RequestUtils.sha256Hex(content));
        document.setContentText(content);
        document.setMetadataJson(writeJson(metadata));
        document.setStatus("processing");
        document = documentRepository.save(document);

        KnowledgeIngestJobEntity job = new KnowledgeIngestJobEntity();
        job.setBaseId(base.getId());
        job.setDocumentId(document.getId());
        job.setRequestedBy(userId);
        job.setSourceType(sourceType);
        job.setStatus("processing");
        job.setStartedAt(Instant.now());
        job = ingestJobRepository.save(job);

        try {
            List<String> chunks = chunkText(content, base.getChunkSize(), base.getChunkOverlap());
            int maxChunks = resolveMaxChunksPerDocument();
            if (chunks.size() > maxChunks) {
                chunks = chunks.subList(0, maxChunks);
            }
            List<KnowledgeChunkEntity> entities = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                KnowledgeChunkEntity entity = new KnowledgeChunkEntity();
                entity.setBaseId(base.getId());
                entity.setDocumentId(document.getId());
                entity.setChunkNo(i);
                entity.setChunkText(chunk);
                entity.setTokenCount(countTokens(chunk));
                entity.setMetadataJson(writeJson(Map.of("title", title, "sourceType", sourceType, "sourceUri", sourceUri == null ? "" : sourceUri, "chunkNo", i)));
                entity.setEmbedding(embeddingService.encode(embeddingService.embed(chunk)));
                entities.add(entity);
            }
            chunkRepository.saveAll(entities);
            document.setChunkCount(entities.size());
            document.setStatus("ready");
            document = documentRepository.save(document);
            job.setProcessedChunks(entities.size());
            job.setStatus("completed");
            job.setCompletedAt(Instant.now());
            job = ingestJobRepository.save(job);
        } catch (RuntimeException ex) {
            document.setStatus("failed");
            documentRepository.save(document);
            job.setStatus("failed");
            job.setErrorMessage(defaultString(ex.getMessage(), "ingest failed"));
            job.setCompletedAt(Instant.now());
            ingestJobRepository.save(job);
            throw ex;
        }

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("base", toBaseDto(base));
        out.put("document", toDocumentDto(document));
        out.put("job", toJobDto(job));
        return out;
    }

    public Map<String, Object> getJob(UUID userId, UUID baseId, UUID jobId) {
        requireOwnedBase(userId, baseId);
        return toJobDto(ingestJobRepository.findByIdAndBaseId(jobId, baseId).orElseThrow(() -> new IllegalArgumentException("knowledge ingest job not found")));
    }

    public Map<String, Object> search(UUID userId, UUID baseId, Map<String, Object> body) {
        KnowledgeBaseEntity base = requireOwnedBase(userId, baseId);
        String query = requireText(body, "query", 4000);
        String retrievalQuery = KnowledgeQueryNormalizer.normalize(query);
        int limit = clampInt(body == null ? null : body.get("limit"), 1, 20, base.getRetrievalLimit());
        UUID conversationId = parseUuid(body == null ? null : body.get("conversationId"));
        float[] queryEmbedding = embeddingService.embed(retrievalQuery);
        String encoded = embeddingService.encode(queryEmbedding);
        List<SearchHit> hits = loadSearchHits(baseId, queryEmbedding, encoded, limit).stream()
                .filter(hit -> hit.similarity() >= base.getSimilarityThreshold())
                .sorted(Comparator.comparing(SearchHit::similarity).reversed())
                .limit(limit)
                .toList();

        List<Map<String, Object>> items = new ArrayList<>();
        int index = 1;
        for (SearchHit hit : hits) items.add(toCitationItem(hit, index++));

        LinkedHashMap<String, Object> citationPayload = new LinkedHashMap<>();
        citationPayload.put("knowledge", items);
        citationPayload.put("query", query);
        citationPayload.put("baseId", String.valueOf(baseId));

        LinkedHashMap<String, Object> citationBlock = new LinkedHashMap<>();
        citationBlock.put("type", "citation");
        citationBlock.put("key", "knowledge-" + baseId);
        citationBlock.put("status", "final");
        citationBlock.put("payload", citationPayload);

        LinkedHashMap<String, Object> binding = new LinkedHashMap<>();
        binding.put("conversationId", conversationId == null ? "" : String.valueOf(conversationId));
        binding.put("baseId", String.valueOf(baseId));
        binding.put("attached", conversationId != null && bindingRepository.findByUserIdAndConversationIdAndBaseId(userId, conversationId, baseId).isPresent());

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("query", query);
        out.put("base", toBaseDto(base));
        out.put("items", items);
        out.put("citationBlock", citationBlock);
        out.put("binding", binding);
        return out;
    }

    @Transactional
    public Map<String, Object> bindConversation(UUID userId, UUID baseId, UUID conversationId, boolean enabledBinding) {
        requireOwnedBase(userId, baseId);
        if (conversationId == null) throw new IllegalArgumentException("conversationId is required");
        if (enabledBinding) {
            if (bindingRepository.findByUserIdAndConversationIdAndBaseId(userId, conversationId, baseId).isEmpty()) {
                KnowledgeConversationBindingEntity binding = new KnowledgeConversationBindingEntity();
                binding.setUserId(userId);
                binding.setConversationId(conversationId);
                binding.setBaseId(baseId);
                bindingRepository.save(binding);
            }
        } else {
            bindingRepository.deleteByUserIdAndConversationIdAndBaseId(userId, conversationId, baseId);
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", String.valueOf(conversationId));
        out.put("baseId", String.valueOf(baseId));
        out.put("attached", enabledBinding);
        out.put("bindings", listBindings(userId, conversationId));
        return out;
    }

    public List<Map<String, Object>> listBindings(UUID userId, UUID conversationId) {
        requireUser(userId);
        if (conversationId == null) return List.of();
        List<KnowledgeConversationBindingEntity> bindings = bindingRepository.findByUserIdAndConversationId(userId, conversationId);
        if (bindings.isEmpty()) return List.of();
        Map<UUID, KnowledgeBaseEntity> bases = new LinkedHashMap<>();
        for (KnowledgeBaseEntity base : baseRepository.findAllById(bindings.stream().map(KnowledgeConversationBindingEntity::getBaseId).toList())) {
            bases.put(base.getId(), base);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeConversationBindingEntity binding : bindings) {
            KnowledgeBaseEntity base = bases.get(binding.getBaseId());
            if (base == null) continue;
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", String.valueOf(binding.getConversationId()));
            item.put("baseId", String.valueOf(binding.getBaseId()));
            item.put("baseName", base.getName());
            item.put("createdAt", binding.getCreatedAt() == null ? "" : binding.getCreatedAt().toString());
            out.add(item);
        }
        return out;
    }

    public List<Map<String, Object>> adminListBases() {
        return baseRepository.findAll().stream().filter(base -> base.getDeletedAt() == null).map(this::toBaseDto).toList();
    }

    public List<Map<String, Object>> adminListJobs() {
        return ingestJobRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toJobDto).toList();
    }

    public Map<String, Object> adminConfig() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled);
        out.put("maxBasesPerUser", resolveMaxBasesPerUser());
        out.put("maxChunksPerDocument", resolveMaxChunksPerDocument());
        return out;
    }

    @Transactional
    public Map<String, Object> updateAdminConfig(Map<String, Object> body) {
        int maxBasesPerUser = clampInt(body == null ? null : body.get("maxBasesPerUser"), 1, 200, resolveMaxBasesPerUser());
        int maxChunksPerDocument = clampInt(body == null ? null : body.get("maxChunksPerDocument"), 10, 5000, resolveMaxChunksPerDocument());
        sysConfigService.set("knowledge.maxBasesPerUser", String.valueOf(maxBasesPerUser));
        sysConfigService.set("knowledge.maxChunksPerDocument", String.valueOf(maxChunksPerDocument));
        return adminConfig();
    }

    private List<SearchHit> loadSearchHits(UUID baseId, float[] queryEmbedding, String encoded, int limit) {
        Map<UUID, KnowledgeDocumentEntity> documents = new LinkedHashMap<>();
        for (KnowledgeDocumentEntity document : documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(baseId)) {
            documents.put(document.getId(), document);
        }
        try {
            List<SearchHit> hits = new ArrayList<>();
            for (KnowledgeChunkSearchRow row : chunkRepository.searchByBaseId(baseId, encoded, limit)) {
                KnowledgeDocumentEntity document = documents.get(row.getDocumentId());
                if (document == null) continue;
                hits.add(new SearchHit(row.getId(), row.getChunkText(), readJsonMap(row.getMetadataJson()), row.getSimilarity() == null ? 0d : row.getSimilarity(), document));
            }
            if (!hits.isEmpty()) return hits;
        } catch (RuntimeException ignored) {
        }
        List<SearchHit> fallback = new ArrayList<>();
        for (KnowledgeChunkEntity entity : chunkRepository.findByBaseIdAndDeletedAtIsNullOrderByDocumentIdAscChunkNoAsc(baseId)) {
            KnowledgeDocumentEntity document = documents.get(entity.getDocumentId());
            if (document == null) continue;
            double similarity = embeddingService.cosine(queryEmbedding, embeddingService.decode(entity.getEmbedding()));
            fallback.add(new SearchHit(entity.getId(), entity.getChunkText(), readJsonMap(entity.getMetadataJson()), similarity, document));
        }
        fallback.sort(Comparator.comparing(SearchHit::similarity).reversed());
        return fallback.stream().limit(limit).toList();
    }

    private Map<String, Object> toBaseDto(KnowledgeBaseEntity base) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", base.getId());
        out.put("ownerUserId", String.valueOf(base.getOwnerUserId()));
        out.put("name", base.getName());
        out.put("description", defaultString(base.getDescription(), ""));
        out.put("chunkSize", base.getChunkSize());
        out.put("chunkOverlap", base.getChunkOverlap());
        out.put("retrievalLimit", base.getRetrievalLimit());
        out.put("similarityThreshold", base.getSimilarityThreshold());
        out.put("embeddingModel", base.getEmbeddingModel());
        out.put("rerankModel", defaultString(base.getRerankModel(), ""));
        out.put("documentCount", documentRepository.findByBaseIdAndDeletedAtIsNullOrderByCreatedAtDesc(base.getId()).size());
        out.put("createdAt", base.getCreatedAt() == null ? "" : base.getCreatedAt().toString());
        out.put("updatedAt", base.getUpdatedAt() == null ? "" : base.getUpdatedAt().toString());
        out.putAll(ContextAssetContract.knowledgeContractPayload());
        out.put("retrievalMode", ContextAssetContract.USAGE_MODE_RETRIEVAL);
        return out;
    }

    private Map<String, Object> toDocumentDto(KnowledgeDocumentEntity document) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", document.getId());
        out.put("baseId", String.valueOf(document.getBaseId()));
        out.put("sourceType", document.getSourceType());
        out.put("title", document.getTitle());
        out.put("sourceUri", defaultString(document.getSourceUri(), ""));
        out.put("status", document.getStatus());
        out.put("chunkCount", document.getChunkCount());
        out.put("metadata", readJsonMap(document.getMetadataJson()));
        out.put("createdAt", document.getCreatedAt() == null ? "" : document.getCreatedAt().toString());
        out.put("updatedAt", document.getUpdatedAt() == null ? "" : document.getUpdatedAt().toString());
        return out;
    }

    private Map<String, Object> toJobDto(KnowledgeIngestJobEntity job) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", job.getId());
        out.put("baseId", String.valueOf(job.getBaseId()));
        out.put("documentId", job.getDocumentId() == null ? "" : String.valueOf(job.getDocumentId()));
        out.put("requestedBy", String.valueOf(job.getRequestedBy()));
        out.put("sourceType", job.getSourceType());
        out.put("status", job.getStatus());
        out.put("errorMessage", defaultString(job.getErrorMessage(), ""));
        out.put("processedChunks", job.getProcessedChunks());
        out.put("createdAt", job.getCreatedAt() == null ? "" : job.getCreatedAt().toString());
        out.put("startedAt", job.getStartedAt() == null ? "" : job.getStartedAt().toString());
        out.put("completedAt", job.getCompletedAt() == null ? "" : job.getCompletedAt().toString());
        return out;
    }

    private Map<String, Object> toCitationItem(SearchHit hit, int index) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("index", index);
        out.put("documentId", String.valueOf(hit.document().getId()));
        out.put("chunkId", String.valueOf(hit.chunkId()));
        out.put("title", hit.document().getTitle());
        out.put("sourceType", hit.document().getSourceType());
        out.put("sourceUri", defaultString(hit.document().getSourceUri(), ""));
        out.put("content", hit.content());
        out.put("score", hit.similarity());
        out.put("metadata", hit.metadata());
        return out;
    }

    private KnowledgeBaseEntity requireOwnedBase(UUID userId, UUID baseId) {
        requireEnabled();
        requireUser(userId);
        if (baseId == null) throw new IllegalArgumentException("knowledge base id is required");
        return baseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(baseId, userId)
                .orElseThrow(() -> new IllegalArgumentException("knowledge base not found"));
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("knowledge is disabled");
    }

    private static void requireUser(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("user not authenticated");
    }

    private int resolveMaxBasesPerUser() {
        return clampInt(sysConfigService.get("knowledge.maxBasesPerUser").orElse(null), 1, 200, defaultMaxBasesPerUser);
    }

    private int resolveMaxChunksPerDocument() {
        return clampInt(sysConfigService.get("knowledge.maxChunksPerDocument").orElse(null), 10, 5000, defaultMaxChunksPerDocument);
    }

    private List<String> chunkText(String content, int chunkSize, int chunkOverlap) {
        String text = defaultString(content, "");
        if (text.isBlank()) return List.of();
        int safeChunkSize = Math.max(200, chunkSize);
        int safeOverlap = Math.max(0, Math.min(chunkOverlap, safeChunkSize / 2));
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + safeChunkSize);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);
            if (end >= text.length()) break;
            start = Math.max(end - safeOverlap, start + 1);
        }
        return chunks;
    }

    private int countTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\s+").length;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private static String requireText(Map<String, Object> body, String key, int maxLength) {
        String text = trim(body == null ? null : body.get(key));
        if (text == null) throw new IllegalArgumentException(key + " is required");
        if (text.length() > maxLength) throw new IllegalArgumentException(key + " is too long");
        return text;
    }

    private static String requireKnowledgeBaseName(Map<String, Object> body) {
        String name = requireText(body, "name", 160);
        if (containsSuspiciousPlaceholderText(name)) {
            throw new IllegalArgumentException("knowledge base name contains invalid placeholder characters");
        }
        return name;
    }

    private static String sanitizeKnowledgeBaseDescription(Object value) {
        String description = trim(value);
        if (description == null) {
            return null;
        }
        return containsSuspiciousPlaceholderText(description) ? null : description;
    }

    private static String trim(Object value) {
        return RequestUtils.trimOrNull(value);
    }

    private static String defaultString(Object value, String fallback) {
        String text = trim(value);
        return text == null ? fallback : text;
    }

    private static int clampInt(Object value, int min, int max, int fallback) {
        Integer parsed = parseInt(value);
        if (parsed == null) return fallback;
        return Math.max(min, Math.min(max, parsed));
    }

    private static double clampDouble(Object value, double min, double max, double fallback) {
        Double parsed = parseDouble(value);
        if (parsed == null) return fallback;
        return Math.max(min, Math.min(max, parsed));
    }

    private static Integer parseInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        String text = trim(value);
        if (text == null) return null;
        try { return Integer.parseInt(text); } catch (NumberFormatException e) { return null; }
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        String text = trim(value);
        if (text == null) return null;
        try { return Double.parseDouble(text); } catch (NumberFormatException e) { return null; }
    }

    private static UUID parseUuid(Object value) {
        String text = trim(value);
        if (text == null) return null;
        try { return UUID.fromString(text); } catch (IllegalArgumentException e) { return null; }
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private static boolean containsSuspiciousPlaceholderText(String text) {
        String normalized = trim(text);
        if (normalized == null) {
            return false;
        }
        long placeholderCount = normalized.chars().filter(ch -> ch == '?' || ch == '？').count();
        if (placeholderCount < 3) {
            return false;
        }
        return normalized.codePoints().noneMatch(KnowledgeService::isCjkCodePoint);
    }

    private static boolean isCjkCodePoint(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private record SearchHit(UUID chunkId, String content, Map<String, Object> metadata, double similarity, KnowledgeDocumentEntity document) {}
}
