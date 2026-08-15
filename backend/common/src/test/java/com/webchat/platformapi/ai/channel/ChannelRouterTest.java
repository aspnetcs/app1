package com.webchat.platformapi.ai.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelRouterTest {

    @Mock
    private AiChannelRepository channelRepository;

    @Mock
    private AiChannelKeyRepository keyRepository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChannelRouter router;

    @BeforeEach
    void setUp() {
        router = new ChannelRouter(channelRepository, keyRepository, redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(any())).thenReturn(1L);
    }

    @Test
    void selectSupportsExactModelMappingOnRoutableChannel() {
        AiChannelEntity channel = buildChannel(1L, "gpt-4o", Map.of("gpt-5.3-codex", "upstream-codex"));
        AiChannelKeyEntity key = buildKey(11L);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));
        when(keyRepository.findByChannel_IdOrderByIdAsc(1L)).thenReturn(List.of(key));

        ChannelSelection selection = router.select("gpt-5.3-codex", Set.of(), Set.of());

        assertEquals(1L, selection.channel().getId());
        assertEquals(11L, selection.key().getId());
        assertEquals("upstream-codex", selection.actualModel());
        verify(channelRepository).findByEnabledTrue();
        verify(redis).expire(eq("ai:ch:key_rr:1"), any(Duration.class));
    }

    @Test
    void selectSupportsWildcardModelMappingOnRoutableChannel() {
        AiChannelEntity channel = buildChannel(2L, "gpt-4o", Map.of("gpt-5*", "upstream-codex"));
        AiChannelKeyEntity key = buildKey(22L);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));
        when(keyRepository.findByChannel_IdOrderByIdAsc(2L)).thenReturn(List.of(key));

        ChannelSelection selection = router.select("gpt-5.3-codex", Set.of(), Set.of());

        assertEquals("upstream-codex", selection.actualModel());
    }

    @Test
    void selectSkipsChannelsWithoutEnabledNormalKeys() {
        AiChannelEntity broken = buildChannel(3L, "gpt-5.3-codex", Map.of());
        AiChannelEntity working = buildChannel(4L, "gpt-5.3-codex", Map.of());
        AiChannelKeyEntity key = buildKey(44L);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(broken, working));
        when(keyRepository.findByChannel_IdOrderByIdAsc(3L)).thenReturn(List.of());
        when(keyRepository.findByChannel_IdOrderByIdAsc(4L)).thenReturn(List.of(key));

        ChannelSelection selection = router.select("gpt-5.3-codex", Set.of(), Set.of());

        assertEquals(4L, selection.channel().getId());
        verify(keyRepository).findByChannel_IdOrderByIdAsc(3L);
    }

    @Test
    void selectThrowsWhenNoRoutableChannelExists() {
        AiChannelEntity channel = buildChannel(5L, "gpt-5.3-codex", Map.of());

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));
        when(keyRepository.findByChannel_IdOrderByIdAsc(5L)).thenReturn(List.of());

        assertThrows(NoChannelException.class, () -> router.select("gpt-5.3-codex", Set.of(), Set.of()));
    }

    @Test
    void selectFallsBackToAutoDisabledKeyWhenNormalPoolIsEmpty() {
        AiChannelEntity channel = buildChannel(6L, "gpt-5.3-codex", Map.of());
        AiChannelKeyEntity autoDisabledKey = buildKey(66L, AiChannelStatus.DISABLED_AUTO);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(channel));
        when(keyRepository.findByChannel_IdOrderByIdAsc(6L)).thenReturn(List.of(autoDisabledKey));

        ChannelSelection selection = router.select("gpt-5.3-codex", Set.of(), Set.of());

        assertEquals(6L, selection.channel().getId());
        assertEquals(66L, selection.key().getId());
    }

    @Test
    void selectFallsBackToAutoDisabledChannelWhenNoNormalChannelIsRoutable() {
        AiChannelEntity autoDisabledChannel = buildChannel(7L, "gpt-5.3-codex", Map.of(), AiChannelStatus.DISABLED_AUTO);
        AiChannelKeyEntity autoDisabledKey = buildKey(77L, AiChannelStatus.DISABLED_AUTO);

        when(channelRepository.findByEnabledTrue()).thenReturn(List.of(autoDisabledChannel));
        when(keyRepository.findByChannel_IdOrderByIdAsc(7L)).thenReturn(List.of(autoDisabledKey));

        ChannelSelection selection = router.select("gpt-5.3-codex", Set.of(), Set.of());

        assertEquals(7L, selection.channel().getId());
        assertEquals(77L, selection.key().getId());
    }

    private static AiChannelEntity buildChannel(Long id, String models, Map<String, String> mapping) {
        return buildChannel(id, models, mapping, AiChannelStatus.NORMAL);
    }

    private static AiChannelEntity buildChannel(Long id, String models, Map<String, String> mapping, int status) {
        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(id);
        channel.setEnabled(true);
        channel.setStatus(status);
        channel.setPriority(1);
        channel.setWeight(1);
        channel.setModels(models);
        channel.setModelMapping(mapping);
        return channel;
    }

    private static AiChannelKeyEntity buildKey(Long id) {
        return buildKey(id, AiChannelStatus.NORMAL);
    }

    private static AiChannelKeyEntity buildKey(Long id, int status) {
        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(id);
        key.setEnabled(true);
        key.setStatus(status);
        key.setApiKeyEncrypted("encrypted");
        return key;
    }
}
