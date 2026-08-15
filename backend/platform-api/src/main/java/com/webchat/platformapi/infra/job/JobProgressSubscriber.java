package com.webchat.platformapi.infra.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.ws.WsSessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class JobProgressSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(JobProgressSubscriber.class);

    private final ObjectMapper objectMapper;
    private final WsSessionRegistry ws;

    public JobProgressSubscriber(ObjectMapper objectMapper, WsSessionRegistry ws) {
        this.objectMapper = objectMapper;
        this.ws = ws;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(json, Map.class);

            Object userIdObj = data.get("userId");
            if (userIdObj == null) return;

            UUID userId = UUID.fromString(String.valueOf(userIdObj));
            ws.sendToUser(userId, "job.progress", data);
        } catch (Exception e) {
            log.debug("[job] progress message parse failed: error={}", e.toString());
        }
    }
}
