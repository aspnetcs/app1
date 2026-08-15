package com.webchat.platformapi.ai.model;

public class ModelMetadataRequest {

    private String name;
    private String avatar;
    private String description;
    private Boolean pinned;
    private Integer sortOrder;
    private Boolean defaultSelected;
    private Boolean multiChatEnabled;
    private Boolean billingEnabled;
    private java.math.BigDecimal requestPriceUsd;
    private java.math.BigDecimal promptPriceUsd;
    private java.math.BigDecimal inputPriceUsdPer1M;
    private java.math.BigDecimal outputPriceUsdPer1M;
    private Boolean imageParsingOverride;
    private boolean imageParsingOverridePresent = false;

    public ModelMetadataRequest() {
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

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getDefaultSelected() {
        return defaultSelected;
    }

    public void setDefaultSelected(Boolean defaultSelected) {
        this.defaultSelected = defaultSelected;
    }

    public Boolean getMultiChatEnabled() {
        return multiChatEnabled;
    }

    public void setMultiChatEnabled(Boolean multiChatEnabled) {
        this.multiChatEnabled = multiChatEnabled;
    }

    public Boolean getBillingEnabled() {
        return billingEnabled;
    }

    public void setBillingEnabled(Boolean billingEnabled) {
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
        this.imageParsingOverridePresent = true;
    }

    public boolean hasImageParsingOverride() {
        return imageParsingOverridePresent;
    }
}
