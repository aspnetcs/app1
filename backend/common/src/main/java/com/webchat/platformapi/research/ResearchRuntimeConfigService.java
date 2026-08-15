package com.webchat.platformapi.research;

import com.webchat.platformapi.config.SysConfigService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ResearchRuntimeConfigService {

    private static final String CFG_ENABLED = "platform.research-assistant.enabled";
    private static final String LEGACY_CFG_ENABLED = "research.enabled";
    private static final String CFG_MAX_CONCURRENT_PIPELINES = "platform.research-assistant.max-concurrent-pipelines";
    private static final String LEGACY_CFG_MAX_CONCURRENT_PIPELINES = "research.max_concurrent_pipelines";
    private static final String CFG_STAGE_TIMEOUT_MINUTES = "platform.research-assistant.stage-timeout-minutes";
    private static final String LEGACY_CFG_STAGE_TIMEOUT_MINUTES = "research.stage_timeout_minutes";
    private static final String CFG_LLM_MODEL = "platform.research-assistant.llm.model";
    private static final String LEGACY_CFG_LLM_MODEL = "research.llm.model";
    private static final String CFG_LLM_MAX_TOKENS = "platform.research-assistant.llm.max-tokens";
    private static final String LEGACY_CFG_LLM_MAX_TOKENS = "research.llm.max_tokens";
    private static final String CFG_LLM_TEMPERATURE = "platform.research-assistant.llm.temperature";
    private static final String LEGACY_CFG_LLM_TEMPERATURE = "research.llm.temperature";
    private static final String CFG_LITERATURE_ENABLED = "platform.research-assistant.literature.enabled";
    private static final String LEGACY_CFG_LITERATURE_ENABLED = "research.literature.enabled";
    private static final String CFG_LITERATURE_SOURCES = "platform.research-assistant.literature.sources";
    private static final String LEGACY_CFG_LITERATURE_SOURCES = "research.literature.sources";
    private static final String CFG_LITERATURE_MAX_RESULTS = "platform.research-assistant.literature.max-results-per-source";
    private static final String LEGACY_CFG_LITERATURE_MAX_RESULTS = "research.literature.max_results_per_source";
    private static final String CFG_EXPERIMENT_ENABLED = "platform.research-assistant.experiment.enabled";
    private static final String LEGACY_CFG_EXPERIMENT_ENABLED = "research.experiment.enabled";
    private static final String CFG_EXPERIMENT_MODE = "platform.research-assistant.experiment.mode";
    private static final String LEGACY_CFG_EXPERIMENT_MODE = "research.experiment.mode";
    private static final String CFG_EXPERIMENT_TIME_BUDGET_SEC = "platform.research-assistant.experiment.time-budget-sec";
    private static final String LEGACY_CFG_EXPERIMENT_TIME_BUDGET_SEC = "research.experiment.time_budget_sec";
    private static final String CFG_PAPER_ENABLED = "platform.research-assistant.paper.enabled";
    private static final String LEGACY_CFG_PAPER_ENABLED = "research.paper.enabled";
    private static final String CFG_PAPER_MAX_ITERATIONS = "platform.research-assistant.paper.max-iterations";
    private static final String LEGACY_CFG_PAPER_MAX_ITERATIONS = "research.paper.max_iterations";
    private static final String CFG_PAPER_QUALITY_THRESHOLD = "platform.research-assistant.paper.quality-threshold";
    private static final String LEGACY_CFG_PAPER_QUALITY_THRESHOLD = "research.paper.quality_threshold";

    private final ResearchProperties properties;
    private final ObjectProvider<SysConfigService> sysConfigServiceProvider;

    public ResearchRuntimeConfigService(
            ResearchProperties properties,
            ObjectProvider<SysConfigService> sysConfigServiceProvider
    ) {
        this.properties = properties;
        this.sysConfigServiceProvider = sysConfigServiceProvider;
        refresh();
    }

    public ResearchProperties snapshot() {
        return refresh();
    }

    public ResearchProperties refresh() {
        SysConfigService sysConfigService = sysConfigServiceProvider.getIfAvailable();
        if (sysConfigService == null) {
            return properties;
        }

        read(sysConfigService, CFG_ENABLED, LEGACY_CFG_ENABLED)
                .ifPresent(value -> properties.setEnabled(Boolean.parseBoolean(value)));
        read(sysConfigService, CFG_MAX_CONCURRENT_PIPELINES, LEGACY_CFG_MAX_CONCURRENT_PIPELINES)
                .ifPresent(value -> applyInteger(value, properties::setMaxConcurrentPipelines));
        read(sysConfigService, CFG_STAGE_TIMEOUT_MINUTES, LEGACY_CFG_STAGE_TIMEOUT_MINUTES)
                .ifPresent(value -> applyInteger(value, properties::setStageTimeoutMinutes));
        read(sysConfigService, CFG_LLM_MODEL, LEGACY_CFG_LLM_MODEL)
                .ifPresent(value -> properties.getLlm().setModel(value));
        read(sysConfigService, CFG_LLM_MAX_TOKENS, LEGACY_CFG_LLM_MAX_TOKENS)
                .ifPresent(value -> applyInteger(value, properties.getLlm()::setMaxTokens));
        read(sysConfigService, CFG_LLM_TEMPERATURE, LEGACY_CFG_LLM_TEMPERATURE)
                .ifPresent(value -> applyDouble(value, properties.getLlm()::setTemperature));
        read(sysConfigService, CFG_LITERATURE_ENABLED, LEGACY_CFG_LITERATURE_ENABLED)
                .ifPresent(value -> properties.getLiterature().setEnabled(Boolean.parseBoolean(value)));
        read(sysConfigService, CFG_LITERATURE_SOURCES, LEGACY_CFG_LITERATURE_SOURCES)
                .ifPresent(value -> properties.getLiterature().setSources(value));
        read(sysConfigService, CFG_LITERATURE_MAX_RESULTS, LEGACY_CFG_LITERATURE_MAX_RESULTS)
                .ifPresent(value -> applyInteger(value, properties.getLiterature()::setMaxResultsPerSource));
        read(sysConfigService, CFG_EXPERIMENT_ENABLED, LEGACY_CFG_EXPERIMENT_ENABLED)
                .ifPresent(value -> properties.getExperiment().setEnabled(Boolean.parseBoolean(value)));
        read(sysConfigService, CFG_EXPERIMENT_MODE, LEGACY_CFG_EXPERIMENT_MODE)
                .ifPresent(value -> properties.getExperiment().setMode(value));
        read(sysConfigService, CFG_EXPERIMENT_TIME_BUDGET_SEC, LEGACY_CFG_EXPERIMENT_TIME_BUDGET_SEC)
                .ifPresent(value -> applyInteger(value, properties.getExperiment()::setTimeBudgetSec));
        read(sysConfigService, CFG_PAPER_ENABLED, LEGACY_CFG_PAPER_ENABLED)
                .ifPresent(value -> properties.getPaper().setEnabled(Boolean.parseBoolean(value)));
        read(sysConfigService, CFG_PAPER_MAX_ITERATIONS, LEGACY_CFG_PAPER_MAX_ITERATIONS)
                .ifPresent(value -> applyInteger(value, properties.getPaper()::setMaxIterations));
        read(sysConfigService, CFG_PAPER_QUALITY_THRESHOLD, LEGACY_CFG_PAPER_QUALITY_THRESHOLD)
                .ifPresent(value -> applyDouble(value, properties.getPaper()::setQualityThreshold));
        return properties;
    }

    public ResearchProperties apply(Map<String, Object> body) {
        ResearchProperties current = refresh();
        if (body == null) {
            throw new IllegalArgumentException("config body required");
        }

        if (body.containsKey("enabled")) {
            boolean enabled = parseBoolean(body.get("enabled"), current.isEnabled());
            current.setEnabled(enabled);
            persistBoth(CFG_ENABLED, LEGACY_CFG_ENABLED, String.valueOf(enabled));
        }
        if (body.containsKey("maxConcurrentPipelines")) {
            Integer maxPipelines = parseInteger(body.get("maxConcurrentPipelines"));
            if (maxPipelines == null || maxPipelines < 1) {
                throw new IllegalArgumentException("maxConcurrentPipelines must be >= 1");
            }
            current.setMaxConcurrentPipelines(maxPipelines);
            persistBoth(CFG_MAX_CONCURRENT_PIPELINES, LEGACY_CFG_MAX_CONCURRENT_PIPELINES, String.valueOf(maxPipelines));
        }
        if (body.containsKey("stageTimeoutMinutes")) {
            Integer timeout = parseInteger(body.get("stageTimeoutMinutes"));
            if (timeout == null || timeout < 1) {
                throw new IllegalArgumentException("stageTimeoutMinutes must be >= 1");
            }
            current.setStageTimeoutMinutes(timeout);
            persistBoth(CFG_STAGE_TIMEOUT_MINUTES, LEGACY_CFG_STAGE_TIMEOUT_MINUTES, String.valueOf(timeout));
        }

        if (body.get("llm") instanceof Map<?, ?> llmBody) {
            if (llmBody.containsKey("model")) {
                String model = String.valueOf(llmBody.get("model") == null ? "" : llmBody.get("model")).trim();
                current.getLlm().setModel(model);
                persistBoth(CFG_LLM_MODEL, LEGACY_CFG_LLM_MODEL, model);
            }
            if (llmBody.containsKey("maxTokens")) {
                Integer maxTokens = parseInteger(llmBody.get("maxTokens"));
                if (maxTokens == null || maxTokens < 256) {
                    throw new IllegalArgumentException("llm.maxTokens must be >= 256");
                }
                current.getLlm().setMaxTokens(maxTokens);
                persistBoth(CFG_LLM_MAX_TOKENS, LEGACY_CFG_LLM_MAX_TOKENS, String.valueOf(maxTokens));
            }
            if (llmBody.containsKey("temperature")) {
                Double temperature = parseDouble(llmBody.get("temperature"));
                if (temperature == null) {
                    throw new IllegalArgumentException("llm.temperature invalid");
                }
                current.getLlm().setTemperature(temperature);
                persistBoth(CFG_LLM_TEMPERATURE, LEGACY_CFG_LLM_TEMPERATURE, String.valueOf(temperature));
            }
        }

        if (body.get("literature") instanceof Map<?, ?> literatureBody) {
            if (literatureBody.containsKey("enabled")) {
                boolean enabled = parseBoolean(literatureBody.get("enabled"), current.getLiterature().isEnabled());
                current.getLiterature().setEnabled(enabled);
                persistBoth(CFG_LITERATURE_ENABLED, LEGACY_CFG_LITERATURE_ENABLED, String.valueOf(enabled));
            }
            if (literatureBody.containsKey("sources")) {
                String sources = normalizeSources(literatureBody.get("sources"));
                current.getLiterature().setSources(sources);
                persistBoth(CFG_LITERATURE_SOURCES, LEGACY_CFG_LITERATURE_SOURCES, sources);
            }
            if (literatureBody.containsKey("maxResultsPerSource")) {
                Integer maxResults = parseInteger(literatureBody.get("maxResultsPerSource"));
                if (maxResults == null || maxResults < 1) {
                    throw new IllegalArgumentException("literature.maxResultsPerSource must be >= 1");
                }
                current.getLiterature().setMaxResultsPerSource(maxResults);
                persistBoth(CFG_LITERATURE_MAX_RESULTS, LEGACY_CFG_LITERATURE_MAX_RESULTS, String.valueOf(maxResults));
            }
        }

        if (body.get("experiment") instanceof Map<?, ?> experimentBody) {
            if (experimentBody.containsKey("enabled")) {
                boolean enabled = parseBoolean(experimentBody.get("enabled"), current.getExperiment().isEnabled());
                current.getExperiment().setEnabled(enabled);
                persistBoth(CFG_EXPERIMENT_ENABLED, LEGACY_CFG_EXPERIMENT_ENABLED, String.valueOf(enabled));
            }
            if (experimentBody.containsKey("mode")) {
                String mode = String.valueOf(experimentBody.get("mode") == null ? "" : experimentBody.get("mode")).trim();
                current.getExperiment().setMode(mode);
                persistBoth(CFG_EXPERIMENT_MODE, LEGACY_CFG_EXPERIMENT_MODE, mode);
            }
            if (experimentBody.containsKey("timeBudgetSec")) {
                Integer timeBudgetSec = parseInteger(experimentBody.get("timeBudgetSec"));
                if (timeBudgetSec == null || timeBudgetSec < 10) {
                    throw new IllegalArgumentException("experiment.timeBudgetSec must be >= 10");
                }
                current.getExperiment().setTimeBudgetSec(timeBudgetSec);
                persistBoth(CFG_EXPERIMENT_TIME_BUDGET_SEC, LEGACY_CFG_EXPERIMENT_TIME_BUDGET_SEC, String.valueOf(timeBudgetSec));
            }
        }

        if (body.get("paper") instanceof Map<?, ?> paperBody) {
            if (paperBody.containsKey("enabled")) {
                boolean enabled = parseBoolean(paperBody.get("enabled"), current.getPaper().isEnabled());
                current.getPaper().setEnabled(enabled);
                persistBoth(CFG_PAPER_ENABLED, LEGACY_CFG_PAPER_ENABLED, String.valueOf(enabled));
            }
            if (paperBody.containsKey("maxIterations")) {
                Integer maxIterations = parseInteger(paperBody.get("maxIterations"));
                if (maxIterations == null || maxIterations < 1) {
                    throw new IllegalArgumentException("paper.maxIterations must be >= 1");
                }
                current.getPaper().setMaxIterations(maxIterations);
                persistBoth(CFG_PAPER_MAX_ITERATIONS, LEGACY_CFG_PAPER_MAX_ITERATIONS, String.valueOf(maxIterations));
            }
            if (paperBody.containsKey("qualityThreshold")) {
                Double qualityThreshold = parseDouble(paperBody.get("qualityThreshold"));
                if (qualityThreshold == null) {
                    throw new IllegalArgumentException("paper.qualityThreshold invalid");
                }
                current.getPaper().setQualityThreshold(qualityThreshold);
                persistBoth(CFG_PAPER_QUALITY_THRESHOLD, LEGACY_CFG_PAPER_QUALITY_THRESHOLD, String.valueOf(qualityThreshold));
            }
        }

        return current;
    }

    private Optional<String> read(SysConfigService sysConfigService, String primaryKey, String legacyKey) {
        Optional<String> primary = sysConfigService.get(primaryKey);
        if (primary.isPresent()) {
            return primary;
        }
        return sysConfigService.get(legacyKey);
    }

    private void persistBoth(String primaryKey, String legacyKey, String value) {
        SysConfigService sysConfigService = sysConfigServiceProvider.getIfAvailable();
        if (sysConfigService == null) {
            return;
        }
        sysConfigService.set(primaryKey, value);
        sysConfigService.set(legacyKey, value);
    }

    private static boolean parseBoolean(Object raw, boolean defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        return Boolean.parseBoolean(String.valueOf(raw).trim());
    }

    private static Integer parseInteger(Object raw) {
        if (raw instanceof Number value) {
            return value.intValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Double parseDouble(Object raw) {
        if (raw instanceof Number value) {
            return value.doubleValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void applyInteger(String raw, IntSetter setter) {
        Integer parsed = parseInteger(raw);
        if (parsed != null) {
            setter.set(parsed);
        }
    }

    private static void applyDouble(String raw, DoubleSetter setter) {
        Double parsed = parseDouble(raw);
        if (parsed != null) {
            setter.set(parsed);
        }
    }

    private static String normalizeSources(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .reduce((left, right) -> left + "," + right)
                    .orElse("");
        }
        return String.valueOf(raw == null ? "" : raw).trim();
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }

    @FunctionalInterface
    private interface DoubleSetter {
        void set(double value);
    }
}
