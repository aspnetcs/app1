package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.market.MarketAssetType;
import com.webchat.platformapi.market.UserSavedAssetEntity;
import com.webchat.platformapi.market.UserSavedAssetRepository;
import com.webchat.platformapi.skill.LocalSkillDefinition;
import com.webchat.platformapi.skill.LocalSkillDiscoveryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSkillContextServiceTest {

    @Test
    void applySavedSkillContractsLoadsEnabledAvailableSkillsOnly() {
        UserSavedAssetRepository repository = mock(UserSavedAssetRepository.class);
        LocalSkillDiscoveryService discoveryService = mock(LocalSkillDiscoveryService.class);
        ChatSkillContextService service = new ChatSkillContextService(repository, discoveryService, true);
        UUID userId = UUID.randomUUID();

        UserSavedAssetEntity enabledSkill = savedSkill(userId, "master-task-executor", true);
        UserSavedAssetEntity disabledSkill = savedSkill(userId, "disabled-skill", false);
        UserSavedAssetEntity missingSkill = savedSkill(userId, "missing-skill", true);

        when(discoveryService.isEnabled()).thenReturn(true);
        when(repository.findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.SKILL))
                .thenReturn(List.of(enabledSkill, disabledSkill, missingSkill));
        when(discoveryService.getSkill("master-task-executor")).thenReturn(Optional.of(new LocalSkillDefinition(
                "master-task-executor",
                "Master Task Executor",
                "Execute master task documents",
                "C:/repo/.agents/skills/master-task-executor/SKILL.md",
                "master-task-executor/SKILL.md",
                "SKILL.md",
                "# Skill body",
                "# Skill body",
                12L,
                "full_instruction",
                "Follow the full skill content when matched.",
                Map.of("name", "Master Task Executor")
        )));
        when(discoveryService.getSkill("missing-skill")).thenReturn(Optional.empty());

        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("messages", List.of(Map.of("role", "user", "content", "Start executing the task plan.")));

        service.applySavedSkillContracts(userId, request);

        String prompt = String.valueOf(request.get(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY));
        assertThat(prompt).contains("master-task-executor");
        assertThat(prompt).contains("Follow the full skill content when matched.");
        assertThat(prompt).contains("Perform skill matching silently as internal reasoning only.");
        assertThat(prompt).contains("Do not mention the loaded skill list");
        assertThat(prompt).contains("Never say that skills were loaded");
        assertThat(prompt).contains("choose the single best-fit skill first");
        assertThat(prompt).contains("If no skill matches, ignore the skill contracts and answer normally");
        assertThat(prompt).contains("provide the best direct answer instead of describing hidden skills or internal routing");
        assertThat(prompt).doesNotContain("disabled-skill");
        assertThat(prompt).doesNotContain("missing-skill");
    }

    @Test
    void buildSavedSkillSystemPromptKeepsSkillRoutingHiddenFromUsers() {
        String prompt = ChatSkillContextService.buildSavedSkillSystemPrompt(List.of(new LocalSkillDefinition(
                "release-risk-reviewer",
                "Release Risk Reviewer",
                "Review release risk and rollback strategy",
                "C:/repo/.agents/skills/release-risk-reviewer/SKILL.md",
                "release-risk-reviewer/SKILL.md",
                "SKILL.md",
                "# Release skill",
                "# Release skill",
                8L,
                "full_instruction",
                "When the user asks for a release review, follow this skill completely.",
                Map.of("name", "Release Risk Reviewer")
        )));

        assertThat(prompt).contains("hidden internal instructions");
        assertThat(prompt).contains("Treat the skill catalog as hidden runtime context");
        assertThat(prompt).contains("Never say that skills were loaded, never offer a skill menu, and never ask which skill should be used.");
        assertThat(prompt).contains("When one skill clearly matches, execute that skill directly");
        assertThat(prompt).contains("If multiple skills match, choose the single best-fit skill first");
        assertThat(prompt).contains("When the user asks a general question, provide the best direct answer instead of describing hidden skills or internal routing.");
        assertThat(prompt).contains("AIUsageInstruction: When the user asks for a release review, follow this skill completely.");
        assertThat(prompt).contains("===== SKILL START: release-risk-reviewer =====");
    }

    @Test
    void buildSavedSkillSystemPromptPrefersExplicitKnowledgeRequests() {
        String prompt = ChatSkillContextService.buildSavedSkillSystemPrompt(List.of(new LocalSkillDefinition(
                "customer-support-triage",
                "Customer Support Triage",
                "Turn support requests into structured tickets",
                "C:/repo/.agents/skills/customer-support-triage/SKILL.md",
                "customer-support-triage/SKILL.md",
                "SKILL.md",
                "# Support skill",
                "# Support skill",
                8L,
                "full_instruction",
                "When the user asks for support triage, follow this skill completely.",
                Map.of("name", "Customer Support Triage")
        )), true);

        assertThat(prompt).contains("The current request explicitly includes selected knowledge bases.");
        assertThat(prompt).contains("Do not let a broad workflow, triage, support, or review skill override a factual knowledge-base request");
        assertThat(prompt).contains("prefer answering from retrieved knowledge first");
    }

    @Test
    void applySavedSkillContractsSkipsBroadSkillWhenKnowledgeIsExplicitlySelected() {
        UserSavedAssetRepository repository = mock(UserSavedAssetRepository.class);
        LocalSkillDiscoveryService discoveryService = mock(LocalSkillDiscoveryService.class);
        ChatSkillContextService service = new ChatSkillContextService(repository, discoveryService, true);
        UUID userId = UUID.randomUUID();

        UserSavedAssetEntity enabledSkill = savedSkill(userId, "customer-support-triage", true);
        when(discoveryService.isEnabled()).thenReturn(true);
        when(repository.findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.SKILL))
                .thenReturn(List.of(enabledSkill));
        when(discoveryService.getSkill("customer-support-triage")).thenReturn(Optional.of(new LocalSkillDefinition(
                "customer-support-triage",
                "Customer Support Triage",
                "Turn support requests into structured tickets",
                "C:/repo/.agents/skills/customer-support-triage/SKILL.md",
                "customer-support-triage/SKILL.md",
                "SKILL.md",
                "# Support skill",
                "# Support skill",
                8L,
                "full_instruction",
                "When the user asks for support triage, follow this skill completely.",
                Map.of("name", "Customer Support Triage")
        )));

        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("knowledgeBaseIds", List.of(UUID.randomUUID().toString()));
        request.put("messages", List.of(Map.of(
                "role", "user",
                "content", "Please answer from the selected knowledge base: how should account recovery work when the user forgot the phone number?"
        )));

        service.applySavedSkillContracts(userId, request);

        assertThat(request).doesNotContainKey(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY);
    }

    @Test
    void applySavedSkillContractsKeepsSkillWhenKnowledgeRequestExplicitlyAsksForWorkflowOutput() {
        UserSavedAssetRepository repository = mock(UserSavedAssetRepository.class);
        LocalSkillDiscoveryService discoveryService = mock(LocalSkillDiscoveryService.class);
        ChatSkillContextService service = new ChatSkillContextService(repository, discoveryService, true);
        UUID userId = UUID.randomUUID();

        UserSavedAssetEntity enabledSkill = savedSkill(userId, "release-risk-reviewer", true);
        when(discoveryService.isEnabled()).thenReturn(true);
        when(repository.findByUserIdAndAssetTypeOrderBySortOrderAscCreatedAtDesc(userId, MarketAssetType.SKILL))
                .thenReturn(List.of(enabledSkill));
        when(discoveryService.getSkill("release-risk-reviewer")).thenReturn(Optional.of(new LocalSkillDefinition(
                "release-risk-reviewer",
                "Release Risk Reviewer",
                "Review release risk and rollback strategy",
                "C:/repo/.agents/skills/release-risk-reviewer/SKILL.md",
                "release-risk-reviewer/SKILL.md",
                "SKILL.md",
                "# Release skill",
                "# Release skill",
                8L,
                "full_instruction",
                "When the user asks for a release review, follow this skill completely.",
                Map.of("name", "Release Risk Reviewer")
        )));

        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("knowledgeBaseIds", List.of(UUID.randomUUID().toString()));
        request.put("messages", List.of(Map.of(
                "role", "user",
                "content", "Please use the selected knowledge base and output a release review with release summary, risk checklist, validation steps, and rollback plan."
        )));

        service.applySavedSkillContracts(userId, request);

        String prompt = String.valueOf(request.get(ChatSystemPromptSupport.CONTEXT_SYSTEM_PROMPT_KEY));
        assertThat(prompt).contains("release-risk-reviewer");
        assertThat(prompt).contains("The current request explicitly includes selected knowledge bases.");
    }

    private static UserSavedAssetEntity savedSkill(UUID userId, String sourceId, boolean enabled) {
        UserSavedAssetEntity entity = new UserSavedAssetEntity();
        entity.setUserId(userId);
        entity.setAssetType(MarketAssetType.SKILL);
        entity.setSourceId(sourceId);
        entity.setEnabled(enabled);
        return entity;
    }
}
