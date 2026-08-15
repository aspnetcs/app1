package com.webchat.platformapi.research.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.research.ResearchStage;
import com.webchat.platformapi.research.ResearchStageOutputNormalizer;
import com.webchat.platformapi.research.entity.ResearchRunEntity;
import com.webchat.platformapi.research.entity.ResearchStageLogEntity;
import com.webchat.platformapi.research.literature.LiteratureResult;
import com.webchat.platformapi.research.literature.LiteratureSearchService;
import com.webchat.platformapi.research.repository.ResearchStageLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes a single research stage by dispatching to the appropriate handler.
 * Each stage produces a StageResult which the pipeline service uses to advance.
 */
@Component
public class ResearchStageExecutor {

    private static final Logger log = LoggerFactory.getLogger(ResearchStageExecutor.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResearchLlmClient llmClient;
    private final LiteratureSearchService literatureService;
    private final ResearchStageLogRepository stageLogRepo;
    private final ResearchTeamExecutor teamExecutor;
    private final ResearchStageOutputNormalizer stageOutputNormalizer = new ResearchStageOutputNormalizer();

    public ResearchStageExecutor(
            ResearchLlmClient llmClient,
            LiteratureSearchService literatureService,
            ResearchStageLogRepository stageLogRepo,
            ResearchTeamExecutor teamExecutor
    ) {
        this.llmClient = llmClient;
        this.literatureService = literatureService;
        this.stageLogRepo = stageLogRepo;
        this.teamExecutor = teamExecutor;
    }

    /**
     * Execute a stage and return the result.
     * Creates a stage log entry with timing and token information.
     */
    public StageResult execute(
            ResearchRunEntity run,
            ResearchStage stage,
            String context,
            String mode,
            boolean pauseAfterStage
    ) {
        Instant start = Instant.now();
        ResearchStageLogEntity stageLog = new ResearchStageLogEntity();
        stageLog.setRunId(run.getId());
        stageLog.setStageNumber(stage.getNumber());
        stageLog.setStageName(stage.getKey());
        stageLog.setStatus("running");
        stageLog.setStartedAt(start);

        try {
            closeSupersededRunningLogs(run.getId(), stage.getNumber(), start);
            stageLogRepo.save(stageLog);

            StageResult result;
            if ("team".equals(mode) && stage.isLlmStage()) {
                String systemPrompt = getSystemPromptForStage(stage);
                String userContent = stage == ResearchStage.TOPIC_INIT
                        ? "Research topic: " + context
                        : tailContext(context, 12000);
                result = teamExecutor.executeWithConsensus(stage, systemPrompt, userContent);
            } else {
                result = dispatch(stage, context);
            }
            result = applyPauseDecision(result, pauseAfterStage);
            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();

            stageLog.setElapsedMs(elapsed);
            stageLog.setCompletedAt(Instant.now());
            if (result.isFailed()) {
                stageLog.setStatus("failed");
                stageLog.setOutputJson(null);
                stageLog.setErrorMessage(truncate(result.errorMessage(), 500));
            } else {
                stageLog.setStatus(result.requiresApproval() ? "waiting_approval" : "completed");
                stageLog.setOutputJson(serializeStageOutput(result.output(), 4000));
                stageLog.setErrorMessage(null);
            }
            stageLogRepo.save(stageLog);

            if (result.isFailed()) {
                log.error("[research-stage] stage {} ({}) failed in {}ms: {}",
                    stage.getNumber(), stage.getKey(), elapsed, result.errorMessage());
            } else {
                log.info("[research-stage] stage {} ({}) completed in {}ms",
                    stage.getNumber(), stage.getKey(), elapsed);
            }
            return result;
        } catch (Throwable error) {
            long elapsed = Instant.now().toEpochMilli() - start.toEpochMilli();
            persistFailureLog(stageLog, elapsed, error);

            log.error("[research-stage] stage {} ({}) failed: {}",
                stage.getNumber(), stage.getKey(), describeThrowable(error), error);
            return StageResult.failure(describeThrowable(error));
        }
    }

    private void closeSupersededRunningLogs(UUID runId, int stageNumber, Instant resumedAt) {
        List<ResearchStageLogEntity> staleLogs =
            stageLogRepo.findByRunIdAndStageNumberAndStatusOrderByCreatedAtAsc(runId, stageNumber, "running");
        for (ResearchStageLogEntity staleLog : staleLogs) {
            staleLog.setStatus("failed");
            staleLog.setErrorMessage("superseded by resumed stage execution");
            if (staleLog.getCompletedAt() == null) {
                staleLog.setCompletedAt(resumedAt);
            }
            if (staleLog.getStartedAt() != null && staleLog.getElapsedMs() == null) {
                staleLog.setElapsedMs(Math.max(0L, resumedAt.toEpochMilli() - staleLog.getStartedAt().toEpochMilli()));
            }
            stageLogRepo.save(staleLog);
        }
    }

    private StageResult dispatch(ResearchStage stage, String context) {
        return switch (stage) {
            case TOPIC_INIT -> executeTopicAnalysis(context);
            case PROBLEM_DECOMPOSE -> executeProblemDecompose(context);
            case SEARCH_STRATEGY -> executeSearchStrategy(context);
            case LITERATURE_COLLECT -> executeLiteratureSearch(context);
            case LITERATURE_SCREEN -> executeLiteratureScreen(context);
            case KNOWLEDGE_EXTRACT -> executeKnowledgeExtract(context);
            case SYNTHESIS -> executeSynthesis(context);
            case HYPOTHESIS_GEN -> executeHypothesisGeneration(context);
            case EXPERIMENT_DESIGN -> executeExperimentDesign(context);
            case CODE_GENERATION -> executeCodeGeneration(context);
            case RESOURCE_PLANNING -> executeResourcePlanning(context);
            case EXPERIMENT_RUN -> executeExperimentRun(context);
            case ITERATIVE_REFINE -> executeIterativeRefine(context);
            case RESULT_ANALYSIS -> executeResultAnalysis(context);
            case RESEARCH_DECISION -> executeResearchDecision(context);
            case PAPER_OUTLINE -> executePaperOutline(context);
            case PAPER_DRAFT -> executePaperDraft(context);
            case PEER_REVIEW -> executePeerReview(context);
            case PAPER_REVISION -> executePaperRevision(context);
            case QUALITY_GATE -> executeQualityGate(context);
            case KNOWLEDGE_ARCHIVE -> executeKnowledgeArchive(context);
            case EXPORT_PUBLISH -> executeExportPublish(context);
            case CITATION_VERIFY -> executeCitationVerify(context);
        };
    }

    private StageResult applyPauseDecision(StageResult rawResult, boolean pauseAfterStage) {
        if (rawResult == null || rawResult.isFailed()) {
            return rawResult;
        }
        if (pauseAfterStage) {
            if (rawResult.requiresApproval()) {
                return rawResult;
            }
            return new StageResult("waiting_approval", rawResult.output(), true, null);
        }
        if (rawResult.requiresApproval()) {
            return StageResult.success(rawResult.output());
        }
        return rawResult;
    }

    // -- Stage 1: Topic Init --
    private StageResult executeTopicAnalysis(String topic) {
        String systemPrompt =
            "You are a research methodology expert. Analyze the given research topic and write a clear, readable analysis. "
            + "Use Markdown formatting with headings and bullet points. Include: "
            + "1. A refined, more specific and researchable topic statement "
            + "2. 5-10 key concepts relevant to this topic "
            + "3. 3-5 specific research questions worth investigating "
            + "4. Suggested research scope and boundaries "
            + "5. Initial methodology suggestions. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, "Research topic: " + topic);
        return StageResult.success(result);
    }

    // -- Stage 2: Problem Decompose --
    private StageResult executeProblemDecompose(String context) {
        String systemPrompt =
            "You are a research problem decomposition expert. Break the research topic "
            + "into sub-problems. Use Markdown formatting with headings and bullet points. Include: "
            + "1. 3-5 independent sub-problems, each with a clear description "
            + "2. Dependencies between sub-problems "
            + "3. Priority ranking with brief justification "
            + "4. Feasibility assessment for each sub-problem. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, context);
        return StageResult.success(result);
    }

    // -- Stage 3: Search Strategy --
    private StageResult executeSearchStrategy(String context) {
        String systemPrompt =
            "You are a literature search strategist. Design search queries for academic databases. "
            + "Use Markdown formatting with headings and bullet points. Include: "
            + "1. 3-5 search queries optimized for academic databases "
            + "2. Recommended databases to search and why "
            + "3. Paper inclusion criteria "
            + "4. Paper exclusion criteria. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, context);
        return StageResult.success(result);
    }

    // -- Stage 4: Literature Collect --
    private StageResult executeLiteratureSearch(String context) {
        String extractPrompt =
            "From the following research context, extract the 3 best search queries "
            + "for academic literature databases. Return ONLY a JSON array of strings. "
            + "Each query should be 3-8 words, optimized for academic search.\n\n"
            + context;

        String queriesJson = llmClient.complete(
            "You extract search queries from research analysis. Return ONLY a JSON array.",
            extractPrompt
        );

        List<LiteratureResult> allResults = new java.util.ArrayList<>();
        String[] queries = parseQueries(queriesJson);
        for (String query : queries) {
            allResults.addAll(literatureService.search(query, 10));
        }

        String summary = allResults.stream()
            .map(r -> String.format("- [%s] %s (%d, cited %d times) - %s",
                r.source(), r.title(), r.year(), r.citationCount(),
                truncate(r.abstractText(), 200)))
            .collect(Collectors.joining("\n"));

        return StageResult.success("Found " + allResults.size() + " papers:\n" + summary);
    }

    // -- Stage 6: Knowledge Extract --
    private StageResult executeKnowledgeExtract(String context) {
        String systemPrompt =
            "You are a systematic review expert. Extract knowledge from the literature. "
            + "Use Markdown formatting. Clearly describe: "
            + "1. Main research themes discovered "
            + "2. Areas of consensus in the field "
            + "3. Ongoing debates and controversies "
            + "4. Knowledge gaps that need filling "
            + "5. The 5 most relevant papers and why they matter. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, context);
        return StageResult.success(result);
    }

    // -- Stage 7: Synthesis --
    private StageResult executeSynthesis(String context) {
        String systemPrompt =
            "You are a research synthesis expert. Synthesize all Discovery phase findings. "
            + "Use Markdown formatting. Include: "
            + "1. Validated knowledge gaps "
            + "2. Prioritized research agenda "
            + "3. Novelty assessment (score 1-10 with justification) "
            + "4. Recommended research methodology and approaches "
            + "5. Executive summary of all findings. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, context);
        return StageResult.success(result);
    }

    // -- Stage 8: Hypothesis Generation --
    private StageResult executeHypothesisGeneration(String context) {
        String systemPrompt =
            "You are a research hypothesis generator. Use Markdown formatting. Present: "
            + "1. The main testable hypothesis "
            + "2. 2-3 alternative hypotheses "
            + "3. The null hypothesis "
            + "4. Key variables (independent, dependent, control) "
            + "5. Specific testable predictions. "
            + "Write in clear, natural prose. Do NOT output JSON.";

        String result = llmClient.complete(systemPrompt, context);
        return StageResult.success(result);
    }

    private StageResult executeLiteratureScreen(String context) {
        String result = llmClient.complete(
            "You are a systematic review screener. Review the collected papers. Use Markdown formatting. "
            + "Present: 1) Shortlisted papers with reasons for inclusion, "
            + "2) Excluded papers with reasons, 3) Evidence gaps identified, "
            + "4) Notes for the reviewer to approve or reject. "
            + "Write in clear, natural prose. Do NOT output JSON.",
            tailContext(context, 12000)
        );
        return new StageResult("waiting_approval", result, true, null);
    }

    private StageResult executeExperimentDesign(String context) {
        String result = llmClient.complete(
            "You are a rigorous experiment designer. Use Markdown formatting. Present: "
            + "1) Experiment objective, 2) Dataset plan, 3) Baseline plan, "
            + "4) Evaluation metrics, 5) Risks and mitigation, 6) Approval checklist. "
            + "Write in clear, natural prose. Do NOT output JSON.",
            tailContext(context, 12000)
        );
        return new StageResult("waiting_approval", result, true, null);
    }

    private StageResult executeCodeGeneration(String context) {
        String systemPrompt =
            "You are a research code generation planner. Use Markdown formatting. Present: "
            + "1) Files to create and their purpose, 2) Entry point, 3) Dependencies needed, "
            + "4) Step-by-step run instructions, 5) Validation plan, 6) Implementation notes. "
            + "Include concise code snippets where helpful. Write in clear prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeResourcePlanning(String context) {
        String systemPrompt =
            "You are a compute and resource planner. Use Markdown formatting. Describe: "
            + "1) Hardware requirements, 2) Software stack, 3) Time budget, "
            + "4) Dataset needs, 5) Estimated cost, 6) Operational risks. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeExperimentRun(String context) {
        String systemPrompt =
            "You are simulating a research experiment execution. Use Markdown formatting. Present: "
            + "1) Execution approach, 2) Key checkpoints, 3) Expected metrics, "
            + "4) Potential failure points, 5) Reproducibility notes. "
            + "This is a dry-run summary. Write in clear prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeIterativeRefine(String context) {
        String systemPrompt =
            "You are an experiment iteration planner. Use Markdown formatting. Describe: "
            + "1) Observed issues, 2) Refinement actions to take, 3) Expected improvement, "
            + "4) When to rollback, 5) Focus of next iteration. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeResultAnalysis(String context) {
        String systemPrompt =
            "You are a research analyst. Use Markdown formatting. Present: "
            + "1) Key findings, 2) Statistical takeaways, 3) Limitations, "
            + "4) Anomaly checks, 5) Suggested figures and visualizations. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeResearchDecision(String context) {
        String systemPrompt =
            "You are a principal investigator deciding whether to continue, pivot, or stop the research. "
            + "Use Markdown formatting. Present: 1) Your recommendation, 2) Rationale, "
            + "3) Required changes if continuing, 4) Notes for the reviewer. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return new StageResult("waiting_approval", llmClient.complete(systemPrompt, tailContext(context, 12000)), true, null);
    }

    private StageResult executePaperOutline(String context) {
        String systemPrompt =
            "You are a conference paper strategist. Use Markdown formatting. Present: "
            + "1) 2-3 candidate paper titles, 2) Abstract outline, "
            + "3) Detailed IMRAD section outline (Introduction, Methods, Results, Discussion). "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executePaperDraft(String context) {
        String systemPrompt =
            "You are drafting a research paper in markdown. Produce markdown with title, abstract, introduction, method, experiments, results, discussion, limitations, and conclusion. "
            + "Do not use placeholder text. Use concrete statements tied to the provided context.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 14000)));
    }

    private StageResult executePeerReview(String context) {
        String systemPrompt =
            "You are simulating conference peer review. Use Markdown formatting. Provide: "
            + "1) Scores (novelty, methodology, empirical quality, writing, overall - each out of 10), "
            + "2) Strengths of the paper, 3) Weaknesses, 4) Mandatory revisions needed. "
            + "Write in clear, natural prose like a real reviewer. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executePaperRevision(String context) {
        String systemPrompt =
            "You are revising a paper after peer review. Produce markdown with revised abstract, revised sections summary, and a response-to-reviewers checklist. Avoid placeholders.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 14000)));
    }

    private StageResult executeQualityGate(String context) {
        String systemPrompt =
            "You are a strict publication quality gate. Use Markdown formatting. Provide: "
            + "1) Scores (methodology, evidence, novelty, writing - each out of 10), "
            + "2) Risk assessment, 3) Verdict (pass/fail/conditional), 4) Notes for the reviewer. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return new StageResult("waiting_approval", llmClient.complete(systemPrompt, tailContext(context, 12000)), true, null);
    }

    private StageResult executeKnowledgeArchive(String context) {
        String systemPrompt =
            "You are archiving research lessons. Use Markdown formatting. Describe: "
            + "1) Reusable lessons learned, 2) Failed attempts and what to avoid, "
            + "3) Validated patterns, 4) Future work directions. "
            + "Write in clear, natural prose. Do NOT output JSON.";
        return StageResult.success(llmClient.complete(systemPrompt, tailContext(context, 12000)));
    }

    private StageResult executeExportPublish(String context) {
        String draft = llmClient.complete(
            "You are preparing the final deliverable package. Produce markdown with final title, abstract, sections, references checklist, and release notes. Avoid placeholders.",
            tailContext(context, 14000)
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("format", "markdown");
        payload.put("document", draft);
        payload.put("publishedAt", Instant.now().toString());
        return StageResult.success(toJson(payload));
    }

    private StageResult executeCitationVerify(String context) {
        List<String> candidateQueries = extractCitationQueries(context);
        List<LiteratureResult> verificationHits = new ArrayList<>();
        for (String query : candidateQueries) {
            verificationHits.addAll(literatureService.search(query, 3));
            if (verificationHits.size() >= 9) {
                break;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("queries", candidateQueries);
        payload.put("matchCount", verificationHits.size());
        payload.put("matches", verificationHits.stream().limit(9).map(result -> Map.of(
            "source", result.source(),
            "title", result.title(),
            "year", result.year(),
            "doi", result.doi() == null ? "" : result.doi(),
            "url", result.url() == null ? "" : result.url()
        )).collect(Collectors.toList()));
        payload.put("assessment", llmClient.complete(
            "You are verifying whether the available paper evidence supports the citations and claims. Provide a concise verification assessment with likely verified claims, suspicious areas, and next checks.",
            "Context:\n" + tailContext(context, 9000) + "\n\nEvidence:\n" + toJson(payload)
        ));
        return StageResult.success(toJson(payload));
    }

    // -- System Prompt Registry for Team Mode --

    /**
     * Return the system prompt for a given stage.
     * Used by the team executor to dispatch the same prompt to multiple models.
     */
    String getSystemPromptForStage(ResearchStage stage) {
        return switch (stage) {
            case TOPIC_INIT -> "You are a research methodology expert. Analyze the given research topic and write a clear, readable analysis. "
                    + "Use Markdown formatting with headings and bullet points. Include: "
                    + "1. A refined, more specific and researchable topic statement "
                    + "2. 5-10 key concepts relevant to this topic "
                    + "3. 3-5 specific research questions worth investigating "
                    + "4. Suggested research scope and boundaries "
                    + "5. Initial methodology suggestions. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case PROBLEM_DECOMPOSE -> "You are a research problem decomposition expert. Break the research topic "
                    + "into sub-problems. Use Markdown formatting with headings and bullet points. Include: "
                    + "1. 3-5 independent sub-problems, each with a clear description "
                    + "2. Dependencies between sub-problems "
                    + "3. Priority ranking with brief justification "
                    + "4. Feasibility assessment for each sub-problem. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case SEARCH_STRATEGY -> "You are a literature search strategist. Design search queries for academic databases. "
                    + "Use Markdown formatting with headings and bullet points. Include: "
                    + "1. 3-5 search queries optimized for academic databases "
                    + "2. Recommended databases to search and why "
                    + "3. Paper inclusion criteria "
                    + "4. Paper exclusion criteria. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case KNOWLEDGE_EXTRACT -> "You are a systematic review expert. Extract knowledge from the literature. "
                    + "Use Markdown formatting. Clearly describe: "
                    + "1. Main research themes discovered "
                    + "2. Areas of consensus in the field "
                    + "3. Ongoing debates and controversies "
                    + "4. Knowledge gaps that need filling "
                    + "5. The 5 most relevant papers and why they matter. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case SYNTHESIS -> "You are a research synthesis expert. Synthesize all Discovery phase findings. "
                    + "Use Markdown formatting. Include: "
                    + "1. Validated knowledge gaps "
                    + "2. Prioritized research agenda "
                    + "3. Novelty assessment (score 1-10 with justification) "
                    + "4. Recommended research methodology and approaches "
                    + "5. Executive summary of all findings. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case HYPOTHESIS_GEN -> "You are a research hypothesis generator. Use Markdown formatting. Present: "
                    + "1. The main testable hypothesis "
                    + "2. 2-3 alternative hypotheses "
                    + "3. The null hypothesis "
                    + "4. Key variables (independent, dependent, control) "
                    + "5. Specific testable predictions. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case LITERATURE_SCREEN -> "You are a systematic review screener. Review the collected papers. Use Markdown formatting. "
                    + "Present: 1) Shortlisted papers with reasons for inclusion, "
                    + "2) Excluded papers with reasons, 3) Evidence gaps identified, "
                    + "4) Notes for the reviewer to approve or reject. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case EXPERIMENT_DESIGN -> "You are a rigorous experiment designer. Use Markdown formatting. Present: "
                    + "1) Experiment objective, 2) Dataset plan, 3) Baseline plan, "
                    + "4) Evaluation metrics, 5) Risks and mitigation, 6) Approval checklist. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case CODE_GENERATION -> "You are a research code generation planner. Use Markdown formatting. Present: "
                    + "1) Files to create and their purpose, 2) Entry point, 3) Dependencies needed, "
                    + "4) Step-by-step run instructions, 5) Validation plan, 6) Implementation notes. "
                    + "Include concise code snippets where helpful. Write in clear prose. Do NOT output JSON.";
            case RESOURCE_PLANNING -> "You are a compute and resource planner. Use Markdown formatting. Describe: "
                    + "1) Hardware requirements, 2) Software stack, 3) Time budget, "
                    + "4) Dataset needs, 5) Estimated cost, 6) Operational risks. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case EXPERIMENT_RUN -> "You are simulating a research experiment execution. Use Markdown formatting. Present: "
                    + "1) Execution approach, 2) Key checkpoints, 3) Expected metrics, "
                    + "4) Potential failure points, 5) Reproducibility notes. "
                    + "This is a dry-run summary. Write in clear prose. Do NOT output JSON.";
            case ITERATIVE_REFINE -> "You are an experiment iteration planner. Use Markdown formatting. Describe: "
                    + "1) Observed issues, 2) Refinement actions to take, 3) Expected improvement, "
                    + "4) When to rollback, 5) Focus of next iteration. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case RESULT_ANALYSIS -> "You are a research analyst. Use Markdown formatting. Present: "
                    + "1) Key findings, 2) Statistical takeaways, 3) Limitations, "
                    + "4) Anomaly checks, 5) Suggested figures and visualizations. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case RESEARCH_DECISION -> "You are a principal investigator deciding whether to continue, pivot, or stop the research. "
                    + "Use Markdown formatting. Present: 1) Your recommendation, 2) Rationale, "
                    + "3) Required changes if continuing, 4) Notes for the reviewer. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case PAPER_OUTLINE -> "You are a conference paper strategist. Use Markdown formatting. Present: "
                    + "1) 2-3 candidate paper titles, 2) Abstract outline, "
                    + "3) Detailed IMRAD section outline (Introduction, Methods, Results, Discussion). "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case PAPER_DRAFT -> "You are drafting a research paper in markdown. Produce markdown with title, abstract, introduction, method, experiments, results, discussion, limitations, and conclusion. "
                    + "Do not use placeholder text. Use concrete statements tied to the provided context.";
            case PEER_REVIEW -> "You are simulating conference peer review. Use Markdown formatting. Provide: "
                    + "1) Scores (novelty, methodology, empirical quality, writing, overall - each out of 10), "
                    + "2) Strengths of the paper, 3) Weaknesses, 4) Mandatory revisions needed. "
                    + "Write in clear, natural prose like a real reviewer. Do NOT output JSON.";
            case PAPER_REVISION -> "You are revising a paper after peer review. Produce markdown with revised abstract, revised sections summary, and a response-to-reviewers checklist. Avoid placeholders.";
            case QUALITY_GATE -> "You are a strict publication quality gate. Use Markdown formatting. Provide: "
                    + "1) Scores (methodology, evidence, novelty, writing - each out of 10), "
                    + "2) Risk assessment, 3) Verdict (pass/fail/conditional), 4) Notes for the reviewer. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            case KNOWLEDGE_ARCHIVE -> "You are archiving research lessons. Use Markdown formatting. Describe: "
                    + "1) Reusable lessons learned, 2) Failed attempts and what to avoid, "
                    + "3) Validated patterns, 4) Future work directions. "
                    + "Write in clear, natural prose. Do NOT output JSON.";
            default -> "You are a research assistant. Analyze the provided context and produce a comprehensive Markdown response.";
        };
    }

    // -- Utilities --

    private String[] parseQueries(String json) {
        try {
            json = json.trim();
            if (json.startsWith("[")) {
                String[] arr = objectMapper.readValue(json, String[].class);
                if (arr.length > 0) return arr;
            }
        } catch (Exception ignored) {}
        return new String[]{ json.replaceAll("[\\[\\]\"']", "").trim() };
    }

    private void persistFailureLog(ResearchStageLogEntity stageLog, long elapsed, Throwable error) {
        stageLog.setStatus("failed");
        stageLog.setElapsedMs(elapsed);
        stageLog.setCompletedAt(Instant.now());
        stageLog.setOutputJson(null);
        stageLog.setErrorMessage(truncate(describeThrowable(error), 500));
        try {
            stageLogRepo.save(stageLog);
        } catch (Exception persistError) {
            log.error("[research-stage] failed to persist failure log for stage {} ({}): {}",
                stageLog.getStageNumber(), stageLog.getStageName(), persistError.getMessage(), persistError);
        }
    }

    private String describeThrowable(Throwable error) {
        if (error == null) {
            return "unknown stage failure";
        }
        String simpleName = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return simpleName;
        }
        return simpleName + ": " + message;
    }

    private String serializeStageOutput(String output, int maxLen) {
        String truncated = truncate(output, maxLen);
        return stageOutputNormalizer.normalizeStoredOutputJson(truncated);
    }

    private String guessOutputFormat(String text) {
        if (text == null || text.isBlank()) {
            return "text";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("#") || trimmed.contains("\n#") || trimmed.contains("```")) {
            return "markdown";
        }
        return "text";
    }

    private List<String> extractCitationQueries(String context) {
        List<String> queries = new ArrayList<>();
        String[] segments = tailContext(context, 8000).split("[\\r\\n]+");
        for (String segment : segments) {
            String trimmed = segment == null ? "" : segment.trim();
            if (trimmed.length() < 12) {
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
                continue;
            }
            String cleaned = trimmed.replaceAll("\\s+", " ");
            if (cleaned.length() > 90) {
                cleaned = cleaned.substring(0, 90);
            }
            if (!queries.contains(cleaned)) {
                queries.add(cleaned);
            }
            if (queries.size() >= 3) {
                break;
            }
        }
        if (queries.isEmpty()) {
            queries.add("research paper verification");
        }
        return queries;
    }

    private String tailContext(String context, int maxChars) {
        if (context == null) {
            return "";
        }
        if (context.length() <= maxChars) {
            return context;
        }
        return context.substring(context.length() - maxChars);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
