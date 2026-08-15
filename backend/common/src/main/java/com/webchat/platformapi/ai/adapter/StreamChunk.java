package com.webchat.platformapi.ai.adapter;

public record StreamChunk(String delta, String reasoningDelta, boolean done, String errorMessage,
                           Integer promptTokens, Integer completionTokens, Integer totalTokens) {

    public static StreamChunk delta(String delta) {
        return new StreamChunk(delta, null, false, null, null, null, null);
    }

    /** Reasoning-only chunk (thinking content from models like DeepSeek R1). */
    public static StreamChunk reasoning(String reasoningDelta) {
        return new StreamChunk(null, reasoningDelta, false, null, null, null, null);
    }

    /** Chunk with both content delta and reasoning delta. */
    public static StreamChunk deltaWithReasoning(String delta, String reasoningDelta) {
        return new StreamChunk(delta, reasoningDelta, false, null, null, null, null);
    }

    public static StreamChunk doneChunk() {
        return new StreamChunk(null, null, true, null, null, null, null);
    }

    public static StreamChunk error(String message) {
        return new StreamChunk(null, null, true, message == null ? "upstream_error" : message, null, null, null);
    }

    /** Usage-only chunk -- does NOT signal stream end (done=false). Tokens accumulate in consumer. */
    public static StreamChunk withUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        return new StreamChunk(null, null, false, null, promptTokens, completionTokens, totalTokens);
    }
}
