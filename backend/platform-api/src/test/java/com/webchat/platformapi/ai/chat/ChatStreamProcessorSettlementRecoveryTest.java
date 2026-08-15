package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.adapter.AdapterFactory;
import com.webchat.platformapi.ai.adapter.ProviderAdapter;
import com.webchat.platformapi.ai.adapter.StreamChunk;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.ai.channel.AiChannelKeyEntity;
import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.ChannelSelection;
import com.webchat.platformapi.ai.extension.FunctionCallingService;
import com.webchat.platformapi.ai.security.AiCryptoService;
import com.webchat.platformapi.ai.security.SsrfGuard;
import com.webchat.platformapi.credits.CreditsPolicyEvaluator;
import com.webchat.platformapi.trace.TraceService;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamProcessorSettlementRecoveryTest {

    @Test
    void streamStopsWhenCreditsReserveReturnsNullAfterPolicyAllows() {
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        WsSessionRegistry ws = mock(WsSessionRegistry.class);
        FunctionCallingService functionCallingService = mock(FunctionCallingService.class);
        TraceService traceService = mock(TraceService.class);
        HttpClient httpClient = mock(HttpClient.class);
        ChatStreamContextRegistry streamContextRegistry = new ChatStreamContextRegistry();

        ChatStreamProcessor processor = new ChatStreamProcessor(
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                ws,
                functionCallingService,
                true,
                new ChatMessagePreparer(),
                traceService,
                streamContextRegistry,
                httpClient
        );

        AiChatService owner = mock(AiChatService.class);
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        String requestId = "req-reserve-null";
        String traceId = "trace-reserve-null";
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-5.4-mini",
                "messages", List.of(Map.of("role", "user", "content", "hello"))
        );

        when(owner.reserveStreamingQuota(eq(userId), eq(requestId), eq(traceId), anyMap())).thenReturn(10L);
        when(owner.evaluateCreditsPolicy(userId, "user", "gpt-5.4-mini"))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.allow(new com.webchat.platformapi.ai.model.AiModelMetadataEntity(), null));
        when(owner.reserveCredits(eq(userId), eq("user"), eq("gpt-5.4-mini"), any(), eq(requestId))).thenReturn(null);

        processor.stream(owner, userId, requestId, traceId, requestBody, "127.0.0.1", "JUnit");

        verify(owner).releaseReservedQuota(userId, 10L);
        verify(ws).sendToUser(eq(userId), eq("chat.error"), any());
        verify(channelRouter, never()).select(any(), anySet(), anySet());
    }

    @Test
    void streamPartiallySettlesCreditsWhenSettleThrowsAfterDeltaWasSent() throws Exception {
        AdapterFactory adapterFactory = mock(AdapterFactory.class);
        ChannelRouter channelRouter = mock(ChannelRouter.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        SsrfGuard ssrfGuard = mock(SsrfGuard.class);
        WsSessionRegistry ws = mock(WsSessionRegistry.class);
        FunctionCallingService functionCallingService = mock(FunctionCallingService.class);
        TraceService traceService = mock(TraceService.class);
        HttpClient httpClient = mock(HttpClient.class);
        ChatStreamContextRegistry streamContextRegistry = new ChatStreamContextRegistry();

        ChatStreamProcessor processor = new ChatStreamProcessor(
                adapterFactory,
                channelRouter,
                cryptoService,
                ssrfGuard,
                ws,
                functionCallingService,
                true,
                new ChatMessagePreparer(),
                traceService,
                streamContextRegistry,
                httpClient
        );

        AiChatService owner = mock(AiChatService.class);
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String requestId = "req-settle-fail";
        String traceId = "trace-settle-fail";
        Long creditsSnapshotId = 91L;
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-5.4-mini",
                "messages", List.of(Map.of("role", "user", "content", "hello"))
        );

        AiChannelEntity channel = new AiChannelEntity();
        channel.setId(11L);
        channel.setType("openai");
        channel.setBaseUrl("https://example.com");

        AiChannelKeyEntity key = new AiChannelKeyEntity();
        key.setId(22L);
        key.setChannel(channel);
        key.setApiKeyEncrypted("enc-key");

        ProviderAdapter adapter = mock(ProviderAdapter.class);
        HttpRequest upstreamRequest = HttpRequest.newBuilder(URI.create("https://example.com/v1/chat/completions")).build();
        @SuppressWarnings("unchecked")
        HttpResponse<InputStream> upstreamResponse = mock(HttpResponse.class);

        when(owner.reserveStreamingQuota(eq(userId), eq(requestId), eq(traceId), anyMap())).thenReturn(10L);
        when(owner.evaluateCreditsPolicy(userId, "user", "gpt-5.4-mini"))
                .thenReturn(CreditsPolicyEvaluator.PolicyDecision.allow(new com.webchat.platformapi.ai.model.AiModelMetadataEntity(), null));
        when(owner.reserveCredits(eq(userId), eq("user"), eq("gpt-5.4-mini"), any(), eq(requestId))).thenReturn(creditsSnapshotId);
        when(channelRouter.select(eq("gpt-5.4-mini"), anySet(), anySet()))
                .thenReturn(new ChannelSelection(channel, key, "gpt-5.4-mini"));
        when(adapterFactory.get("openai")).thenReturn(adapter);
        when(adapter.type()).thenReturn("openai");
        when(cryptoService.decrypt("enc-key")).thenReturn("sk-test");
        when(functionCallingService.supports(anyMap(), eq("openai"))).thenReturn(false);
        when(adapter.buildChatRequest(anyMap(), eq(channel), eq("sk-test"), eq(true))).thenReturn(upstreamRequest);
        when(httpClient.send(eq(upstreamRequest), any(HttpResponse.BodyHandler.class))).thenReturn(upstreamResponse);
        when(upstreamResponse.statusCode()).thenReturn(200);
        when(upstreamResponse.body()).thenReturn(new ByteArrayInputStream("line-1\nline-2\n".getBytes(StandardCharsets.UTF_8)));
        when(adapter.parseStreamLine("line-1")).thenReturn(StreamChunk.delta("hello"));
        when(adapter.parseStreamLine("line-2")).thenReturn(StreamChunk.doneChunk());
        when(ws.sendToUser(eq(userId), eq("chat.delta"), any())).thenReturn(1);
        doThrow(new IllegalStateException("credits settle failed"))
                .when(owner).settleCredits(eq(creditsSnapshotId), eq(0), eq(0));

        processor.stream(owner, userId, requestId, traceId, requestBody, "127.0.0.1", "JUnit");

        verify(owner).finalizeReservedQuotaAfterStream(eq(userId), eq(10L), eq(11L), eq("openai"),
                eq("gpt-5.4-mini"), eq(0), eq(0), eq(0), anyLong(), eq(true), eq(requestId), eq(false), eq(true));
        verify(owner).settleCredits(creditsSnapshotId, 0, 0);
        verify(owner).partialSettleCredits(creditsSnapshotId, 0, 0);
        verify(owner, never()).refundCredits(any());
        verify(ws, never()).sendToUser(eq(userId), eq("chat.done"), any());
    }
}
