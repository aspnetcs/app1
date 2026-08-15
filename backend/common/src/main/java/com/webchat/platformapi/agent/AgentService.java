package com.webchat.platformapi.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.common.util.RequestUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Unified Agent service -- replaces ConversationTemplateService, PromptService, and AgentInstallService.
 */
@Service
public class AgentService {

    private static final int MAX_SYSTEM_PROMPT_LENGTH = 8000;
    private static final int MAX_CONTEXT_MESSAGE_COUNT = 12;
    private static final int MAX_CONTEXT_MESSAGE_LENGTH = 4000;
    private static final int MAX_CONTEXT_TOTAL_LENGTH = 12000;
    private static final double MIN_TEMPERATURE = 0.0;
    private static final double MAX_TEMPERATURE = 2.0;
    private static final double MIN_TOP_P = 0.0;
    private static final double MAX_TOP_P = 1.0;
    private static final int MIN_MAX_TOKENS = 0;
    private static final int MAX_MAX_TOKENS = 200000;
    private static final int MIN_SORT_ORDER = 0;
    private static final int MAX_SORT_ORDER = 100000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentRepository repository;

    public AgentService(AgentRepository repository) {
        this.repository = repository;
    }

    // ========== User-facing: marketplace ==========

    /**
     * List public agents visible to all users (scope=SYSTEM, enabled=true).
     */
    public List<Map<String, Object>> listMarket() {
        return listMarket(null);
    }

    /**
     * List public agents visible to all users (scope=SYSTEM, enabled=true),
     * optionally constrained to a single category.
     */
    public List<Map<String, Object>> listMarket(String category) {
        String normalizedCategory = normalizeCategory(category);
        List<AgentEntity> entities = normalizedCategory == null
                ? repository.findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(AgentScope.SYSTEM)
                : repository.findByScopeAndEnabledTrueAndDeletedAtIsNullAndNormalizedCategoryOrderBySortOrderAscCreatedAtDesc(
                        AgentScope.SYSTEM,
                        normalizedCategory
                );
        return entities
                .stream()
                .map(AgentService::toMarketDto)
                .toList();
    }

    /**
     * List user's private agents (scope=USER).
     */
    public List<Map<String, Object>> listUserAgents(UUID userId) {
        return repository
                .findByUserIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(userId)
                .stream()
                .map(AgentService::toUserDto)
                .toList();
    }

    /**
     * Get a single agent detail visible to the current requester.
     */
    public Map<String, Object> getAgent(UUID requesterUserId, UUID id) {
        AgentEntity entity = requireUserFacingDetailAgent(requesterUserId, id);
        return toUserDto(entity);
    }

    /**
     * Get raw entity by id (for multi-agent discussion and other internal consumers).
     */
    public AgentEntity getAgentEntity(UUID id) {
        return requireAgent(id);
    }

    /**
     * Create a personal agent for a user.
     */
    public Map<String, Object> createAgent(UUID userId, Map<String, Object> body) {
        AgentEntity entity = new AgentEntity();
        entity.setScope(AgentScope.USER);
        entity.setUserId(userId);
        applyBody(entity, body);
        return toUserDto(repository.save(entity));
    }

    /**
     * Update an owned agent.
     */
    public Map<String, Object> updateAgent(UUID userId, UUID id, Map<String, Object> body) {
        AgentEntity entity = requireOwnedAgent(userId, id);
        applyBody(entity, body);
        return toUserDto(repository.save(entity));
    }

    /**
     * Delete an owned agent (soft delete).
     */
    public void deleteAgent(UUID userId, UUID id) {
        AgentEntity entity = requireOwnedAgent(userId, id);
        entity.setDeletedAt(Instant.now());
        repository.save(entity);
    }

