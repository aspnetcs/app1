package com.webchat.platformapi.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.monitor")
public class AiMonitorProperties {

    /**
     * Enable/disable auto-disable and auto-recover.
     */
    private boolean enabled = true;

    /**
     * Disable channel (status=DISABLED_AUTO) when consecutive failures reaches this threshold.
     */
    private int channelDisableAfter = 10;

    /**
     * Disable key (status=DISABLED_AUTO) when consecutive failures reaches this threshold.
     */
    private int keyDisableAfter = 10;

    /**
     * Cooldown before probing auto-disabled channels/keys for recovery.
     */
    private long recoverAfterSeconds = 300;

    /**
     * Background probe interval in milliseconds.
     */
    private long probeIntervalMs = 60_000;

    /**
     * Max probes per scheduled run.
     */
    private int maxProbesPerRun = 10;

    /**
     * Probe request timeout in seconds.
     */
    private int probeTimeoutSeconds = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getChannelDisableAfter() {
        return channelDisableAfter;
    }

    public void setChannelDisableAfter(int channelDisableAfter) {
        this.channelDisableAfter = Math.max(1, channelDisableAfter);
    }

    public int getKeyDisableAfter() {
        return keyDisableAfter;
    }

    public void setKeyDisableAfter(int keyDisableAfter) {
        this.keyDisableAfter = Math.max(1, keyDisableAfter);
    }

    public long getRecoverAfterSeconds() {
        return recoverAfterSeconds;
    }

    public void setRecoverAfterSeconds(long recoverAfterSeconds) {
        this.recoverAfterSeconds = Math.max(1, recoverAfterSeconds);
    }

    public long getProbeIntervalMs() {
        return probeIntervalMs;
    }

    public void setProbeIntervalMs(long probeIntervalMs) {
        this.probeIntervalMs = Math.max(1_000, probeIntervalMs);
    }

    public int getMaxProbesPerRun() {
        return maxProbesPerRun;
    }

    public void setMaxProbesPerRun(int maxProbesPerRun) {
        this.maxProbesPerRun = Math.max(1, maxProbesPerRun);
    }

    public int getProbeTimeoutSeconds() {
        return probeTimeoutSeconds;
    }

    public void setProbeTimeoutSeconds(int probeTimeoutSeconds) {
        this.probeTimeoutSeconds = Math.max(1, probeTimeoutSeconds);
    }
}

