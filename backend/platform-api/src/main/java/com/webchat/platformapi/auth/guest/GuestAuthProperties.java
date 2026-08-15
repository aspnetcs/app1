package com.webchat.platformapi.auth.guest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "platform.auth.guest")
public class GuestAuthProperties {

    private static final long DEFAULT_ACCESS_TTL_SECONDS = 3600;
    private static final long DEFAULT_REFRESH_TTL_SECONDS = 86400;
    private static final long DEFAULT_DEVICE_BINDING_TTL_SECONDS = 2592000;
    private static final long DEFAULT_RECOVERY_TOKEN_TTL_SECONDS = 2592000;

    private boolean enabled = true;
    private boolean refreshEnabled = true;
    private boolean historyMigrationEnabled = true;
    private long accessTokenTtlSeconds = DEFAULT_ACCESS_TTL_SECONDS;
    private long refreshTokenTtlSeconds = DEFAULT_REFRESH_TTL_SECONDS;
    private long deviceBindingTtlSeconds = DEFAULT_DEVICE_BINDING_TTL_SECONDS;
    private long recoveryTokenTtlSeconds = DEFAULT_RECOVERY_TOKEN_TTL_SECONDS;
    private int issueRateLimitPerHour = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public boolean isHistoryMigrationEnabled() {
        return historyMigrationEnabled;
    }

    public void setHistoryMigrationEnabled(boolean historyMigrationEnabled) {
        this.historyMigrationEnabled = historyMigrationEnabled;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getDeviceBindingTtlSeconds() {
        return deviceBindingTtlSeconds;
    }

    public void setDeviceBindingTtlSeconds(long deviceBindingTtlSeconds) {
        this.deviceBindingTtlSeconds = deviceBindingTtlSeconds;
    }

    public long getRecoveryTokenTtlSeconds() {
        return recoveryTokenTtlSeconds;
    }

    public void setRecoveryTokenTtlSeconds(long recoveryTokenTtlSeconds) {
        this.recoveryTokenTtlSeconds = recoveryTokenTtlSeconds;
    }

    public int getIssueRateLimitPerHour() {
        return issueRateLimitPerHour;
    }

    public void setIssueRateLimitPerHour(int issueRateLimitPerHour) {
        this.issueRateLimitPerHour = issueRateLimitPerHour;
    }

    public Duration accessTokenTtl() {
        long seconds = accessTokenTtlSeconds <= 0 ? DEFAULT_ACCESS_TTL_SECONDS : accessTokenTtlSeconds;
        return Duration.ofSeconds(seconds);
    }

    public Duration refreshTokenTtl() {
        long seconds = refreshTokenTtlSeconds <= 0 ? DEFAULT_REFRESH_TTL_SECONDS : refreshTokenTtlSeconds;
        return Duration.ofSeconds(seconds);
    }

    public Duration deviceBindingTtl() {
        long seconds = deviceBindingTtlSeconds <= 0 ? DEFAULT_DEVICE_BINDING_TTL_SECONDS : deviceBindingTtlSeconds;
        return Duration.ofSeconds(seconds);
    }

    public Duration recoveryTokenTtl() {
        long seconds = recoveryTokenTtlSeconds <= 0 ? DEFAULT_RECOVERY_TOKEN_TTL_SECONDS : recoveryTokenTtlSeconds;
        return Duration.ofSeconds(seconds);
    }
}
