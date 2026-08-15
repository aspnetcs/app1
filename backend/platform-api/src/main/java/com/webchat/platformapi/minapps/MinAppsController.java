package com.webchat.platformapi.minapps;

import com.webchat.platformapi.common.api.ApiResponse;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/minapps")
public class MinAppsController {

    private static final String FLAG_NAME = "platform.minapps.enabled";

    private final Environment environment;

    public MinAppsController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        boolean enabled = Boolean.parseBoolean(environment.getProperty(FLAG_NAME, "false"));

        // Wave 4A contract: keep the switch + explanation endpoint, but do not provide a runtime.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("flagName", FLAG_NAME);
        payload.put("policy", "disabled-by-default");
        payload.put("reason", "MinApps is disabled in this deployment. The runtime is not shipped in the mini program build.");
        payload.put("actions", java.util.List.of(
                "No user entry points are exposed when disabled.",
                "Admin UI is read-only and shows this explanation only."
        ));
        return ApiResponse.ok(payload);
    }
}

