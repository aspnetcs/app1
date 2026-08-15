package com.webchat.platformapi.ai.chat;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatRequestBody {

    private String model;
    private List<String> models;
    private List<Map<String, Object>> messages;
    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }

    public List<Map<String, Object>> getMessages() {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        this.messages = messages;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        extraFields.put(key, value);
    }

    public Map<String, Object> toRequestMap() {
        Map<String, Object> request = new HashMap<>(extraFields);
        if (model != null) {
            request.put("model", model);
        }
        if (models != null) {
            request.put("models", models);
        }
        if (messages != null) {
            request.put("messages", messages);
        }
        return request;
    }
}
