package com.webchat.platformapi.preferences;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preference")
public class UserPreferenceEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "default_agent_id", length = 120)
    private String defaultAgentId;

    @Column(name = "theme_mode", nullable = false, length = 20)
    private String themeMode = "system";

    @Column(name = "code_theme", nullable = false, length = 32)
    private String codeTheme = "system";

    @Column(name = "font_scale", nullable = false, length = 16)
    private String fontScale = "md";

    @Column(name = "spacing_vertical", nullable = false, length = 16)
    private String spacingVertical = "16px";

    @Column(name = "spacing_horizontal", nullable = false, length = 16)
    private String spacingHorizontal = "16px";

    @Column(name = "mcp_mode", nullable = false, length = 20)
    private String mcpMode = "auto";

    @Column(name = "preferred_mcp_server_id", length = 120)
    private String preferredMcpServerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (themeMode == null || themeMode.isBlank()) {
            themeMode = "system";
        }
        if (codeTheme == null || codeTheme.isBlank()) {
            codeTheme = "system";
        }
        if (fontScale == null || fontScale.isBlank()) {
            fontScale = "md";
        }
        if (spacingVertical == null || spacingVertical.isBlank()) {
            spacingVertical = "16px";
        }
        if (spacingHorizontal == null || spacingHorizontal.isBlank()) {
            spacingHorizontal = "16px";
        }
        if (mcpMode == null || mcpMode.isBlank()) {
            mcpMode = "auto";
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
        if (themeMode == null || themeMode.isBlank()) {
            themeMode = "system";
        }
        if (codeTheme == null || codeTheme.isBlank()) {
            codeTheme = "system";
        }
        if (fontScale == null || fontScale.isBlank()) {
            fontScale = "md";
        }
        if (spacingVertical == null || spacingVertical.isBlank()) {
            spacingVertical = "16px";
        }
        if (spacingHorizontal == null || spacingHorizontal.isBlank()) {
            spacingHorizontal = "16px";
        }
        if (mcpMode == null || mcpMode.isBlank()) {
            mcpMode = "auto";
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getDefaultAgentId() {
        return defaultAgentId;
    }

    public void setDefaultAgentId(String defaultAgentId) {
        this.defaultAgentId = defaultAgentId;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public String getCodeTheme() {
        return codeTheme;
    }

    public void setCodeTheme(String codeTheme) {
        this.codeTheme = codeTheme;
    }

    public String getFontScale() {
        return fontScale;
    }

    public void setFontScale(String fontScale) {
        this.fontScale = fontScale;
    }

    public String getSpacingVertical() {
        return spacingVertical;
    }

    public void setSpacingVertical(String spacingVertical) {
        this.spacingVertical = spacingVertical;
    }

    public String getSpacingHorizontal() {
        return spacingHorizontal;
    }

    public void setSpacingHorizontal(String spacingHorizontal) {
        this.spacingHorizontal = spacingHorizontal;
    }

    public String getMcpMode() {
        return mcpMode;
    }

    public void setMcpMode(String mcpMode) {
        this.mcpMode = mcpMode;
    }

    public String getPreferredMcpServerId() {
        return preferredMcpServerId;
    }

    public void setPreferredMcpServerId(String preferredMcpServerId) {
        this.preferredMcpServerId = preferredMcpServerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
