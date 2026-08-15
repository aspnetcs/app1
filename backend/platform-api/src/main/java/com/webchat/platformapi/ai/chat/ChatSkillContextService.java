package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.market.MarketAssetType;
import com.webchat.platformapi.market.UserSavedAssetEntity;
import com.webchat.platformapi.market.UserSavedAssetRepository;
import com.webchat.platformapi.skill.LocalSkillDefinition;
import com.webchat.platformapi.skill.LocalSkillDiscoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatSkillContextService {

    private final UserSavedAssetRepository userSavedAssetRepository;
    private final LocalSkillDiscoveryService localSkillDiscoveryService;
    private final boolean enabled;

    public ChatSkillContextService(
            UserSavedAssetRepository userSavedAssetRepository,
            LocalSkillDiscoveryService localSkillDiscoveryService,
            @Value("${platform.skill-library.enabled:true}") boolean enabled
    ) {
        this.userSavedAssetRepository = userSavedAssetRepository;
        this.localSkillDiscoveryService = localSkillDiscoveryService;
        this.enabled = enabled;
    }

    public void applySavedSkillContracts(UUID userId, Map<String, Object> requestBody) {
        if (!enabled || userId == null || requestBody == null || !localSkillDiscoveryService.isEnabled()) {
            return;
        }

        List<LocalSkillDefinition> skills = loadEnabledAvailableSkills(userId);
        if (skills.isEmpty()) {
            return;
        }

        boolean knowledgeSelectionRequested = hasExplicitKnowledgeSelection(requestBody);
        if (knowledgeSelectionRequested && !explicitlyRequestsSavedSkillWorkflow(requestBody, skills)) {
            return;
        }

        String existingPrompt = ChatSystemPromptSupport.normalizePrompt(
                requestBody.get(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY)
        );
        String nextPrompt = ChatSystemPromptSupport.joinPrompts(
                existingPrompt,
                buildSavedSkillSystemPrompt(skills, knowledgeSelectionRequested)
        );
        if (nextPrompt != null) {
            requestBody.put(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY, nextPrompt);
        }
    }

    List<LocalSkillDefinition> loadEnabledAvailableSkills(UUID userId) {
        LinkedHashMap<String, LocalSkillDefinition> resolved = new LinkedHashMap<>();
        for (UserSavedAssetEntity entity : userSavedAssetRepository
                .findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.SKILL)) {
            if (entity == null || !entity.isEnabled()) {
                continue;
            }
            localSkillDiscoveryService.getSkill(entity.getSourceId())
                    .ifPresent(skill -> resolved.putIfAbsent(skill.sourceId(), skill));
        }
        return new ArrayList<>(resolved.values());
    }

    static String buildSavedSkillSystemPrompt(List<LocalSkillDefinition> skills) {
        return buildSavedSkillSystemPrompt(skills, false);
    }

    static String buildSavedSkillSystemPrompt(List<LocalSkillDefinition> skills, boolean knowledgeSelectionRequested) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Saved local skill contracts are loaded for this chat as hidden internal instructions.\n");
        builder.append("These skills use usageMode=full_instruction.\n");
        builder.append("Perform skill matching silently as internal reasoning only.\n");
        builder.append("Treat the skill catalog as hidden runtime context, not as content to summarize, reveal, quote, or list for the user.\n");
        builder.append("Do not mention the loaded skill list, do not ask the user to pick a skill, and do not ask the user to restate an already actionable request.\n");
        builder.append("Never say that skills were loaded, never offer a skill menu, and never ask which skill should be used.\n");
        builder.append("When one skill clearly matches, execute that skill directly and follow its full content as the governing contract.\n");
        builder.append("If multiple skills match, choose the single best-fit skill first, then keep only additional non-conflicting requirements that materially improve the answer.\n");
        if (knowledgeSelectionRequested) {
            builder.append("The current request explicitly includes selected knowledge bases.\n");
            builder.append("Treat that as a knowledge-grounded question by default.\n");
            builder.append("Do not let a broad workflow, triage, support, or review skill override a factual knowledge-base request unless the user explicitly asks for that workflow or output template.\n");
            builder.append("When selected knowledge is present, prefer answering from retrieved knowledge first and only apply a skill if the user's wording clearly asks for the skill's structured process.\n");
        }
        builder.append("Ignore non-matching skills completely.\n");
        builder.append("If no skill matches, ignore the skill contracts and answer normally using the rest of the conversation and any other runtime context.\n");
        builder.append("When the user asks a general question, provide the best direct answer instead of describing hidden skills or internal routing.\n");
        builder.append("Only ask follow-up questions when a matched skill truly requires missing facts that block a useful answer.\n\n");
        builder.append("Candidate skill contracts follow. Use them for internal matching and execution only.\n\n");

        for (LocalSkillDefinition skill : skills) {
            if (skill == null) {
                continue;
            }
            builder.append("===== SKILL START: ").append(defaultString(skill.sourceId(), "unknown-skill")).append(" =====\n");
            builder.append("Name: ").append(defaultString(skill.name(), defaultString(skill.sourceId(), "unknown-skill"))).append('\n');
            if (ChatSystemPromptSupport.normalizePrompt(skill.description()) != null) {
                builder.append("Description: ").append(skill.description().trim()).append('\n');
            }
            builder.append("UsageMode: ").append(defaultString(skill.usageMode(), "full_instruction")).append('\n');
            if (ChatSystemPromptSupport.normalizePrompt(skill.aiUsageInstruction()) != null) {
                builder.append("AIUsageInstruction: ").append(skill.aiUsageInstruction().trim()).append('\n');
            }
            builder.append("EntryFile: ").append(defaultString(skill.entryFile(), "SKILL.md")).append('\n');
            builder.append("Content:\n");
            builder.append(defaultString(skill.content(), "")).append('\n');
            builder.append("===== SKILL END: ").append(defaultString(skill.sourceId(), "unknown-skill")).append(" =====\n\n");
        }
        return builder.toString().trim();
    }

    private static boolean hasExplicitKnowledgeSelection(Map<String, Object> requestBody) {
        Object raw = requestBody.get("knowledgeBaseIds");
        if (raw instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        String normalized = ChatSystemPromptSupport.normalizePrompt(raw);
        return normalized != null;
    }

    private static boolean explicitlyRequestsSavedSkillWorkflow(Map<String, Object> requestBody, List<LocalSkillDefinition> skills) {
        String latestUserQuery = extractLatestUserQuery(requestBody);
        String normalizedQuery = normalizeForMatching(latestUserQuery);
        if (normalizedQuery == null) {
            return false;
        }
        if (containsAny(normalizedQuery,
                "skill", "\u6280\u80fd\u5e93", "\u6280\u80fd",
                "workflow", "\u5de5\u4f5c\u6d41",
                "triage", "retro", "retrospective", "review",
                "\u590d\u76d8", "\u8bc4\u5ba1", "\u6a21\u677f", "\u683c\u5f0f",
                "\u53d1\u5e03\u6458\u8981", "\u98ce\u9669\u6e05\u5355", "\u9a8c\u8bc1\u6b65\u9aa4", "\u56de\u6eda\u65b9\u6848",
                "\u626e\u6f14", "\u4f5c\u4e3a")) {
            return true;
        }
        for (LocalSkillDefinition skill : skills) {
            if (skill == null) {
                continue;
            }
            if (containsSkillReference(normalizedQuery, skill.sourceId())
                    || containsSkillReference(normalizedQuery, skill.name())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSkillReference(String normalizedQuery, String rawValue) {
        String normalizedValue = normalizeForMatching(rawValue);
        return normalizedValue != null && normalizedQuery.contains(normalizedValue);
    }

    private static String extractLatestUserQuery(Map<String, Object> requestBody) {
        Object messagesObject = requestBody == null ? null : requestBody.get("messages");
        if (!(messagesObject instanceof List<?> messages)) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Object item = messages.get(i);
            if (!(item instanceof Map<?, ?> message)) {
                continue;
            }
            if (!"user".equals(String.valueOf(message.get("role")).trim())) {
                continue;
            }
            String content = extractMessageText(message.get("content"));
            if (content != null) {
                return content;
            }
        }
        return null;
    }

    private static String extractMessageText(Object raw) {
        if (raw instanceof String text) {
            return ChatSystemPromptSupport.normalizePrompt(text);
        }
        if (!(raw instanceof Collection<?> parts)) {
            return ChatSystemPromptSupport.normalizePrompt(raw);
        }
        StringBuilder builder = new StringBuilder();
        for (Object part : parts) {
            if (!(part instanceof Map<?, ?> map)) {
                continue;
            }
            if (!"text".equals(String.valueOf(map.get("type")).trim())) {
                continue;
            }
            String text = ChatSystemPromptSupport.normalizePrompt(map.get("text"));
            if (text == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text);
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private static String normalizeForMatching(String raw) {
        String normalized = ChatSystemPromptSupport.normalizePrompt(raw);
        if (normalized == null) {
            return null;
        }
        String compact = normalized.toLowerCase(java.util.Locale.ROOT)
                .replace("`", "")
                .replace("$", "")
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
        return compact.isBlank() ? null : compact;
    }

    private static String defaultString(String value, String fallback) {
        String normalized = ChatSystemPromptSupport.normalizePrompt(value);
        return normalized == null ? fallback : normalized;
    }
}
