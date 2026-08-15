package com.webchat.adminapi;

import com.webchat.adminapi.agent.AgentAdminController;
import com.webchat.adminapi.ai.controller.ModelAdminController;
import com.webchat.adminapi.banner.BannerAdminController;
import com.webchat.adminapi.configtransfer.ConfigTransferAdminController;
import com.webchat.adminapi.configtransfer.ConfigTransferService;

import com.webchat.adminapi.group.UserGroupAdminController;
import com.webchat.adminapi.group.UserGroupAdminService;
import com.webchat.adminapi.mcp.McpAdminController;
import com.webchat.adminapi.translation.TranslationAdminController;
import com.webchat.adminapi.voicechat.VoiceChatAdminController;
import com.webchat.adminapi.webread.WebReadAdminController;
import com.webchat.platformapi.agent.AgentService;
import com.webchat.platformapi.ai.audio.VoiceChatProperties;
import com.webchat.platformapi.ai.extension.McpClientService;
import com.webchat.platformapi.ai.extension.McpProperties;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.model.AiModelCapabilityResolver;
import com.webchat.platformapi.ai.model.AiModelMetadataService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.texttransform.TranslationService;
import com.webchat.platformapi.admin.ops.BannerService;

import com.webchat.platformapi.config.SysConfigService;
import com.webchat.platformapi.webread.WebReadService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminFeatureFlagControllerRegistrationTest {

    private final ApplicationContextRunner agentRunner =
            new ApplicationContextRunner().withUserConfiguration(AgentAdminControllerConfig.class);
    private final ApplicationContextRunner bannerRunner =
            new ApplicationContextRunner().withUserConfiguration(BannerAdminControllerConfig.class);

    private final ApplicationContextRunner webReadRunner =
            new ApplicationContextRunner().withUserConfiguration(WebReadAdminControllerConfig.class);
    private final ApplicationContextRunner userGroupRunner =
            new ApplicationContextRunner().withUserConfiguration(UserGroupAdminControllerConfig.class);
    private final ApplicationContextRunner modelRunner =
            new ApplicationContextRunner().withUserConfiguration(ModelAdminControllerConfig.class);
    private final ApplicationContextRunner mcpRunner =
            new ApplicationContextRunner().withUserConfiguration(McpAdminControllerConfig.class);
    private final ApplicationContextRunner voiceChatRunner =
            new ApplicationContextRunner().withUserConfiguration(VoiceChatAdminControllerConfig.class);
    private final ApplicationContextRunner translationRunner =
            new ApplicationContextRunner().withUserConfiguration(TranslationAdminControllerConfig.class);
    private final ApplicationContextRunner configTransferRunner =
            new ApplicationContextRunner().withUserConfiguration(ConfigTransferAdminControllerConfig.class);

    @Test
    void controllersBackOffWhenDevPanelIsFalse() {
        assertControllerBacksOffWhenDevPanelIsFalse(agentRunner, AgentAdminController.class);

        assertControllerBacksOffWhenDevPanelIsFalse(bannerRunner, BannerAdminController.class);

        assertControllerBacksOffWhenDevPanelIsFalse(webReadRunner, WebReadAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(userGroupRunner, UserGroupAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(modelRunner, ModelAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(mcpRunner, McpAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(voiceChatRunner, VoiceChatAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(translationRunner, TranslationAdminController.class);
        assertControllerBacksOffWhenDevPanelIsFalse(configTransferRunner, ConfigTransferAdminController.class);
    }

    @Test
    void agentControllerRegistersWhenFeatureFlagIsMissing() {
        agentRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(AgentAdminController.class)).hasSize(1));
    }



    @Test
    void bannerControllerRegistersWhenFeatureFlagIsMissing() {
        bannerRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(BannerAdminController.class)).hasSize(1));
    }

    @Test
    void bannerControllerBacksOffWhenFeatureFlagIsFalse() {
        bannerRunner.withPropertyValues("platform.dev-panel=true", "platform.banner.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(BannerAdminController.class)).isEmpty());
    }



    @Test
    void webReadControllerRegistersWhenFeatureFlagIsMissing() {
        webReadRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(WebReadAdminController.class)).hasSize(1));
    }

    @Test
    void webReadControllerBacksOffWhenFeatureFlagIsFalse() {
        webReadRunner.withPropertyValues("platform.dev-panel=true", "platform.web-read.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(WebReadAdminController.class)).isEmpty());
    }

    @Test
    void userGroupControllerRegistersWhenFeatureFlagIsMissing() {
        userGroupRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(UserGroupAdminController.class)).hasSize(1));
    }

    @Test
    void userGroupControllerBacksOffWhenFeatureFlagIsFalse() {
        userGroupRunner.withPropertyValues("platform.dev-panel=true", "platform.user-groups.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(UserGroupAdminController.class)).isEmpty());
    }

    @Test
    void modelControllerRegistersWhenFeatureFlagIsMissing() {
        modelRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(ModelAdminController.class)).hasSize(1));
    }

    @Test
    void modelControllerBacksOffWhenFeatureFlagIsFalse() {
        modelRunner.withPropertyValues("platform.dev-panel=true", "platform.model-metadata.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ModelAdminController.class)).isEmpty());
    }

    @Test
    void mcpControllerRegistersWhenFeatureFlagIsMissing() {
        mcpRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(McpAdminController.class)).hasSize(1));
    }

    @Test
    void mcpControllerBacksOffWhenFeatureFlagIsFalse() {
        mcpRunner.withPropertyValues("platform.dev-panel=true", "platform.mcp.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(McpAdminController.class)).isEmpty());
    }

    @Test
    void voiceChatControllerRegistersWhenFeatureFlagIsMissing() {
        voiceChatRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(VoiceChatAdminController.class)).hasSize(1));
    }

    @Test
    void voiceChatControllerBacksOffWhenFeatureFlagIsFalse() {
        voiceChatRunner.withPropertyValues("platform.dev-panel=true", "platform.voice-chat.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(VoiceChatAdminController.class)).isEmpty());
    }

    @Test
    void translationControllerRegistersWhenFeatureFlagIsMissing() {
        translationRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(TranslationAdminController.class)).hasSize(1));
    }

    @Test
    void translationControllerBacksOffWhenFeatureFlagIsFalse() {
        translationRunner.withPropertyValues("platform.dev-panel=true", "platform.translation.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(TranslationAdminController.class)).isEmpty());
    }

    @Test
    void configTransferControllerRegistersWhenFeatureFlagIsMissing() {
        configTransferRunner.withPropertyValues("platform.dev-panel=true")
                .run(context -> assertThat(context.getBeansOfType(ConfigTransferAdminController.class)).hasSize(1));
    }

    @Test
    void configTransferControllerBacksOffWhenFeatureFlagIsFalse() {
        configTransferRunner.withPropertyValues("platform.dev-panel=true", "platform.config-transfer.enabled=false")
                .run(context -> assertThat(context.getBeansOfType(ConfigTransferAdminController.class)).isEmpty());
    }

    private static <T> void assertControllerBacksOffWhenDevPanelIsFalse(ApplicationContextRunner runner, Class<T> controllerType) {
        runner.withPropertyValues("platform.dev-panel=false")
                .run(context -> assertThat(context.getBeansOfType(controllerType)).isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AgentAdminController.class)
    static class AgentAdminControllerConfig {
        @Bean
        AgentService agentService() {
            return mock(AgentService.class);
        }
    }



    @Configuration(proxyBeanMethods = false)
    @Import(BannerAdminController.class)
    static class BannerAdminControllerConfig {
        @Bean
        BannerService bannerService() {
            return mock(BannerService.class);
        }
    }



    @Configuration(proxyBeanMethods = false)
    @Import(WebReadAdminController.class)
    static class WebReadAdminControllerConfig {
        @Bean
        WebReadService webReadService() {
            return mock(WebReadService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(UserGroupAdminController.class)
    static class UserGroupAdminControllerConfig {
        @Bean
        UserGroupAdminService userGroupAdminService() {
            return mock(UserGroupAdminService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ModelAdminController.class)
    static class ModelAdminControllerConfig {
        @Bean
        AiModelMetadataService aiModelMetadataService() {
            return mock(AiModelMetadataService.class);
        }
        @Bean
        ChannelRouter channelRouter() {
            return mock(ChannelRouter.class);
        }
        @Bean
        ChannelMonitor channelMonitor() {
            return mock(ChannelMonitor.class);
        }
        @Bean
        AiModelCapabilityResolver aiModelCapabilityResolver() {
            return new AiModelCapabilityResolver();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(McpAdminController.class)
    static class McpAdminControllerConfig {
        @Bean
        McpClientService mcpClientService() {
            McpClientService service = mock(McpClientService.class);
            when(service.getProperties()).thenReturn(new McpProperties());
            return service;
        }

        @Bean
        AiCryptoService aiCryptoService() {
            return mock(AiCryptoService.class);
        }

        @Bean
        SysConfigService sysConfigService() {
            SysConfigService service = mock(SysConfigService.class);
            when(service.get(anyString())).thenReturn(Optional.empty());
            return service;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(VoiceChatAdminController.class)
    static class VoiceChatAdminControllerConfig {
        @Bean
        VoiceChatProperties voiceChatProperties() {
            return new VoiceChatProperties();
        }

        @Bean
        SysConfigService sysConfigService() {
            return mock(SysConfigService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(TranslationAdminController.class)
    static class TranslationAdminControllerConfig {
        @Bean
        TranslationService translationService() {
            return mock(TranslationService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ConfigTransferAdminController.class)
    static class ConfigTransferAdminControllerConfig {
        @Bean
        ConfigTransferService configTransferService() {
            return mock(ConfigTransferService.class);
        }
    }
}
