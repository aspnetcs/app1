package com.webchat.platformapi.ai.chat;

import com.webchat.platformapi.ai.extension.UserMcpToolAccessService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatMcpToolContextService {

    private final UserMcpToolAccessService userMcpToolAccessService;

    public ChatMcpToolContextService(UserMcpToolAccessService userMcpToolAccessService) {
        this.userMcpToolAccessService = userMcpToolAccessService;
    }

    public void applySavedMcpToolNames(UUID userId, Map<String, Object> requestBody) {
        if (userId == null || requestBody == null) {
            return;
        }
        List<String> selectedToolNames = userMcpToolAccessService.listSelectedToolNames(userId);
        if (selectedToolNames.isEmpty()) {
            return;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        Object rawToolNames = requestBody.get("toolNames");
        if (rawToolNames instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String value = String.valueOf(item).trim();
                if (!value.isEmpty()) {
                    merged.add(value);
                }
            }
        }
        merged.addAll(selectedToolNames);
        requestBody.put("toolNames", new ArrayList<>(merged));
    }
}
