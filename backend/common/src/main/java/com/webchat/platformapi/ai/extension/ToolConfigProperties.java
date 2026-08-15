package com.webchat.platformapi.ai.extension;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform.function-calling")
public class ToolConfigProperties {

    private boolean enabled;
    private int maxSteps = 4;
    private ToolFlags tools = new ToolFlags();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public ToolFlags getTools() {
        return tools;
    }

    public void setTools(ToolFlags tools) {
        this.tools = tools == null ? new ToolFlags() : tools;
    }

    public static class ToolFlags {
        private ToolToggle currentTime = new ToolToggle();
        private ToolToggle calculator = new ToolToggle();
        private ToolToggle webPageSummary = new ToolToggle();

        public ToolToggle getCurrentTime() {
            return currentTime;
        }

        public void setCurrentTime(ToolToggle currentTime) {
            this.currentTime = currentTime == null ? new ToolToggle() : currentTime;
        }

        public ToolToggle getCalculator() {
            return calculator;
        }

        public void setCalculator(ToolToggle calculator) {
            this.calculator = calculator == null ? new ToolToggle() : calculator;
        }

        public ToolToggle getWebPageSummary() {
            return webPageSummary;
        }

        public void setWebPageSummary(ToolToggle webPageSummary) {
            this.webPageSummary = webPageSummary == null ? new ToolToggle() : webPageSummary;
        }
    }

    public static class ToolToggle {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
