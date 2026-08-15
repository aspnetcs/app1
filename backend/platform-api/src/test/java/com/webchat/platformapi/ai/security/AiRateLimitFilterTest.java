package com.webchat.platformapi.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRateLimitFilterTest {

    @Test
    void enforcesPerModelLimitForParallelChatModelsArray() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                createRedisStub(),
                new ObjectMapper(),
                100,
                1,
                null,
                null
        );
        UUID userId = UUID.randomUUID();

        MockHttpServletResponse firstResponse = runRequest(filter, userId, "/api/v1/chat/completions/multi", """
                {
                  "models": ["model-a", "model-b"]
                }
                """);
        MockHttpServletResponse secondResponse = runRequest(filter, userId, "/api/v1/chat/completions/multi", """
                {
                  "models": ["model-a", "model-b"]
                }
                """);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString(StandardCharsets.UTF_8))
                .contains("rate_limit_exceeded");
    }

    @Test
    void enforcesPerModelLimitForTeamChatModelIdsArray() throws Exception {
        AiRateLimitFilter filter = new AiRateLimitFilter(
                createRedisStub(),
                new ObjectMapper(),
                100,
                1,
                null,
                null
        );
        UUID userId = UUID.randomUUID();

        MockHttpServletResponse firstResponse = runRequest(filter, userId, "/api/v1/team-chat/start", """
                {
                  "message": "hello",
                  "modelIds": ["model-a", "model-b"]
                }
                """);
        MockHttpServletResponse secondResponse = runRequest(filter, userId, "/api/v1/team-chat/start", """
                {
                  "message": "hello",
                  "modelIds": ["model-a", "model-b"]
                }
                """);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(secondResponse.getStatus()).isEqualTo(429);
        assertThat(secondResponse.getContentAsString(StandardCharsets.UTF_8))
                .contains("rate_limit_exceeded");
    }

    private static MockHttpServletResponse runRequest(
            AiRateLimitFilter filter,
            UUID userId,
            String uri,
            String body
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.setAttribute(JwtAuthFilter.ATTR_USER_ID, userId);
        request.setAttribute(JwtAuthFilter.ATTR_USER_ROLE, "user");
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request);
        primeRequestBody(wrapper);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(wrapper, response, new MockFilterChain());
        return response;
    }

    private static void primeRequestBody(ContentCachingRequestWrapper request) throws IOException {
        request.getInputStream().readAllBytes();
    }

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate createRedisStub() {
        Map<String, Long> counts = new HashMap<>();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long next = counts.getOrDefault(key, 0L) + 1L;
            counts.put(key, next);
            return next;
        });
        when(valueOperations.get(anyString())).thenReturn(null);
        doAnswer(invocation -> Boolean.TRUE).when(redis).expire(anyString(), any(Duration.class));
        return redis;
    }
}
