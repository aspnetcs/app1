package com.webchat.adminapi.configtransfer;

import java.util.List;
import java.util.Map;

public record ConfigImportPreview(
        int schemaVersion,
        List<Map<String, Object>> modules,
        List<String> warnings,
        Map<String, Object> summary
) {
}

