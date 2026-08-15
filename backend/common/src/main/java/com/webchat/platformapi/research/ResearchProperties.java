package com.webchat.platformapi.research;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Research Assistant module.
 * Bound to "platform.research-assistant" in application.yml.
 */
@ConfigurationProperties(prefix = "platform.research-assistant")
public class ResearchProperties {

    private boolean enabled = false;
    private int maxConcurrentPipelines = 3;
    private int stageTimeoutMinutes = 30;
    private Llm llm = new Llm();
    private Literature literature = new Literature();
    private Experiment experiment = new Experiment();
    private Paper paper = new Paper();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getMaxConcurrentPipelines() { return maxConcurrentPipelines; }
    public void setMaxConcurrentPipelines(int v) { this.maxConcurrentPipelines = Math.max(1, v); }

    public int getStageTimeoutMinutes() { return stageTimeoutMinutes; }
    public void setStageTimeoutMinutes(int v) { this.stageTimeoutMinutes = Math.max(1, v); }

    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public Literature getLiterature() { return literature; }
    public void setLiterature(Literature literature) { this.literature = literature; }

    public Experiment getExperiment() { return experiment; }
    public void setExperiment(Experiment experiment) { this.experiment = experiment; }

    public Paper getPaper() { return paper; }
    public void setPaper(Paper paper) { this.paper = paper; }

    public static class Llm {
        private String model = "";
        private int maxTokens = 4096;
        private double temperature = 0.7;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = Math.max(256, maxTokens); }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
    }

    public static class Literature {
        private boolean enabled = true;
        private String sources = "openalex,semantic_scholar,arxiv";
        private String s2ApiKey = "";
        private int maxResultsPerSource = 20;
        private int cacheTtlHours = 24;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getSources() { return sources; }
        public void setSources(String sources) { this.sources = sources; }

        public String getS2ApiKey() { return s2ApiKey; }
        public void setS2ApiKey(String s2ApiKey) { this.s2ApiKey = s2ApiKey; }

        public int getMaxResultsPerSource() { return maxResultsPerSource; }
        public void setMaxResultsPerSource(int v) { this.maxResultsPerSource = Math.max(1, v); }

        public int getCacheTtlHours() { return cacheTtlHours; }
        public void setCacheTtlHours(int v) { this.cacheTtlHours = Math.max(1, v); }
    }

    public static class Experiment {
        private boolean enabled = false;
        private String mode = "simulated";
        private int timeBudgetSec = 300;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public int getTimeBudgetSec() { return timeBudgetSec; }
        public void setTimeBudgetSec(int v) { this.timeBudgetSec = Math.max(10, v); }
    }

    public static class Paper {
        private boolean enabled = false;
        private int maxIterations = 3;
        private double qualityThreshold = 7.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int v) { this.maxIterations = Math.max(1, v); }

        public double getQualityThreshold() { return qualityThreshold; }
        public void setQualityThreshold(double v) { this.qualityThreshold = v; }
    }
}
