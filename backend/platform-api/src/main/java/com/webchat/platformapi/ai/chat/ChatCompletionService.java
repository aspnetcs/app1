package com.webchat.platformapi.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

final class ChatCompletionService {

    @FunctionalInterface
    interface StreamDispatcher {
        void dispatch(UUID userId, String requestId, String traceId, Map<String, Object> requestBody, String ip, String userAgent);
    }

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionService.class);

    private final ExecutorService executor;
    private final Semaphore concurrency;

    ChatCompletionService(ExecutorService executor, Semaphore concurrency) {
        this.executor = executor;
        this.concurrency = concurrency;
    }

    boolean startStream(UUID userId, String requestId, String traceId, Map<String, Object> requestBody,
                        String ip, String userAgent, StreamDispatcher dispatcher) {
        if (userId == null || requestId == null || requestId.isBlank()) {
            return false;
        }
        if (!concurrency.tryAcquire()) {
            return false;
        }
        try {
            executor.submit(() -> {
                try {
                    dispatcher.dispatch(userId, requestId, traceId, requestBody, ip, userAgent);
                } finally {
                    concurrency.release();
                }
            });
            return true;
        } catch (Exception e) {
            concurrency.release();
            log.warn("[ai] startStream submit failed: userId={}, requestId={}, error={}", userId, requestId, e.toString());
            return false;
        }
    }

    boolean startStreams(UUID userId, List<ChatStreamTask> tasks,
                         String ip, String userAgent, StreamDispatcher dispatcher) {
        if (userId == null || tasks == null || tasks.isEmpty()) {
            return false;
        }
        int count = tasks.size();
        if (!concurrency.tryAcquire(count)) {
            return false;
        }
        try {
            for (ChatStreamTask task : tasks) {
                executor.submit(() -> {
                    try {
                        dispatcher.dispatch(userId, task.requestId(), task.traceId(), task.requestBody(), ip, userAgent);
                    } finally {
                        concurrency.release();
                    }
                });
            }
            return true;
        } catch (Exception e) {
            concurrency.release(count);
            log.warn("[ai] startStreams submit failed: userId={}, error={}", userId, e.toString());
            return false;
        }
    }
}
