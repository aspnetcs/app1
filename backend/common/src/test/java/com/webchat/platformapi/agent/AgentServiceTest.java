package com.webchat.platformapi.agent;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    @Test
    void listMarketWithoutCategoryKeepsFullMarketResponse() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);

        AgentEntity prompt = marketAgent("Prompt Coach", "prompt", 1);
        AgentEntity template = marketAgent("Template Builder", "template", 2);
        when(repository.findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(AgentScope.SYSTEM))
                .thenReturn(List.of(prompt, template));

        List<Map<String, Object>> result = service.listMarket();

        assertThat(result)
                .extracting(item -> item.get("name"))
                .containsExactly("Prompt Coach", "Template Builder");
        verify(repository).findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(AgentScope.SYSTEM);
        verify(repository, never())
                .findByScopeAndEnabledTrueAndDeletedAtIsNullAndNormalizedCategoryOrderBySortOrderAscCreatedAtDesc(any(), any());
    }

    @Test
    void listMarketWithCategoryPushesExactNormalizedFilterToRepository() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);

        AgentEntity prompt = marketAgent("Prompt Coach", "prompt", 1);
        when(repository.findByScopeAndEnabledTrueAndDeletedAtIsNullAndNormalizedCategoryOrderBySortOrderAscCreatedAtDesc(
                AgentScope.SYSTEM,
                "prompt"
        )).thenReturn(List.of(prompt));

        List<Map<String, Object>> result = service.listMarket("  PROMPT  ");

        assertThat(result)
                .extracting(item -> item.get("name"))
                .containsExactly("Prompt Coach");
        verify(repository).findByScopeAndEnabledTrueAndDeletedAtIsNullAndNormalizedCategoryOrderBySortOrderAscCreatedAtDesc(
                AgentScope.SYSTEM,
                "prompt"
        );
        verify(repository, never()).findByScopeAndEnabledTrueAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(any());
    }

    @Test
    void getAgentAllowsSystemAgentForAuthenticatedUser() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID requesterUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = marketAgent("Prompt Coach", "prompt", 1);
        entity.setId(agentId);
        entity.setSystemPrompt("system prompt");
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        Map<String, Object> result = service.getAgent(requesterUserId, agentId);

        assertThat(result.get("id")).isEqualTo(agentId);
        assertThat(result.get("scope")).isEqualTo("SYSTEM");
        assertThat(result.get("systemPrompt")).isEqualTo("system prompt");
        verify(repository).findByIdAndDeletedAtIsNull(agentId);
    }

    @Test
    void getAgentRejectsDisabledSystemAgentForUserFacingDetail() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID requesterUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = marketAgent("Prompt Coach", "prompt", 1);
        entity.setId(agentId);
        entity.setEnabled(false);
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getAgent(requesterUserId, agentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agent not found");

        verify(repository).findByIdAndDeletedAtIsNull(agentId);
    }

    @Test
    void getAgentAllowsOwnedUserAgentForOwner() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID requesterUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = userAgent("Private Coach", "prompt", requesterUserId);
        entity.setId(agentId);
        entity.setSystemPrompt("private system prompt");
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        Map<String, Object> result = service.getAgent(requesterUserId, agentId);

        assertThat(result.get("id")).isEqualTo(agentId);
        assertThat(result.get("scope")).isEqualTo("USER");
        assertThat(result.get("systemPrompt")).isEqualTo("private system prompt");
        verify(repository).findByIdAndDeletedAtIsNull(agentId);
    }

    @Test
    void getAgentRejectsNonOwnedUserAgent() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID requesterUserId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = userAgent("Private Coach", "prompt", ownerUserId);
        entity.setId(agentId);
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getAgent(requesterUserId, agentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agent not found");

        verify(repository).findByIdAndDeletedAtIsNull(agentId);
    }

    @Test
    void installAgentRejectsDisabledSystemAgentAsNotFound() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = marketAgent("Prompt Coach", "prompt", 1);
        entity.setId(agentId);
        entity.setEnabled(false);
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.installAgent(userId, agentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agent not found");

        verify(repository).findByIdAndDeletedAtIsNull(agentId);
        verify(repository, never()).incrementInstallCount(any());
        verify(repository, never()).save(any(AgentEntity.class));
    }

    @Test
    void installAgentRejectsForeignUserAgentAsNotFound() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID userId = UUID.randomUUID();
        UUID ownerUserId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = userAgent("Private Coach", "prompt", ownerUserId);
        entity.setId(agentId);
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.installAgent(userId, agentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("agent not found");

        verify(repository).findByIdAndDeletedAtIsNull(agentId);
        verify(repository, never()).incrementInstallCount(any());
        verify(repository, never()).save(any(AgentEntity.class));
    }

    @Test
    void createForAdminRejectsUserScopeWithoutUserId() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        Map<String, Object> body = minimalAdminBody();
        body.put("scope", "USER");

        assertThatThrownBy(() -> service.createForAdmin(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId required for USER scope");

        verify(repository, never()).save(any(AgentEntity.class));
    }

    @Test
    void updateForAdminRejectsUserScopeWithoutUserId() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID agentId = UUID.randomUUID();
        AgentEntity entity = marketAgent("Template Builder", "template", 1);
        entity.setId(agentId);
        entity.setScope(AgentScope.SYSTEM);
        when(repository.findByIdAndDeletedAtIsNull(agentId)).thenReturn(Optional.of(entity));

        Map<String, Object> body = minimalAdminBody();
        body.put("scope", "USER");

        assertThatThrownBy(() -> service.updateForAdmin(agentId, body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId required for USER scope");

        verify(repository).findByIdAndDeletedAtIsNull(agentId);
        verify(repository, never()).save(any(AgentEntity.class));
    }

    @Test
    void createForAdminClearsUserIdForSystemScope() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        UUID userId = UUID.randomUUID();
        ArgumentCaptor<AgentEntity> savedCaptor = ArgumentCaptor.forClass(AgentEntity.class);
        when(repository.save(any(AgentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> body = minimalAdminBody();
        body.put("scope", "SYSTEM");
        body.put("userId", userId.toString());

        Map<String, Object> result = service.createForAdmin(body);

        verify(repository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getScope()).isEqualTo(AgentScope.SYSTEM);
        assertThat(savedCaptor.getValue().getUserId()).isNull();
        assertThat(result.get("userId")).isEqualTo("");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listForAdminUsesExactNormalizedCategorySpecification() {
        AgentRepository repository = mock(AgentRepository.class);
        AgentService service = new AgentService(repository);
        ArgumentCaptor<Specification<AgentEntity>> specCaptor = ArgumentCaptor.forClass((Class) Specification.class);
        when(repository.findAll(specCaptor.capture(), eq(PageRequest.of(0, 25,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("sortOrder"),
                        org.springframework.data.domain.Sort.Order.desc("createdAt")
                )))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));

        service.listForAdmin(null, "  ProMpt  ", null, null, null, 0, 25);

        Specification<AgentEntity> specification = specCaptor.getValue();
        Root<AgentEntity> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path deletedAtPath = mock(Path.class);
        Path categoryPath = mock(Path.class);
        Expression<String> trimmedCategory = mock(Expression.class);
        Expression<String> loweredCategory = mock(Expression.class);
        Predicate notDeleted = mock(Predicate.class);
        Predicate exactCategory = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);

        when(root.get("deletedAt")).thenReturn(deletedAtPath);
        when(root.get("category")).thenReturn(categoryPath);
        when(cb.isNull(deletedAtPath)).thenReturn(notDeleted);
        when(cb.trim(categoryPath)).thenReturn(trimmedCategory);
        when(cb.lower(trimmedCategory)).thenReturn(loweredCategory);
        when(cb.equal(loweredCategory, "prompt")).thenReturn(exactCategory);
        when(cb.and(notDeleted, exactCategory)).thenReturn(combined);

        Predicate predicate = specification.toPredicate(root, query, cb);

        assertThat(predicate).isSameAs(combined);
        verify(cb).equal(loweredCategory, "prompt");
        verify(cb, never()).like(any(Expression.class), any(String.class));
    }

    private static Map<String, Object> minimalAdminBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Prompt Coach");
        body.put("modelId", "gpt-4o");
        body.put("category", "prompt");
        return body;
    }

    private static AgentEntity marketAgent(String name, String category, int sortOrder) {
        AgentEntity entity = new AgentEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope(AgentScope.SYSTEM);
        entity.setEnabled(true);
        entity.setName(name);
        entity.setCategory(category);
        entity.setModelId("gpt-4o");
        entity.setSortOrder(sortOrder);
        entity.setCreatedAt(Instant.parse("2026-03-24T00:00:00Z"));
        return entity;
    }

    private static AgentEntity userAgent(String name, String category, UUID userId) {
        AgentEntity entity = marketAgent(name, category, 1);
        entity.setScope(AgentScope.USER);
        entity.setUserId(userId);
        return entity;
    }
}
