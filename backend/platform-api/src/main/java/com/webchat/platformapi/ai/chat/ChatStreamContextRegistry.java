package com.webchat.platformapi.ai.chat;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory per-request streaming context:
 * - enables additive v1 envelope fields for WS frames
 * - supports WS client abort without leaking memory (TTL cleanup)
 */
@Service
public class ChatStreamContextRegistry {

    static final long TTL_MILLIS = Duration.ofMinutes(30).toMillis();

    static final class Entry {
        final long createdAtMs;
        volatile long expiresAtMs;

        volatile String traceId;
        volatile String model;
        volatile String channelId;
        volatile String channelType;
        volatile String roundId;

        volatile boolean aborted;
        volatile String abortReason;
        volatile boolean terminalSent;

        Entry(long nowMs, String traceId, String model) {
            this.createdAtMs = nowMs;
            this.expiresAtMs = nowMs + TTL_MILLIS;
            this.traceId = traceId == null ? "" : traceId.trim();
            this.model = model == null ? "" : model.trim();
            this.channelId = "";
            this.channelType = "";
            this.roundId = "";
            this.aborted = false;
            this.abortReason = "";
            this.terminalSent = false;
        }

        void touch(long nowMs) {
            this.expiresAtMs = nowMs + TTL_MILLIS;
        }
    }

    public record Snapshot(
            String traceId,
            String model,
            String channelId,
            String channelType,
            String roundId,
            boolean aborted,
            String abortReason,
            boolean terminalSent
    ) {}

    private final ConcurrentHashMap<String, Entry> byKey = new ConcurrentHashMap<>();

    public void registerStart(UUID userId, String requestId, String traceId, String model) {
        String key = key(userId, requestId);
        if (key.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        byKey.compute(key, (k, e) -> {
            if (e == null || isExpired(e, now)) {
                return new Entry(now, traceId, model);
            }
            e.touch(now);
            if ((e.traceId == null || e.traceId.isBlank()) && traceId != null && !traceId.isBlank()) {
                e.traceId = traceId.trim();
            }
            if ((e.model == null || e.model.isBlank()) && model != null && !model.isBlank()) {
                e.model = model.trim();
            }
            return e;
        });
        cleanupOccasionally(now);
    }

    public void updateChannel(UUID userId, String requestId, Long channelId, String channelType) {
        String key = key(userId, requestId);
        if (key.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        byKey.computeIfPresent(key, (k, e) -> {
            e.touch(now);
            if (channelId != null) {
                e.channelId = String.valueOf(channelId);
            }
            if (channelType != null) {
                e.channelType = channelType.trim();
            }
            return e;
        });
        cleanupOccasionally(now);
    }

    public Snapshot get(UUID userId, String requestId) {
        String key = key(userId, requestId);
        if (key.isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry e = byKey.get(key);
        if (e == null) {
            cleanupOccasionally(now);
            return null;
        }
        if (isExpired(e, now)) {
            byKey.remove(key, e);
            return null;
        }
        e.touch(now);
        cleanupOccasionally(now);
        return new Snapshot(
                safe(e.traceId),
                safe(e.model),
                safe(e.channelId),
                safe(e.channelType),
                safe(e.roundId),
                e.aborted,
                safe(e.abortReason),
                e.terminalSent
        );
    }

    public boolean markAborted(UUID userId, String requestId, String reason) {
        String key = key(userId, requestId);
        if (key.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        byKey.compute(key, (k, e) -> {
            if (e == null || isExpired(e, now)) {
                // Ensure abort can still stop a just-started stream even if context was not yet registered.
                e = new Entry(now, "", "");
            }
            e.touch(now);
            e.aborted = true;
            e.abortReason = reason == null ? "" : reason.trim();
            return e;
        });
        cleanupOccasionally(now);
        return true;
    }

    public boolean isAborted(UUID userId, String requestId) {
        Snapshot s = get(userId, requestId);
        return s != null && s.aborted();
    }

    public boolean markTerminalSentIfAbsent(UUID userId, String requestId) {
        String key = key(userId, requestId);
        if (key.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean[] changed = new boolean[] { false };
        byKey.compute(key, (k, e) -> {
            if (e == null || isExpired(e, now)) {
                e = new Entry(now, "", "");
            }
            e.touch(now);
            if (!e.terminalSent) {
                e.terminalSent = true;
                changed[0] = true;
            }
            return e;
        });
        cleanupOccasionally(now);
        return changed[0];
    }

    private static boolean isExpired(Entry e, long nowMs) {
        return e == null || e.expiresAtMs <= nowMs;
    }

    private void cleanupOccasionally(long nowMs) {
        // Keep cleanup O(1) amortized: sample cleanup on ~2% calls.
        if (byKey.isEmpty()) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) >= 2) {
            return;
        }
        int scanned = 0;
        for (var it = byKey.entrySet().iterator(); it.hasNext() && scanned < 200; ) {
            var entry = it.next();
            scanned++;
            Entry e = entry.getValue();
            if (isExpired(e, nowMs)) {
                it.remove();
            }
        }
    }

    private static String key(UUID userId, String requestId) {
        if (userId == null || requestId == null) {
            return "";
        }
        String rid = requestId.trim();
        if (rid.isEmpty()) {
            return "";
        }
        return userId.toString() + ":" + rid;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
