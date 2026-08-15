package com.webchat.adminapi.configtransfer;

import java.util.List;
import java.util.Map;

public record ConfigTransferPayload(
        int schemaVersion,
        String exportedAt,
        boolean includeSecrets,
        List<String> modules,
        Map<String, Object> data,
        Map<String, Object> meta
) {
}

