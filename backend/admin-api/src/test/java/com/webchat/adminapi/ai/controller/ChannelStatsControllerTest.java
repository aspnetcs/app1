package com.webchat.adminapi.ai.controller;

import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelRepository;
import com.webchat.platformapi.ai.channel.AiChannelStatus;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ErrorCodes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelStatsControllerTest {

    @Mock
    private AiChannelRepository channelRepository;

    private MockMvc mockMvc;
    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelStatsController(channelRepository)).build();
        adminUserId = UUID.randomUUID();
    }

    @Test
    void statsRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/channels/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(channelRepository);
    }

    @Test
    void statsReturnsComputedChannelHealthMetrics() throws Exception {
        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(101L);
        channel.setName("OpenAI Primary");
        channel.setType("openai");
        channel.setEnabled(true);
        channel.setStatus(AiChannelStatus.NORMAL);
        channel.setSuccessCount(8);
        channel.setFailCount(2);
        channel.setConsecutiveFailures(1);
        channel.setLastSuccessAt(Instant.parse("2026-03-24T12:00:00Z"));
        channel.setLastFailAt(Instant.parse("2026-03-24T13:00:00Z"));
        when(channelRepository.findAll()).thenReturn(List.of(channel));

        mockMvc.perform(admin(get("/api/v1/admin/ai/channels/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].name").value("OpenAI Primary"))
                .andExpect(jsonPath("$.data[0].type").value("openai"))
                .andExpect(jsonPath("$.data[0].enabled").value(true))
                .andExpect(jsonPath("$.data[0].successCount").value(8))
                .andExpect(jsonPath("$.data[0].failCount").value(2))
                .andExpect(jsonPath("$.data[0].successRate").value(80.0))
                .andExpect(jsonPath("$.data[0].consecutiveFailures").value(1));
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(JwtAuthFilter.ATTR_USER_ID, adminUserId)
                .requestAttr(JwtAuthFilter.ATTR_USER_ROLE, "admin");
    }
}
