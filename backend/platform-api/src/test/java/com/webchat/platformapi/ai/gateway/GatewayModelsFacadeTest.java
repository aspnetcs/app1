package com.webchat.platformapi.ai.gateway;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.auth.role.RolePolicyService;
import com.webchat.platformapi.user.UserEntity;
import com.webchat.platformapi.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayModelsFacadeTest {

    @Test
    void modelsRejectAnonymousUser() {
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        UserRepository userRepository = mock(UserRepository.class);
        GatewayModelsFacade facade = new GatewayModelsFacade(channelRouter, userRepository, null);

        ResponseEntity<Map<String, Object>> response = facade.models(null, null);

        assertEquals(401, response.getStatusCode().value());
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
        verify(channelRouter, never()).listRoutableChannels();
    }

    @Test
    void modelsFailClosedWhenRolePolicyThrows() {
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        UserRepository userRepository = mock(UserRepository.class);
        RolePolicyService rolePolicyService = mock(RolePolicyService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        AiChannelEntity channel = new AiChannelEntity();
        channel.setModels("gpt-4o");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(channelRouter.listRoutableChannels()).thenReturn(List.of(channel));
        when(rolePolicyService.resolveAllowedModels(eq(userId), any())).thenThrow(new IllegalStateException("policy service down"));

        GatewayModelsFacade facade = new GatewayModelsFacade(channelRouter, userRepository, rolePolicyService);

        ResponseEntity<Map<String, Object>> response = facade.models(userId, null);
        Map<?, ?> error = assertInstanceOf(Map.class, response.getBody().get("error"));

        assertEquals(503, response.getStatusCode().value());
        assertEquals("policy unavailable", error.get("message"));
        verify(rolePolicyService).resolveAllowedModels(eq(userId), any());
    }

    @Test
    void modelsUseOnlyRoutableChannels() {
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setId(userId);
        AiChannelEntity routableChannel = new AiChannelEntity();
        routableChannel.setModels("gpt-4o,gpt-4o-mini");

        when(userRepository.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(user));
        when(channelRouter.listRoutableChannels()).thenReturn(List.of(routableChannel));

        GatewayModelsFacade facade = new GatewayModelsFacade(channelRouter, userRepository, null);

        ResponseEntity<Map<String, Object>> response = facade.models(userId, "user");

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        assertEquals(List.of("gpt-4o", "gpt-4o-mini"), data.stream().map(item -> String.valueOf(item.get("id"))).toList());
        verify(channelRouter).listRoutableChannels();
    }
}
