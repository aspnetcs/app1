package com.webchat.adminapi.configtransfer;

import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.adminapi.group.UserGroupAdminService;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataRepository;
import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.admin.ops.SysBannerRepository;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigExportServiceTest {

    @Mock
    private AiChannelRepository channelRepository;

    @Mock
    private AiModelMetadataRepository modelMetadataRepository;

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private SysBannerRepository bannerRepository;

    @Mock
    private SysUserGroupRepository groupRepository;

    @Mock
    private AgentService agentService;

    @Mock
    private BannerService bannerService;

    @Mock
    private UserGroupAdminService userGroupService;

    @Mock
    private OAuthRuntimeConfigService oauthRuntimeConfigService;

    private ConfigExportService service;

    @BeforeEach
    void setUp() {
        service = new ConfigExportService(
                channelRepository,
                modelMetadataRepository,
                agentRepository,
                bannerRepository,
                groupRepository,
                agentService,
                bannerService,
                userGroupService,
                oauthRuntimeConfigService
        );
    }

    @Test
    void exportChannelsRedactsSecretLikeExtraConfigFields() {
        AiChannelEntity entity = new AiChannelEntity();
        entity.setId(1L);
        entity.setName("Test");
        entity.setType("openai");
        entity.setBaseUrl("https://example.test");

        String openAiKey = "sk-abcdefghijklmnopqrstuvwxyz0123456789";
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("apiKey", openAiKey);
        extra.put("nested", Map.of("apiSecret", "should-not-leak", "ok", "v"));
        extra.put("nonSecretButBearer", "Bearer should-not-leak");
        entity.setExtraConfig(extra);

        when(channelRepository.findAll()).thenReturn(List.of(entity));

        Object exported = service.exportModule("channels");
        assertTrue(exported instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) exported;
        assertEquals(1, items.size());

        Object rawExtraConfig = items.get(0).get("extraConfig");
        assertNotNull(rawExtraConfig);
        assertTrue(rawExtraConfig instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> exportedExtra = (Map<String, Object>) rawExtraConfig;

        assertFalse(exportedExtra.containsKey("apiKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> exportedNested = (Map<String, Object>) exportedExtra.get("nested");
        assertFalse(exportedNested.containsKey("apiSecret"));
        assertEquals("v", exportedNested.get("ok"));
        assertEquals("***", exportedExtra.get("nonSecretButBearer"));
    }
}
