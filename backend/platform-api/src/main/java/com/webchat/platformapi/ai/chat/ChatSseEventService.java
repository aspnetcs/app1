package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.channel.ChannelMonitor;
import com.webchat.platformapi.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ChatSseEventService {

    @FunctionalInterface
    interface EmitterSender {
        boolean send(SseEmitter emitter, String event, Object data);
    }

    private static final Logger log = LoggerFactory.getLogger(ChatSseEventService.class);

    private final ChannelMonitor channelMonitor;
    private final AuditService auditService;

    ChatSseEventService(ChannelMonitor channelMonitor, AuditService auditService) {
        this.channelMonitor = channelMonitor;
        this.auditService = auditService;
    }

    void audit(UUID userId, String requestId, String model, Long channelId, String channelType, Long keyId,
               String status, String error, Instant startedAt, String ip, String userAgent,
               int promptTokens, int completionTokens, int totalTokens) {
        if (auditService == null) {
            return;
        }
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("requestId", requestId);
            detail.put("model", model == null ? "" : model);
            detail.put("channelId", channelId == null ? "" : String.valueOf(channelId));
            detail.put("channelType", channelType == null ? "" : channelType);
            detail.put("keyId", keyId == null ? "" : String.valueOf(keyId));
            detail.put("status", status == null ? "" : status);
            detail.put("error", error == null ? "" : error);
            detail.put("startedAt", startedAt == null ? "" : startedAt.toString());
            detail.put("endedAt", Instant.now().toString());
            detail.put("promptTokens", promptTokens);
            detail.put("completionTokens", completionTokens);
            detail.put("totalTokens", totalTokens);
            auditService.log(userId, "ai.chat.sse", detail, ip, userAgent);
        } catch (Exception e) {
            log.debug("[ai.sse] audit failed: requestId={}, error={}", requestId, e.toString());
        }
    }

    void monitorSuccess(Long channelId, Long keyId) {
        if (channelMonitor == null) {
            return;
        }
        try {
            channelMonitor.recordSuccess(channelId, keyId);
        } catch (Exception e) {
            log.debug("[ai.sse] monitorSuccess failed: channelId={}, keyId={}, error={}", channelId, keyId, e.toString());
        }
    }

    void monitorFailure(Long channelId, Long keyId, String code, Integer httpStatus, String error) {
        if (channelMonitor == null) {
            return;
        }
        try {
            channelMonitor.recordFailure(channelId, keyId, code, httpStatus, error);
        } catch (Exception e) {
            log.debug("[ai.sse] monitorFailure failed: channelId={}, keyId={}, code={}, error={}", channelId, keyId, code, e.toString());
        }
    }

    static void excludeForHttpError(int statusCode, Long channelId, Long keyId, Set<Long> excludedChannels, Set<Long> excludedKeys) {
        if (excludedChannels == null || excludedKeys == null) {
            return;
        }
        if (statusCode == 401 || statusCode == 403 || statusCode == 429) {
            if (keyId != null) {
                excludedKeys.add(keyId);
            }
            return;
        }
        if (channelId != null) {
            excludedChannels.add(channelId);
        }
    }

    void finishSuccessfulStream(UUID userId, String requestId, String traceId, String model,
                                Long channelId, String channelType, Long keyId,
                                Map<String, Object> requestBody, SseEmitter emitter, Instant startedAt, String ip, String userAgent,
                                int promptTokens, int completionTokens, int totalTokens,
                                EmitterSender sender) {
        boolean doneSent = sender.send(emitter, "chat.done",
                ChatMessageBlockEnvelopeFactory.donePayload(requestId, traceId, requestBody));
        if (emitter != null) {
            emitter.complete();
        }

        String endStatus = doneSent ? "done" : "client_closed";
        String endError = doneSent ? "" : "chat_done_delivery_failed";
        if (doneSent) {
            monitorSuccess(channelId, keyId);
        }
        audit(userId, requestId, model, channelId, channelType, keyId, endStatus, endError, startedAt, ip, userAgent,
                promptTokens, completionTokens, totalTokens);
    }

    void finishSuccessfulStream(UUID userId, String requestId, String traceId, String model,
                                Long channelId, String channelType, Long keyId,
                                SseEmitter emitter, Instant startedAt, String ip, String userAgent,
                                int promptTokens, int completionTokens, int totalTokens,
                                EmitterSender sender) {
        finishSuccessfulStream(userId, requestId, traceId, model, channelId, channelType, keyId,
                null, emitter, startedAt, ip, userAgent, promptTokens, completionTokens, totalTokens, sender);
    }
}
