package com.webchat.adminapi.ai.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.webchat.platformapi.ai.security.SsrfGuard;
import org.springframework.stereotype.Component;

@Component
public class ChannelValidationHelper {

    private final SsrfGuard ssrfGuard;

    public ChannelValidationHelper(SsrfGuard ssrfGuard) {
        this.ssrfGuard = ssrfGuard;
    }

    public void assertAllowedBaseUrl(String baseUrl) {
        ssrfGuard.assertAllowedBaseUrl(baseUrl);
    }

    public Long parseChannelId(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        if (raw.isIntegralNumber()) {
            return raw.longValue();
        }
        if (raw.isTextual()) {
            String text = raw.textValue() == null ? "" : raw.textValue().trim();
            if (text.isEmpty()) {
                throw new IllegalArgumentException("channel_id");
            }
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("channel_id", e);
            }
        }
        throw new IllegalArgumentException("channel_id");
    }
}
