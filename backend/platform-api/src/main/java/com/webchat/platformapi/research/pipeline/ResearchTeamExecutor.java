package com.webchat.platformapi.research.pipeline;

import com.webchat.platformapi.ai.channel.ChannelRouter;
import com.webchat.platformapi.ai.channel.AiChannelEntity;
import com.webchat.platformapi.research.ResearchStage;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Multi-model consensus executor for the research pipeline.
 * When a project is in "team" mode, this component runs an LLM stage
 * across multiple models concurrently, then synthesizes their outputs
 * through a judge model to produce a higher-quality result.
 *
 * Graceful degradation: if fewer than 2 models are available, falls
 * back to single-model execution automatically.
 */
@Component
public class ResearchTeamExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResearchTeamExecutor.class);
    private static final long MODEL_CALL_TIMEOUT_SECONDS = 180L;

    private final ResearchLlmClient llmClient;
    private final ChannelRouter channelRouter;
    private final ExecutorService consensusExecutor;

    public ResearchTeamExecutor(
            ResearchLlmClient llmClient,
            ChannelRouter channelRouter
    ) {
        this.llmClient = llmClient;
        this.channelRouter = channelRouter;
        this.consensusExecutor = Executors.newCachedThreadPool();
    }

    @PreDestroy
    void shutdown() {
        consensusExecutor.shutdownNow();
    }

    /**
     * Execute a stage with multi-model consensus.
     *
     * Phase 1: Dispatch the same system prompt to N expert models concurrently.
     * Phase 2: Collect proposals, tolerate partial failures.
     * Phase 3: Synthesize all proposals through a judge model.
     *
     * @param stage        the research stage being executed
     * @param systemPrompt the system prompt for this stage
     * @param userContent  the accumulated context / user content
     * @return synthesized StageResult
     */
    public StageResult executeWithConsensus(
            ResearchStage stage,
            String systemPrompt,
            String userContent
    ) {
        List<String> teamModels = resolveTeamModels(3);
        log.info("[research-team] stage {} using {} expert models: {}",
                stage.getKey(), teamModels.size(), teamModels);

        if (teamModels.size() < 2) {
            log.warn("[research-team] insufficient models ({}), falling back to single mode",
                    teamModels.size());
            String result = completeSingleWithTimeout(stage, systemPrompt, userContent, firstOrNull(teamModels));
            return stage.isGate()
                    ? new StageResult("waiting_approval", result, true, null)
                    : StageResult.success(result);
        }

        // Phase 1: Concurrent proposals from each expert model
        Map<String, CompletableFuture<String>> futures = new LinkedHashMap<>();
        for (String model : teamModels) {
            futures.put(model, supplyTimedCall(
                    stage,
                    "model " + model,
                    () -> llmClient.complete(systemPrompt, userContent, model, 4096, 0.4)
            ));
        }

        // Phase 2: Collect results (tolerate partial failures)
        Map<String, String> proposals = new LinkedHashMap<>();
        for (var entry : futures.entrySet()) {
            String output = entry.getValue().join();
            if (output != null && !output.isBlank()) {
                proposals.put(entry.getKey(), output);
            }
        }

        log.info("[research-team] stage {} collected {}/{} proposals",
                stage.getKey(), proposals.size(), teamModels.size());

        if (proposals.isEmpty()) {
            // Don't fail the whole pipeline if all expert models are unavailable (common in
            // self-hosted / proxy setups where only 1 model is actually usable).
            log.warn("[research-team] all expert models failed for stage {}, falling back to single mode",
                    stage.getKey());
            try {
                String result = completeSingleWithTimeout(stage, systemPrompt, userContent, firstOrNull(teamModels));
                return stage.isGate()
                        ? new StageResult("waiting_approval", result, true, null)
                        : StageResult.success(result);
            } catch (Exception e) {
                return StageResult.failure("all team models failed for stage: " + stage.getKey()
                        + "; single fallback failed: " + e.getMessage());
            }
        }

        if (proposals.size() == 1) {
            // Only one succeeded -- return directly without synthesis
            String singleOutput = proposals.values().iterator().next();
            if (stage.isGate()) {
                return new StageResult("waiting_approval", singleOutput, true, null);
            }
            return StageResult.success(singleOutput);
        }

        // Phase 3: Judge synthesis
        String synthesisPrompt = buildSynthesisPrompt(stage, proposals);
        String finalOutput = supplyTimedCall(
                stage,
                "judge synthesis",
                () -> llmClient.complete(synthesisPrompt, userContent)
        ).join();
        if (finalOutput == null || finalOutput.isBlank()) {
            finalOutput = proposals.values().iterator().next();
            log.warn("[research-team] judge synthesis unavailable for stage {}, falling back to first proposal", stage.getKey());
        }

        if (stage.isGate()) {
            return new StageResult("waiting_approval", finalOutput, true, null);
        }
        return StageResult.success(finalOutput);
    }

    /**
     * Resolve up to {@code count} distinct routable model IDs from the channel router.
     */
    List<String> resolveTeamModels(int count) {
        Set<String> seen = new LinkedHashSet<>();
        // Prefer explicit model env vars so "team mode" doesn't accidentally pick
        // blocked models from legacy channel configs (e.g. api.openai.com in CN).
        String preferred = preferModelFromEnv();
        if (preferred != null) {
            seen.add(preferred);
            // If we only have one reliable model, let the caller gracefully degrade.
            if (count <= 1) {
                return new ArrayList<>(seen);
            }
        }
        try {
            for (AiChannelEntity channel : channelRouter.listRoutableChannels()) {
                if (channel == null) continue;

                // Check models field
                String models = channel.getModels();
                if (models != null && !models.isBlank()) {
                    for (String candidate : models.split(",")) {
                        String trimmed = candidate.trim();
                        if (!trimmed.isEmpty() && seen.add(trimmed) && seen.size() >= count) {
                            return new ArrayList<>(seen);
                        }
                    }
                }

                // Check modelMapping keys
                Map<String, String> mapping = channel.getModelMapping();
                if (mapping != null) {
                    for (String key : mapping.keySet()) {
                        if (key != null && !key.isBlank()) {
                            String trimmed = key.trim();
                            if (seen.add(trimmed) && seen.size() >= count) {
                                return new ArrayList<>(seen);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[research-team] failed to resolve team models: {}", e.getMessage());
        }
        return new ArrayList<>(seen);
    }

    private static String preferModelFromEnv() {
        // Allow explicit research override; fall back to global default.
        String research = getenvTrimmed("RESEARCH_LLM_MODEL");
        if (research != null) return research;
        return getenvTrimmed("AI_DEFAULT_MODEL");
    }

    private static String getenvTrimmed(String key) {
        String value = System.getenv(key);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String firstOrNull(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        String v = values.get(0);
        if (v == null) return null;
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    long modelCallTimeoutSeconds() {
        return MODEL_CALL_TIMEOUT_SECONDS;
    }

    private CompletableFuture<String> supplyTimedCall(
            ResearchStage stage,
            String label,
            Supplier<String> supplier
    ) {
        return CompletableFuture
                .supplyAsync(supplier, consensusExecutor)
                .completeOnTimeout(null, modelCallTimeoutSeconds(), TimeUnit.SECONDS)
                .exceptionally(error -> {
                    String message = error == null ? "unknown error" : error.getMessage();
                    log.warn("[research-team] {} failed for stage {}: {}", label, stage.getKey(), message);
                    return null;
                });
    }

    private String completeSingleWithTimeout(ResearchStage stage, String systemPrompt, String userContent, String preferredModel) {
        String result = supplyTimedCall(stage, "single fallback", () -> completeSingle(systemPrompt, userContent, preferredModel)).join();
        if (result == null || result.isBlank()) {
            throw new ResearchLlmClient.ResearchLlmException("single fallback timed out for stage: " + stage.getKey());
        }
        return result;
    }

    private String completeSingle(String systemPrompt, String userContent, String preferredModel) {
        // Use preferred model when available, otherwise fall back to ResearchLlmClient's internal resolution.
        if (preferredModel != null && !preferredModel.isBlank()) {
            return llmClient.complete(systemPrompt, userContent, preferredModel, 4096, 0.4);
        }
        return llmClient.complete(systemPrompt, userContent);
    }

    /**
     * Build a synthesis prompt that instructs the judge to merge multiple expert proposals.
     */
    private String buildSynthesisPrompt(ResearchStage stage, Map<String, String> proposals) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior research advisor synthesizing multiple expert opinions for the \"")
          .append(stage.getKey()).append("\" stage of a research pipeline.\n\n");
        sb.append("Below are independent analyses from different AI experts. ")
          .append("Your task is to synthesize them into a single, authoritative result that:\n")
          .append("1. Preserves all unique valid insights from each expert\n")
          .append("2. Resolves any contradictions with clear explanation\n")
          .append("3. Is more comprehensive and higher quality than any single opinion alone\n")
          .append("4. Uses Markdown formatting with clear structure\n\n");

        int expertNum = 1;
        for (var entry : proposals.entrySet()) {
            sb.append("--- Expert ").append(expertNum++).append(" (").append(entry.getKey())
              .append(") ---\n").append(entry.getValue()).append("\n\n");
        }

        sb.append("\nCRITICAL LANGUAGE RULE: Detect the language of the user's research topic. ")
          .append("Your ENTIRE response -- all reasoning, analysis, field names, and output text -- ")
          .append("MUST be written in that SAME language. ")
          .append("If the topic is in Chinese, respond entirely in Chinese. ")
          .append("If in English, respond entirely in English. ")
          .append("Never mix languages unless quoting a proper noun or citation.");

        return sb.toString();
    }
}
