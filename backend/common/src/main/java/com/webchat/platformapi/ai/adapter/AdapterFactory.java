package com.webchat.platformapi.ai.adapter;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdapterFactory {

    private final Map<String, ProviderAdapter> adapterByType = new HashMap<>();

    public AdapterFactory(List<ProviderAdapter> adapters) {
        if (adapters == null) return;
        for (ProviderAdapter a : adapters) {
            if (a == null) continue;
            String type = a.type();
            if (type == null || type.isBlank()) continue;
            adapterByType.put(type.trim().toLowerCase(), a);
        }
    }

    public ProviderAdapter get(String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("adapter type is blank");
        ProviderAdapter a = adapterByType.get(type.trim().toLowerCase());
        if (a == null) throw new IllegalArgumentException("unsupported adapter type: " + type);
        return a;
    }
}

