package com.webchat.platformapi.ai.extension;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.mcp")
public class McpProperties {

    private boolean enabled = false;
    private int maxServers = 10;
    private long requestTimeoutMs = 30000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxServers() { return maxServers; }
    public void setMaxServers(int maxServers) { this.maxServers = maxServers; }
    public long getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
}
