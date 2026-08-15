package com.webchat.platformapi.ai.extension;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ToolRuntimeConfigService {

    private static final String CFG_ENABLED = "function_calling.enabled";
    private static final String CFG_MAX_STEPS = "function_calling.max_steps";
    private static final String CFG_TOOL_CURRENT_TIME_ENABLED = "function_calling.tools.current_time.enabled";
    private static final String CFG_TOOL_CALCULATOR_ENABLED = "function_calling.tools.calculator.enabled";
    private static final String CFG_TOOL_WEB_PAGE_SUMMARY_ENABLED = "function_calling.tools.web_page_summary.enabled";

    private final ToolConfigProperties properties;
    private final ObjectProvider<SysConfigService> sysConfigServiceProvider;

    public ToolRuntimeConfigService(
            ToolConfigProperties properties,
            ObjectProvider<SysConfigService> sysConfigServiceProvider
    ) {
        this.properties = properties;
        this.sysConfigServiceProvider = sysConfigServiceProvider;
        refresh();
    }

    public ToolConfigProperties refresh() {
        SysConfigService sysConfigService = sysConfigServiceProvider.getIfAvailable();
        if (sysConfigService == null) {
            return properties;
        }
        sysConfigService.get(CFG_ENABLED).ifPresent(value -> properties.setEnabled(Boolean.parseBoolean(value)));
        sysConfigService.get(CFG_MAX_STEPS).ifPresent(value -> {
            Integer parsed = parseInt(value);
            if (parsed != null && parsed >= 1) {
                properties.setMaxSteps(parsed);
            }
        });
        sysConfigService.get(CFG_TOOL_CURRENT_TIME_ENABLED)
                .ifPresent(value -> properties.getTools().getCurrentTime().setEnabled(Boolean.parseBoolean(value)));
        sysConfigService.get(CFG_TOOL_CALCULATOR_ENABLED)
                .ifPresent(value -> properties.getTools().getCalculator().setEnabled(Boolean.parseBoolean(value)));
        sysConfigService.get(CFG_TOOL_WEB_PAGE_SUMMARY_ENABLED)
                .ifPresent(value -> properties.getTools().getWebPageSummary().setEnabled(Boolean.parseBoolean(value)));
        return properties;
    }

    public ToolConfigProperties apply(Map<String, Object> body) {
        ToolConfigProperties current = refresh();
        if (body == null) {
            return current;
        }
        if (body.containsKey("enabled")) {
            boolean enabled = parseBoolean(body.get("enabled"));
            current.setEnabled(enabled);
            persist(CFG_ENABLED, String.valueOf(enabled));
        }
        if (body.containsKey("maxSteps")) {
            Integer maxSteps = parseInt(body.get("maxSteps"));
            if (maxSteps == null || maxSteps < 1) {
                throw new IllegalArgumentException("maxSteps must be >= 1");
            }
            current.setMaxSteps(maxSteps);
            persist(CFG_MAX_STEPS, String.valueOf(maxSteps));
        }
        if (body.containsKey("tools")) {
            Object rawTools = body.get("tools");
            if (!(rawTools instanceof Map<?, ?> tools)) {
                throw new IllegalArgumentException("tools must be an object");
            }
            applyToolToggle(tools, "current_time", current.getTools().getCurrentTime(), CFG_TOOL_CURRENT_TIME_ENABLED);
            applyToolToggle(tools, "calculator", current.getTools().getCalculator(), CFG_TOOL_CALCULATOR_ENABLED);
            applyToolToggle(tools, "web_page_summary", current.getTools().getWebPageSummary(), CFG_TOOL_WEB_PAGE_SUMMARY_ENABLED);
        }
        return current;
    }

    private void applyToolToggle(
            Map<?, ?> tools,
            String toolKey,
            ToolConfigProperties.ToolToggle toggle,
            String configKey
    ) {
        Object rawTool = tools.get(toolKey);
        if (!(rawTool instanceof Map<?, ?> toolConfig) || !toolConfig.containsKey("enabled")) {
            return;
        }
        boolean enabled = parseBoolean(toolConfig.get("enabled"));
        toggle.setEnabled(enabled);
        persist(configKey, String.valueOf(enabled));
    }

    private void persist(String key, String value) {
        SysConfigService sysConfigService = sysConfigServiceProvider.getIfAvailable();
        if (sysConfigService != null) {
            sysConfigService.set(key, value);
        }
    }

    private static boolean parseBoolean(Object raw) {
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private static Integer parseInt(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
