package com.webchat.adminapi.ai.service;

import com.webchat.adminapi.ai.helper.ChannelValidationHelper;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyRepository;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.ai.controller.dto.ChannelUpsertRequest;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChannelAdminServiceTest {

    @Mock
    private AiChannelRepository channelRepo;

    @Mock
    private AiChannelKeyRepository keyRepo;

    @Mock
    private AiCryptoService crypto;

    @Mock
    private ChannelValidationHelper channelValidationHelper;

    @Mock
    private ChannelMonitor channelMonitor;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AiChannelAdminService service;

    @BeforeEach
    void setUp() {
        service = new AiChannelAdminService(
                channelRepo,
                keyRepo,
                crypto,
                channelValidationHelper,
                channelMonitor,
                jdbcTemplate
        );
    }

    @Test
    void updateKeepsExistingFallbackWhenRequestOmitsFallbackChannelId() {
        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(1L);
        channel.setName("Primary");
        channel.setType("openai");
        channel.setBaseUrl("https://example.test");
        channel.setFallbackChannelId(99L);

        when(channelRepo.findById(1L)).thenReturn(Optional.of(channel));
        when(channelRepo.save(any(AiChannelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keyRepo.findByChannel_IdOrderByIdAsc(1L)).thenReturn(List.of());

        ChannelUpsertRequest request = new ChannelUpsertRequest(
                "Updated Primary",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ApiResponse<Map<String, Object>> response = service.update(1L, request);

        assertEquals(0, response.code());
        assertEquals(99L, channel.getFallbackChannelId());
        assertNotNull(response.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> savedChannel = (Map<String, Object>) response.data().get("channel");
        assertEquals(99L, savedChannel.get("fallbackChannelId"));
    }

    @Test
    void listReturnsAllChannelsWhenSizeIsZero() {
        when(channelRepo.findAll()).thenReturn(List.of(channel(1L, "Primary"), channel(2L, "Secondary")));
        when(keyRepo.findByChannel_IdOrderByIdAsc(anyLong())).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = service.list(0, 0, null);

        assertEquals(0, response.code());
        assertEquals(2, response.data().get("total"));
        assertEquals(2, response.data().get("size"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.data().get("items");
        assertEquals(2, items.size());
    }

    @Test
    void listClampsOutOfRangePageToLastAvailablePage() {
        List<AiChannelEntity> channels = new ArrayList<>();
        for (long index = 1; index <= 20; index++) {
            channels.add(channel(index, "Channel " + index));
        }
        when(channelRepo.findAll()).thenReturn(channels);
        when(keyRepo.findByChannel_IdOrderByIdAsc(anyLong())).thenReturn(List.of());

        ApiResponse<Map<String, Object>> response = service.list(1, 20, null);

        assertEquals(0, response.code());
        assertEquals(0, response.data().get("page"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.data().get("items");
        assertEquals(20, items.size());
    }

    private static AiChannelEntity channel(Long id, String name) {
        AiChannelEntity entity = new AiChannelEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setType("openai");
        entity.setBaseUrl("https://example.test/" + id);
        entity.setModels("gpt-4o");
        return entity;
    }
}
