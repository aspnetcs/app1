package com.webchat.platformapi.market;

public enum MarketAssetType {
    AGENT,
    KNOWLEDGE,
    SKILL,
    MCP;

    public static MarketAssetType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("assetType is required");
        }
        try {
            return MarketAssetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported assetType: " + raw);
        }
    }
}
