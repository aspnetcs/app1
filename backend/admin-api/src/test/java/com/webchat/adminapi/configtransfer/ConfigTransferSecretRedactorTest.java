package com.webchat.adminapi.configtransfer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTransferSecretRedactorTest {

    @Test
    void redactRemovesSecretLikeKeysRecursively() {
        String openAiKey = "sk-abcdefghijklmnopqrstuvwxyz0123456789";
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("apiSecret", "should-not-leak");
        nested.put("normal", "ok");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiKey", openAiKey);
        root.put("nested", nested);
        root.put("list", List.of(
                Map.of("token", "should-not-leak", "x", "y"),
                "Bearer should-not-leak"
        ));
        root.put("nonSecretKeyButSecretValue", openAiKey);

        Object redacted = ConfigTransferSecretRedactor.redact(root);
        assertTrue(redacted instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) redacted;

        assertFalse(out.containsKey("apiKey"));
        assertTrue(out.containsKey("nested"));
        @SuppressWarnings("unchecked")
        Map<String, Object> outNested = (Map<String, Object>) out.get("nested");
        assertFalse(outNested.containsKey("apiSecret"));
        assertEquals("ok", outNested.get("normal"));

        @SuppressWarnings("unchecked")
        List<Object> outList = (List<Object>) out.get("list");
        @SuppressWarnings("unchecked")
        Map<String, Object> outListMap = (Map<String, Object>) outList.get(0);
        assertFalse(outListMap.containsKey("token"));
        assertEquals("y", outListMap.get("x"));

        assertEquals("***", outList.get(1));
        assertEquals("***", out.get("nonSecretKeyButSecretValue"));
    }
}
