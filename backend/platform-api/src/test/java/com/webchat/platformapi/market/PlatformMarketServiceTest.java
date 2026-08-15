package com.webchat.platformapi.market;

import com.webchat.platformapi.agent.AgentEntity;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentScope;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.market.ContextAssetContract;
import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.knowledge.KnowledgeBaseRepository;
import com.webchat.platformapi.knowledge.KnowledgeDocumentRepository;
import com.webchat.platformapi.ai.extension.McpServerRepository;
import com.webchat.platformapi.skill.LocalSkillDiscoveryService;
import com.webchat.platformapi.skill.LocalSkillDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformMarketServiceTest {

    @Mock
    private MarketCatalogItemRepository catalogRepository;
    @Mock
    private UserSavedAssetRepository savedAssetRepository;
    @Mock
    private AgentRepository agentRepository;
    @Mock
    private AgentService agentService;
    @Mock
    private McpServerRepository mcpServerRepository;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private KnowledgeDocumentRepository knowledgeDocumentRepository;
    @Mock
    private LocalSkillDiscoveryService localSkillDiscoveryService;
    @Mock
    private SysConfigService sysConfigService;

    private PlatformMarketService service;

    @BeforeEach
    void setUp() {
        service = new PlatformMarketService(
                catalogRepository,
                savedAssetRepository,
                agentRepository,
                agentService,
                mcpServerRepository,
                knowledgeBaseRepository,
                knowledgeDocumentRepository,
                localSkillDiscoveryService,
                sysConfigService
        );
        when(sysConfigService.getBoolean(PlatformMarketService.KEY_MARKET_ENABLED, true)).thenReturn(true);
    }

    @Test
    void saveAgentAssetCreatesSavedRelationAndStoresInstalledCloneId() {
        UUID userId = UUID.randomUUID();
        UUID sourceAgentId = UUID.randomUUID();
        UUID installedAgentId = UUID.randomUUID();

        when(savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(eq(userId), eq(MarketAssetType.AGENT), eq(sourceAgentId.toString())))
                .thenReturn(Optional.empty());
        when(agentService.installAgent(eq(userId), eq(sourceAgentId))).thenReturn(Map.of("id", installedAgentId.toString()));
        when(savedAssetRepository.save(any(UserSavedAssetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentEntity agent = new AgentEntity();
        agent.setId(sourceAgentId);
        agent.setScope(AgentScope.SYSTEM);
        agent.setEnabled(true);
        agent.setName("Writer");
        when(agentRepository.findByIdAndDeletedAtIsNull(eq(sourceAgentId))).thenReturn(Optional.of(agent));
        when(catalogRepository.findByAssetTypeAndSourceId(eq(MarketAssetType.AGENT), eq(sourceAgentId.toString())))
                .thenReturn(Optional.empty());

        Map<String, Object> payload = service.saveAsset(userId, MarketAssetType.AGENT, sourceAgentId.toString());

        assertEquals("AGENT", payload.get("assetType"));
        assertEquals("install", payload.get("saveMode"));
        assertEquals(installedAgentId.toString(), ((Map<?, ?>) payload.get("extraConfig")).get("installedAgentId"));
    }

    @Test
    void listAssetsFiltersPublishedCatalogEntries() {
        UUID userId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        MarketCatalogItemEntity catalogItem = new MarketCatalogItemEntity();
        catalogItem.setAssetType(MarketAssetType.AGENT);
        catalogItem.setSourceId(agentId.toString());
        catalogItem.setTitle("Writer");
        catalogItem.setSummary("Writing helper");
        when(catalogRepository.findByEnabledTrueOrderByFeaturedDescSortOrderAscCreatedAtDesc()).thenReturn(List.of(catalogItem));
        when(savedAssetRepository.findByUserIdOrderBySortOrderAscCreatedAtDesc(eq(userId))).thenReturn(List.of());

        AgentEntity agent = new AgentEntity();
        agent.setId(agentId);
        agent.setScope(AgentScope.SYSTEM);
        agent.setEnabled(true);
        agent.setName("Writer");
        agent.setDescription("Writing helper");
        when(agentRepository.findByIdAndDeletedAtIsNull(eq(agentId))).thenReturn(Optional.of(agent));

        List<Map<String, Object>> items = service.listAssets(userId, "AGENT", "writer");

        assertEquals(1, items.size());
        assertEquals("Writer", items.get(0).get("title"));
    }

    @Test
    void saveKnowledgeAssetRequiresOwnedKnowledgeBase() {
        UUID userId = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();
        when(savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(eq(userId), eq(MarketAssetType.KNOWLEDGE), eq(baseId.toString())))
                .thenReturn(Optional.empty());
        when(knowledgeBaseRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(eq(baseId), eq(userId))).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.saveAsset(userId, MarketAssetType.KNOWLEDGE, baseId.toString()));
    }

    @Test
    void saveSkillAssetUsesLocalDiscoveryContract() {
        UUID userId = UUID.randomUUID();
        when(savedAssetRepository.findByUserIdAndAssetTypeAndSourceId(eq(userId), eq(MarketAssetType.SKILL), eq("demo-skill")))
                .thenReturn(Optional.empty());
        when(savedAssetRepository.save(any(UserSavedAssetEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogRepository.findByAssetTypeAndSourceId(eq(MarketAssetType.SKILL), eq("demo-skill"))).thenReturn(Optional.empty());
        when(localSkillDiscoveryService.getSkill("demo-skill")).thenReturn(Optional.of(new LocalSkillDefinition(
                "demo-skill",
                "Demo Skill",
                "Full instruction skill",
                "C:/repo/.agents/skills/demo-skill/SKILL.md",
                "demo-skill/SKILL.md",
                "SKILL.md",
                "# Demo",
                "# Demo",
                32L,
                ContextAssetContract.USAGE_MODE_FULL_INSTRUCTION,
                ContextAssetContract.skillUsageInstruction(),
                Map.of("name", "demo-skill")
        )));

        Map<String, Object> payload = service.saveAsset(userId, MarketAssetType.SKILL, "demo-skill");

        assertEquals("SKILL", payload.get("assetType"));
        assertEquals(ContextAssetContract.USAGE_MODE_FULL_INSTRUCTION, payload.get("usageMode"));
        assertEquals(true, payload.get("available"));
    }
}
