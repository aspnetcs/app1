package com.webchat.platformapi.credits;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.stereotype.Component;

/**
 * Typed accessor for Credits system configuration.
 * Reads from sys_config table via SysConfigService.
 */
@Component
public class CreditsSystemConfig {

    public static final String KEY_CREDITS_SYSTEM_ENABLED = "credits.system.enabled";
    public static final String KEY_CREDITS_PER_USD = "credits.per.usd";
    public static final String KEY_FREE_MODE_ENABLED = "credits.free.mode.enabled";

    private final SysConfigService sysConfigService;

    public CreditsSystemConfig(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    public boolean isCreditsSystemEnabled() {
        return sysConfigService.getBoolean(KEY_CREDITS_SYSTEM_ENABLED, false);
    }

    public int getCreditsPerUsd() {
        return sysConfigService.getInt(KEY_CREDITS_PER_USD, 1000);
    }

    public boolean isFreeModeEnabled() {
        return sysConfigService.getBoolean(KEY_FREE_MODE_ENABLED, false);
    }

    public void setCreditsSystemEnabled(boolean enabled) {
        sysConfigService.set(KEY_CREDITS_SYSTEM_ENABLED, String.valueOf(enabled));
    }

    public void setCreditsPerUsd(int value) {
        sysConfigService.set(KEY_CREDITS_PER_USD, String.valueOf(value));
    }

    public void setFreeModeEnabled(boolean enabled) {
        sysConfigService.set(KEY_FREE_MODE_ENABLED, String.valueOf(enabled));
    }
}