    /**
     * Install (clone) a public agent as user's private copy.
     */
    public Map<String, Object> installAgent(UUID userId, UUID agentId) {
        AgentEntity source = requireInstallablePublicAgent(agentId);

        AgentEntity clone = new AgentEntity();
        clone.setScope(AgentScope.USER);
        clone.setUserId(userId);
        clone.setSourceAgentId(source.getId());
        clone.setName(source.getName());
        clone.setDescription(source.getDescription());
        clone.setAvatar(source.getAvatar());
        clone.setIcon(source.getIcon());
        clone.setCategory(source.getCategory());
        clone.setTags(source.getTags());
        clone.setModelId(source.getModelId());
        clone.setSystemPrompt(source.getSystemPrompt());
        clone.setFirstMessage(source.getFirstMessage());
        clone.setContextMessagesJson(source.getContextMessagesJson());
        clone.setTemperature(source.getTemperature());
        clone.setTopP(source.getTopP());
        clone.setMaxTokens(source.getMaxTokens());
        clone.setAuthor(source.getAuthor());
        clone.setRequiredToolsJson(source.getRequiredToolsJson());
        clone.setEnabled(true);

        repository.incrementInstallCount(source.getId());
        return toUserDto(repository.save(clone));
    }

    // ========== Admin ==========

