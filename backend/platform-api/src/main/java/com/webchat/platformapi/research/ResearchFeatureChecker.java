package com.webchat.platformapi.research;

import com.webchat.platformapi.common.api.ResearchFeatureDisabledException;
import org.springframework.stereotype.Component;

/**
 * Feature gate for the Research Assistant module.
 * All research controllers should call checkEnabled() before processing requests.
 */
@Component
public class ResearchFeatureChecker {

    private final ResearchRuntimeConfigService runtimeConfigService;

    public ResearchFeatureChecker(ResearchRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    /**
     * Throws 404 if the research-assistant feature is disabled.
     * Call this at the start of every research controller method.
     */
    public void checkEnabled() {
        ResearchProperties properties = runtimeConfigService.snapshot();
        if (!properties.isEnabled()) {
            throw new ResearchFeatureDisabledException("Research Assistant is not enabled");
        }
    }

    /**
     * Throws 404 if the literature sub-feature is disabled.
     */
    public void checkLiteratureEnabled() {
        ResearchProperties properties = runtimeConfigService.snapshot();
        if (!properties.isEnabled()) {
            throw new ResearchFeatureDisabledException("Research Assistant is not enabled");
        }
        if (!properties.getLiterature().isEnabled()) {
            throw new ResearchFeatureDisabledException("Literature search is not enabled");
        }
    }

    /**
     * Throws 404 if the experiment sub-feature is disabled.
     */
    public void checkExperimentEnabled() {
        ResearchProperties properties = runtimeConfigService.snapshot();
        if (!properties.isEnabled()) {
            throw new ResearchFeatureDisabledException("Research Assistant is not enabled");
        }
        if (!properties.getExperiment().isEnabled()) {
            throw new ResearchFeatureDisabledException("Experiment execution is not enabled");
        }
    }

    /**
     * Throws 404 if the paper sub-feature is disabled.
     */
    public void checkPaperEnabled() {
        ResearchProperties properties = runtimeConfigService.snapshot();
        if (!properties.isEnabled()) {
            throw new ResearchFeatureDisabledException("Research Assistant is not enabled");
        }
        if (!properties.getPaper().isEnabled()) {
            throw new ResearchFeatureDisabledException("Paper generation is not enabled");
        }
    }
}
