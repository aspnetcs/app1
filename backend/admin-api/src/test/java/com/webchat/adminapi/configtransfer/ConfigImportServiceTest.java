package com.webchat.adminapi.configtransfer;

import com.webchat.adminapi.auth.oauth.OAuthRuntimeConfigService;
import com.webchat.adminapi.group.UserGroupAdminService;
import com.webchat.platformapi.agent.AgentRepository;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataRepository;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.admin.ops.BannerService;
import com.webchat.platformapi.admin.ops.SysBannerRepository;
import com.webchat.platformapi.auth.group.SysUserGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigImportServiceTest {

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
    private AiModelMetadataService modelMetadataService;

    @Mock
    private AgentService agentService;

    @Mock
    private BannerService bannerService;

    @Mock
    private UserGroupAdminService userGroupService;

    @Mock
    private OAuthRuntimeConfigService oauthRuntimeConfigService;

    @Mock
    private ConfigSchemaValidator configSchemaValidator;

    private ConfigImportService service;

    @BeforeEach
    void setUp() {
        service = new ConfigImportService(
                channelRepository,
                modelMetadataRepository,
                agentRepository,
                bannerRepository,
                groupRepository,
                modelMetadataService,
                agentService,
                bannerService,
                userGroupService,
                oauthRuntimeConfigService,
                configSchemaValidator
        );
    }

    @Test
    void importChannelsDropsSecretLikeExtraConfigFieldsBeforePersisting() {
        String openAiKey = "sk-abcdefghijklmnopqrstuvwxyz0123456789";
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("apiKey", openAiKey);
        extra.put("nested", Map.of("token", "should-not-leak", "ok", "v"));
        extra.put("nonSecretButBearer", "Bearer should-not-leak");

        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("name", "Test");
        channel.put("type", "openai");
        channel.put("baseUrl", "https://example.test");
        channel.put("extraConfig", extra);

        ConfigTransferPayload payload = new ConfigTransferPayload(
                1,
                "2026-04-18T00:00:00Z",
                false,
                List.of("channels"),
                Map.of("channels", List.of(channel)),
                Map.of()
        );

        when(configSchemaValidator.readList(eq(payload), eq("channels"))).thenReturn(List.of(channel));
        doNothing().when(configSchemaValidator).validateChannelItem(any());
        when(channelRepository.findFirstByNameAndTypeAndBaseUrl("Test", "openai", "https://example.test"))
                .thenReturn(Optional.empty());
        when(channelRepository.save(any(AiChannelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.importModule("channels", payload, new java.util.ArrayList<>());

        ArgumentCaptor<AiChannelEntity> captor = ArgumentCaptor.forClass(AiChannelEntity.class);
        verify(channelRepository).save(captor.capture());
        AiChannelEntity saved = captor.getValue();
        assertNotNull(saved);

        Map<String, Object> savedExtra = saved.getExtraConfig();
        assertNotNull(savedExtra);
        assertFalse(savedExtra.containsKey("apiKey"));
        @SuppressWarnings("unchecked")
        Map<String, Object> savedNested = (Map<String, Object>) savedExtra.get("nested");
        assertFalse(savedNested.containsKey("token"));
        assertEquals("v", savedNested.get("ok"));
        assertEquals("***", savedExtra.get("nonSecretButBearer"));
    }
}