    public Page<Map<String, Object>> listForAdmin(
            String search, String category, Boolean enabled,
            String scope, Boolean featured,
            int page, int size
    ) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.desc("createdAt"))
        );
        Specification<AgentEntity> spec = Specification.where(notDeleted())
                .and(matchSearch(search))
                .and(matchCategory(category))
                .and(matchEnabled(enabled))
                .and(matchScope(scope))
                .and(matchFeatured(featured));
        Page<AgentEntity> result = repository.findAll(spec, pageable);
        List<Map<String, Object>> items = result.getContent().stream().map(AgentService::toAdminDto).toList();
        return new PageImpl<>(items, pageable, result.getTotalElements());
    }

    public Map<String, Object> createForAdmin(Map<String, Object> body) {
        AgentEntity entity = new AgentEntity();
        String rawScope = str(body == null ? null : body.get("scope"));
        entity.setScope(AgentScope.from(rawScope));
        applyBody(entity, body);
        applyAdminFields(entity, body);
        enforceAdminOwnership(entity);
        return toAdminDto(repository.save(entity));
    }

    public Map<String, Object> updateForAdmin(UUID id, Map<String, Object> body) {
        AgentEntity entity = requireAgent(id);
        applyBody(entity, body);
        applyAdminFields(entity, body);
        enforceAdminOwnership(entity);
        return toAdminDto(repository.save(entity));
    }

    public void deleteForAdmin(UUID id) {
        AgentEntity entity = requireAgent(id);
        entity.setDeletedAt(Instant.now());
        repository.save(entity);
    }

    // ========== Internal ==========

    /**
     * Load multiple agents by IDs (for multi-agent discussion).
     */
    public List<AgentEntity> loadAgents(List<UUID> agentIds) {
        return agentIds.stream().map(this::requireAgent).toList();
    }

    // ========== Body application ==========

    private void applyBody(AgentEntity entity, Map<String, Object> body) {
        if (body == null) throw new IllegalArgumentException("request body is empty");

        String name = str(body.get("name"));
        if (blank(name)) throw new IllegalArgumentException("missing name");
        entity.setName(name);

        String modelId = RequestUtils.firstNonBlank(str(body.get("modelId")), str(body.get("model_id")));
        if (blank(modelId)) throw new IllegalArgumentException("missing modelId");
        entity.setModelId(modelId);

        String systemPrompt = defaultString(RequestUtils.firstNonBlank(str(body.get("systemPrompt")), str(body.get("system_prompt")))).trim();
        if (systemPrompt.length() > MAX_SYSTEM_PROMPT_LENGTH) throw new IllegalArgumentException("systemPrompt too long");
        entity.setSystemPrompt(systemPrompt);

        if (body.containsKey("description")) {
            entity.setDescription(defaultString(str(body.get("description"))));
        }
        if (body.containsKey("avatar")) {
            entity.setAvatar(defaultString(str(body.get("avatar"))));
        }
        if (body.containsKey("icon")) {
            entity.setIcon(defaultString(str(body.get("icon"))));
        }
        if (body.containsKey("category")) {
            entity.setCategory(RequestUtils.firstNonBlank(str(body.get("category")), "general"));
        }
        if (body.containsKey("tags")) {
            entity.setTags(defaultString(str(body.get("tags"))));
        }
        if (body.containsKey("firstMessage") || body.containsKey("first_message")) {
            entity.setFirstMessage(defaultString(RequestUtils.firstNonBlank(str(body.get("firstMessage")), str(body.get("first_message")))));
        }
        if (body.containsKey("contextMessagesJson") || body.containsKey("context_messages_json")) {
            String json = defaultString(RequestUtils.firstNonBlank(str(body.get("contextMessagesJson")), str(body.get("context_messages_json")))).trim();
            if (json.isBlank()) json = "[]";
            try {
                validateContextMessages(OBJECT_MAPPER.readTree(json));
            } catch (Exception e) {
                throw new IllegalArgumentException("contextMessagesJson invalid");
            }
            entity.setContextMessagesJson(json);
        }
        if (body.containsKey("temperature")) {
            Object v = body.get("temperature");
            if (v != null) entity.setTemperature(parseDouble(v, MIN_TEMPERATURE, MAX_TEMPERATURE, "temperature"));
        }
        if (body.containsKey("topP") || body.containsKey("top_p")) {
            Object v = body.containsKey("topP") ? body.get("topP") : body.get("top_p");
            if (v != null) entity.setTopP(parseDouble(v, MIN_TOP_P, MAX_TOP_P, "topP"));
        }
        if (body.containsKey("maxTokens") || body.containsKey("max_tokens")) {
            Object v = body.containsKey("maxTokens") ? body.get("maxTokens") : body.get("max_tokens");
            if (v != null) entity.setMaxTokens(parseInt(v, MIN_MAX_TOKENS, MAX_MAX_TOKENS, "maxTokens"));
        }
        if (body.containsKey("sortOrder") || body.containsKey("sort_order")) {
            Object v = body.containsKey("sortOrder") ? body.get("sortOrder") : body.get("sort_order");
            if (v != null) entity.setSortOrder(parseInt(v, MIN_SORT_ORDER, MAX_SORT_ORDER, "sortOrder"));
        }
        if (body.containsKey("enabled")) {
            Boolean enabled = toBoolean(body.get("enabled"));
            if (enabled != null) entity.setEnabled(enabled);
        }
        if (body.containsKey("author")) {
            entity.setAuthor(defaultString(str(body.get("author"))));
        }
        if (body.containsKey("requiredToolsJson") || body.containsKey("required_tools_json")) {
            entity.setRequiredToolsJson(defaultString(firstNonBlank(
                    str(body.get("requiredToolsJson")),
                    str(body.get("required_tools_json")),
                    "[]")));
        }
    }

    private void applyAdminFields(AgentEntity entity, Map<String, Object> body) {
        if (body == null) return;
        if (body.containsKey("scope")) {
            entity.setScope(AgentScope.from(str(body.get("scope"))));
        }
        if (body.containsKey("featured")) {
            Boolean featured = toBoolean(body.get("featured"));
            if (featured != null) entity.setFeatured(featured);
        }
        if (body.containsKey("userId") || body.containsKey("user_id")) {
            String raw = RequestUtils.firstNonBlank(str(body.get("userId")), str(body.get("user_id")));
            entity.setUserId(raw == null ? null : UUID.fromString(raw));
        }
    }

    // ========== Query helpers ==========

    private AgentEntity requireAgent(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new IllegalArgumentException("agent not found"));
    }

    private AgentEntity requireOwnedAgent(UUID userId, UUID id) {
        AgentEntity entity = requireAgent(id);
        if (entity.getUserId() == null || !entity.getUserId().equals(userId)) {
            throw new IllegalArgumentException("agent not found");
        }
        return entity;
    }

    private AgentEntity requireUserFacingDetailAgent(UUID requesterUserId, UUID id) {
        AgentEntity entity = requireAgent(id);
        if (entity.getScope() == AgentScope.SYSTEM && entity.isEnabled()) {
            return entity;
        }
        if (entity.getScope() == AgentScope.USER
                && requesterUserId != null
                && requesterUserId.equals(entity.getUserId())) {
            return entity;
        }
        throw new IllegalArgumentException("agent not found");
    }

    private AgentEntity requireInstallablePublicAgent(UUID id) {
        AgentEntity entity = requireAgent(id);
        if (entity.getScope() == AgentScope.SYSTEM && entity.isEnabled()) {
            return entity;
        }
        throw new IllegalArgumentException("agent not found");
    }

    // ========== Specifications ==========

    private static Specification<AgentEntity> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private static Specification<AgentEntity> matchSearch(String search) {
        if (blank(search)) return null;
        String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(cb.coalesce(root.get("name"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("category"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("modelId"), "")), like)
        );
    }

    private static Specification<AgentEntity> matchCategory(String category) {
        String normalizedCategory = normalizeCategory(category);
        if (normalizedCategory == null) return null;
        return (root, query, cb) -> cb.equal(
                cb.lower(cb.trim(root.get("category"))),
                normalizedCategory
        );
    }

    private static Specification<AgentEntity> matchEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    private static Specification<AgentEntity> matchScope(String scope) {
        if (blank(scope)) return null;
        AgentScope s = AgentScope.from(scope);
        return (root, query, cb) -> cb.equal(root.get("scope"), s);
    }

    private static Specification<AgentEntity> matchFeatured(Boolean featured) {
        if (featured == null) return null;
        return (root, query, cb) -> cb.equal(root.get("featured"), featured);
    }

    private static void enforceAdminOwnership(AgentEntity entity) {
        if (entity.getScope() == AgentScope.USER) {
            if (entity.getUserId() == null) {
                throw new IllegalArgumentException("userId required for USER scope");
            }
            return;
        }
        entity.setUserId(null);
    }

    private static String normalizeCategory(String category) {
        if (blank(category)) return null;
        return category.trim().toLowerCase(Locale.ROOT);
    }

    // ========== DTO builders ==========

    public static Map<String, Object> toMarketDto(AgentEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("name", e.getName());
        out.put("description", defaultString(e.getDescription()));
        out.put("avatar", defaultString(e.getAvatar()));
        out.put("icon", defaultString(e.getIcon()));
        out.put("category", defaultString(e.getCategory()));
        out.put("tags", defaultString(e.getTags()));
        out.put("modelId", e.getModelId());
        out.put("author", defaultString(e.getAuthor()));
        out.put("featured", e.isFeatured());
        out.put("installCount", e.getInstallCount());
        out.put("firstMessage", defaultString(e.getFirstMessage()));
        return out;
    }

    public static Map<String, Object> toUserDto(AgentEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("name", e.getName());
        out.put("description", defaultString(e.getDescription()));
        out.put("avatar", defaultString(e.getAvatar()));
        out.put("icon", defaultString(e.getIcon()));
        out.put("category", defaultString(e.getCategory()));
        out.put("tags", defaultString(e.getTags()));
        out.put("modelId", e.getModelId());
        out.put("systemPrompt", defaultString(e.getSystemPrompt()));
        out.put("firstMessage", defaultString(e.getFirstMessage()));
        out.put("contextMessagesJson", defaultString(e.getContextMessagesJson()));
        out.put("temperature", e.getTemperature());
        out.put("topP", e.getTopP());
        out.put("maxTokens", e.getMaxTokens());
        out.put("author", defaultString(e.getAuthor()));
        out.put("requiredToolsJson", defaultString(e.getRequiredToolsJson()));
        out.put("scope", e.getScope().name());
        out.put("sortOrder", e.getSortOrder());
        out.put("sourceAgentId", e.getSourceAgentId());
        out.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        return out;
    }

    public static Map<String, Object> toAdminDto(AgentEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("name", e.getName());
        out.put("description", defaultString(e.getDescription()));
        out.put("avatar", defaultString(e.getAvatar()));
        out.put("icon", defaultString(e.getIcon()));
        out.put("category", defaultString(e.getCategory()));
        out.put("tags", defaultString(e.getTags()));
        out.put("modelId", e.getModelId());
        out.put("model_id", e.getModelId());
        out.put("systemPrompt", defaultString(e.getSystemPrompt()));
        out.put("system_prompt", defaultString(e.getSystemPrompt()));
        out.put("firstMessage", defaultString(e.getFirstMessage()));
        out.put("first_message", defaultString(e.getFirstMessage()));
        out.put("contextMessagesJson", defaultString(e.getContextMessagesJson()));
        out.put("temperature", e.getTemperature());
        out.put("topP", e.getTopP());
        out.put("top_p", e.getTopP());
        out.put("maxTokens", e.getMaxTokens());
        out.put("max_tokens", e.getMaxTokens());
        out.put("scope", e.getScope().name());
        out.put("author", defaultString(e.getAuthor()));
        out.put("featured", e.isFeatured());
        out.put("installCount", e.getInstallCount());
        out.put("requiredToolsJson", defaultString(e.getRequiredToolsJson()));
        out.put("userId", e.getUserId() == null ? "" : e.getUserId().toString());
        out.put("user_id", e.getUserId() == null ? "" : e.getUserId().toString());
        out.put("sourceAgentId", e.getSourceAgentId() == null ? "" : e.getSourceAgentId().toString());
        out.put("enabled", e.isEnabled());
        out.put("sortOrder", e.getSortOrder());
        out.put("sort_order", e.getSortOrder());
        out.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        out.put("updatedAt", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return out;
    }

    // ========== Validation ==========

    private static void validateContextMessages(JsonNode root) {
        if (root == null || !root.isArray()) throw new IllegalArgumentException("contextMessagesJson invalid");
        if (root.size() > MAX_CONTEXT_MESSAGE_COUNT) throw new IllegalArgumentException("contextMessagesJson too many messages");
        int totalLength = 0;
        for (JsonNode item : root) {
            if (item == null || !item.isObject()) throw new IllegalArgumentException("contextMessagesJson invalid");
            JsonNode roleNode = item.get("role");
            JsonNode contentNode = item.get("content");
            if (roleNode == null || !roleNode.isTextual()) throw new IllegalArgumentException("contextMessagesJson invalid");
            String role = roleNode.asText();
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role))
                throw new IllegalArgumentException("contextMessagesJson invalid role");
            if (contentNode == null || !contentNode.isTextual()) throw new IllegalArgumentException("contextMessagesJson invalid");
            String content = contentNode.asText().trim();
            if (content.isEmpty() || content.length() > MAX_CONTEXT_MESSAGE_LENGTH)
                throw new IllegalArgumentException("contextMessagesJson message too long");
            totalLength += content.length();
            if (totalLength > MAX_CONTEXT_TOTAL_LENGTH)
                throw new IllegalArgumentException("contextMessagesJson total too long");
        }
    }

    // ========== Parsing utilities ==========

    private static String str(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        String v = RequestUtils.firstNonBlank(first, second);
        return blank(v) ? fallback : v;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static double parseDouble(Object value, double min, double max, String field) {
        double parsed;
        if (value instanceof Number n) parsed = n.doubleValue();
        else {
            try { parsed = Double.parseDouble(String.valueOf(value)); }
            catch (Exception e) { throw new IllegalArgumentException("invalid " + field); }
        }
        if (parsed < min || parsed > max) throw new IllegalArgumentException("invalid " + field);
        return parsed;
    }

    private static int parseInt(Object value, int min, int max, String field) {
        int parsed;
        if (value instanceof Number n) parsed = n.intValue();
        else {
            try { parsed = Integer.parseInt(String.valueOf(value)); }
            catch (Exception e) { throw new IllegalArgumentException("invalid " + field); }
        }
        if (parsed < min || parsed > max) throw new IllegalArgumentException("invalid " + field);
        return parsed;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        String text = str(value);
        if (text == null) return null;
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        return null;
    }
}
