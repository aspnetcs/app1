package com.webchat.platformapi.market;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ContextAssetContract {

    public static final String CONTENT_FORMAT = "ai_context_document";
    public static final String USAGE_MODE_RETRIEVAL = "retrieval";
    public static final String USAGE_MODE_FULL_INSTRUCTION = "full_instruction";

    private ContextAssetContract() {
    }

    public static Map<String, Object> knowledgeContractPayload() {
        return buildContractPayload(USAGE_MODE_RETRIEVAL, knowledgeUsageInstruction());
    }

    public static Map<String, Object> skillContractPayload() {
        return buildContractPayload(USAGE_MODE_FULL_INSTRUCTION, skillUsageInstruction());
    }

    public static String knowledgeUsageInstruction() {
        return "Match this knowledge base, retrieve only the relevant fragments, and use them as context. Do not treat it as a mandatory instruction set.";
    }

    public static String skillUsageInstruction() {
        return "When this skill matches, load the full skill package content and follow it as an execution contract.";
    }

    private static Map<String, Object> buildContractPayload(String usageMode, String instruction) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("contentFormat", CONTENT_FORMAT);
        payload.put("usageMode", usageMode);
        payload.put("aiUsageInstruction", instruction);
        payload.put("aiUsageEntry", Map.of(
                "title", "AI usage",
                "usageMode", usageMode,
                "instruction", instruction
        ));
        return payload;
    }
}
