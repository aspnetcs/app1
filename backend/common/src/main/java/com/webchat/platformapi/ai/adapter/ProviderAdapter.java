package com.webchat.platformapi.ai.adapter;

import com.webchat.platformapi.ai.channel.AiChannelEntity;

import java.net.http.HttpRequest;
import java.util.Map;

public interface ProviderAdapter {

    String type();

    HttpRequest buildChatRequest(Map<String, Object> requestBody, AiChannelEntity channel, String apiKey, boolean stream) throws Exception;

    /**
     * Parse a single upstream stream line into a normalized chunk.
     * Return null to ignore the line.
     */
    StreamChunk parseStreamLine(String line) throws Exception;
}

