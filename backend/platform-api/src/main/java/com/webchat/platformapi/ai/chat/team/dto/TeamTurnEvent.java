package com.webchat.platformapi.ai.chat.team.dto;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cursor-based event payload persisted for non-SSE recovery/polling.
 */
public class TeamTurnEvent {
    private long cursor;
    private String event;
    private Map<String, Object> data = new LinkedHashMap<>();
    private Instant timestamp;

    public long getCursor() {
        return cursor;
    }

    public void setCursor(long cursor) {
        this.cursor = cursor;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
