package com.webchat.platformapi.research;

import java.util.Map;
import java.util.Set;

/**
 * 23-stage research pipeline definition.
 * Translated from AutoResearchClaw stages.py.
 */
public enum ResearchStage {

    TOPIC_INIT(1, "topic_init", Phase.DISCOVERY, false, true),
    PROBLEM_DECOMPOSE(2, "problem_decompose", Phase.DISCOVERY, false, true),
    SEARCH_STRATEGY(3, "search_strategy", Phase.DISCOVERY, false, true),
    LITERATURE_COLLECT(4, "literature_collect", Phase.DISCOVERY, false, false),
    LITERATURE_SCREEN(5, "literature_screen", Phase.DISCOVERY, true, true),
    KNOWLEDGE_EXTRACT(6, "knowledge_extract", Phase.DISCOVERY, false, true),
    SYNTHESIS(7, "synthesis", Phase.DISCOVERY, false, true),

    HYPOTHESIS_GEN(8, "hypothesis_gen", Phase.EXPERIMENTATION, false, true),
    EXPERIMENT_DESIGN(9, "experiment_design", Phase.EXPERIMENTATION, true, true),
    CODE_GENERATION(10, "code_generation", Phase.EXPERIMENTATION, false, true),
    RESOURCE_PLANNING(11, "resource_planning", Phase.EXPERIMENTATION, false, true),
    EXPERIMENT_RUN(12, "experiment_run", Phase.EXPERIMENTATION, false, false),
    ITERATIVE_REFINE(13, "iterative_refine", Phase.EXPERIMENTATION, false, true),

    RESULT_ANALYSIS(14, "result_analysis", Phase.ANALYSIS, false, true),
    RESEARCH_DECISION(15, "research_decision", Phase.ANALYSIS, true, true),

    PAPER_OUTLINE(16, "paper_outline", Phase.PUBLICATION, false, true),
    PAPER_DRAFT(17, "paper_draft", Phase.PUBLICATION, false, true),
    PEER_REVIEW(18, "peer_review", Phase.PUBLICATION, false, true),
    PAPER_REVISION(19, "paper_revision", Phase.PUBLICATION, false, true),
    QUALITY_GATE(20, "quality_gate", Phase.PUBLICATION, true, true),
    KNOWLEDGE_ARCHIVE(21, "knowledge_archive", Phase.PUBLICATION, false, false),
    EXPORT_PUBLISH(22, "export_publish", Phase.PUBLICATION, false, false),

    CITATION_VERIFY(23, "citation_verify", Phase.VERIFICATION, false, false);

    public enum Phase {
        DISCOVERY, EXPERIMENTATION, ANALYSIS, PUBLICATION, VERIFICATION
    }

    /** Gate stages requiring user approval before proceeding. */
    public static final Set<ResearchStage> GATE_STAGES = Set.of(
        LITERATURE_SCREEN, EXPERIMENT_DESIGN, RESEARCH_DECISION, QUALITY_GATE
    );

    /** Rollback targets when a gate stage decides to pivot or refine. */
    public static final Map<ResearchStage, ResearchStage> ROLLBACK_TARGETS = Map.of(
        RESEARCH_DECISION, HYPOTHESIS_GEN,
        QUALITY_GATE, PAPER_OUTLINE
    );

    private final int number;
    private final String key;
    private final Phase phase;
    private final boolean gate;
    private final boolean llmStage;

    ResearchStage(int number, String key, Phase phase, boolean gate, boolean llmStage) {
        this.number = number;
        this.key = key;
        this.phase = phase;
        this.gate = gate;
        this.llmStage = llmStage;
    }

    public int getNumber() { return number; }
    public String getKey() { return key; }
    public Phase getPhase() { return phase; }
    public boolean isGate() { return gate; }
    public boolean isLlmStage() { return llmStage; }

    /** Lookup stage by its 1-based number. */
    public static ResearchStage fromNumber(int number) {
        for (ResearchStage s : values()) {
            if (s.number == number) return s;
        }
        throw new IllegalArgumentException("No stage with number: " + number);
    }

    /** Lookup stage by its string key. */
    public static ResearchStage fromKey(String key) {
        for (ResearchStage s : values()) {
            if (s.key.equals(key)) return s;
        }
        throw new IllegalArgumentException("No stage with key: " + key);
    }
}
