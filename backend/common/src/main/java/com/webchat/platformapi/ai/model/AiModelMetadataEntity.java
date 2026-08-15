package com.webchat.platformapi.ai.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ai_model_metadata")
public class AiModelMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", length = 150, nullable = false, unique = true)
    private String modelId;

    @Column(length = 150)
    private String name;

    @Column(length = 150)
    private String avatar;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_default", nullable = false)
    private boolean defaultSelected;

    @Column(name = "multi_chat_enabled", nullable = false)
    private boolean multiChatEnabled = true;

    @Column(name = "billing_enabled", nullable = false)
    private boolean billingEnabled = false;

    @Column(name = "request_price_usd")
    private java.math.BigDecimal requestPriceUsd;

    @Column(name = "prompt_price_usd")
    private java.math.BigDecimal promptPriceUsd;

    @Column(name = "input_price_usd_per_1m")
    private java.math.BigDecimal inputPriceUsdPer1M;

    @Column(name = "output_price_usd_per_1m")
    private java.math.BigDecimal outputPriceUsdPer1M;

    @Column(name = "image_parsing_override")
    private Boolean imageParsingOverride;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isDefaultSelected() {
        return defaultSelected;
    }

    public void setDefaultSelected(boolean defaultSelected) {
        this.defaultSelected = defaultSelected;
    }

    public boolean isMultiChatEnabled() {
        return multiChatEnabled;
    }

    public void setMultiChatEnabled(boolean multiChatEnabled) {
        this.multiChatEnabled = multiChatEnabled;
    }

    public boolean isBillingEnabled() {
        return billingEnabled;
    }

    public void setBillingEnabled(boolean billingEnabled) {
        this.billingEnabled = billingEnabled;
    }

    public java.math.BigDecimal getRequestPriceUsd() {
        return requestPriceUsd;
    }

    public void setRequestPriceUsd(java.math.BigDecimal requestPriceUsd) {
        this.requestPriceUsd = requestPriceUsd;
    }

    public java.math.BigDecimal getPromptPriceUsd() {
        return promptPriceUsd;
    }

    public void setPromptPriceUsd(java.math.BigDecimal promptPriceUsd) {
        this.promptPriceUsd = promptPriceUsd;
    }

    public java.math.BigDecimal getInputPriceUsdPer1M() {
        return inputPriceUsdPer1M;
    }

    public void setInputPriceUsdPer1M(java.math.BigDecimal inputPriceUsdPer1M) {
        this.inputPriceUsdPer1M = inputPriceUsdPer1M;
    }

    public java.math.BigDecimal getOutputPriceUsdPer1M() {
        return outputPriceUsdPer1M;
    }

    public void setOutputPriceUsdPer1M(java.math.BigDecimal outputPriceUsdPer1M) {
        this.outputPriceUsdPer1M = outputPriceUsdPer1M;
    }

    public Boolean getImageParsingOverride() {
        return imageParsingOverride;
    }

    public void setImageParsingOverride(Boolean imageParsingOverride) {
        this.imageParsingOverride = imageParsingOverride;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
