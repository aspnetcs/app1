package com.webchat.platformapi.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.auth.session.DeviceSessionRepository;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WsTicketControllerTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        WsTicketController controller = new WsTicketController(
                redis,
                new ObjectMapper(),
                deviceSessionRepository,
                30
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void createWsTicketUsesExactRouteAndReturnsTicketForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        mockMvc.perform(
                        post("/api/v1/auth/ws-ticket")
                                .requestAttr(JwtAuthFilter.ATTR_USER_ID, userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").value(org.hamcrest.Matchers.startsWith("ws_")))
                .andExpect(jsonPath("$.data.expiresIn").value(60));

        verify(valueOperations).increment(startsWith("rate:ws_ticket:" + userId));
        verify(redis).expire(startsWith("rate:ws_ticket:" + userId), eq(Duration.ofMinutes(2)));
        verify(valueOperations).set(
                startsWith("ws_ticket:ws_"),
                contains(userId.toString()),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void createWsTicketRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/auth/ws-ticket"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCodes.UNAUTHORIZED));

        verifyNoInteractions(redis, deviceSessionRepository);
    }

    @Test
    void legacyWsTicketRouteIsNotMapped() throws Exception {
        mockMvc.perform(post("/api/auth/ws-ticket"))
                .andExpect(status().isNotFound());
    }
}
