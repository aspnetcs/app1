package com.webchat.platformapi.ai.extension;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * MCP server configuration entity.
 * Each record represents an external MCP server that provides tools.
 */
@Entity
@Table(name = "mcp_servers")
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /** MCP server endpoint URL (HTTP/SSE mode) */
    @Column(name = "endpoint_url", nullable = false, length = 1000)
    private String endpointUrl;

    /** Transport type: http_sse, stdio (future) */
    @Column(name = "transport_type", nullable = false, length = 20)
    private String transportType = "http_sse";

    /** API key or auth token for the MCP server (if required) */
    @Column(name = "auth_token", length = 500)
    private String authToken;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Cached tool list JSON from last successful connection */
    @Column(name = "tools_json", columnDefinition = "TEXT")
    private String toolsJson;

    /** Last time tools were refreshed */
    @Column(name = "last_refreshed_at")
    private Instant lastRefreshedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
    public String getTransportType() { return transportType; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getToolsJson() { return toolsJson; }
    public void setToolsJson(String toolsJson) { this.toolsJson = toolsJson; }
    public Instant getLastRefreshedAt() { return lastRefreshedAt; }
    public void setLastRefreshedAt(Instant lastRefreshedAt) { this.lastRefreshedAt = lastRefreshedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
