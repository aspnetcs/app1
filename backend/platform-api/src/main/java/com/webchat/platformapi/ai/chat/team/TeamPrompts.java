package com.webchat.platformapi.ai.chat.team;

/**
 * Prompt templates for the team debate consensus engine.
 * <p>
 * These prompts are used by TeamLlmClient for non-streaming internal calls
 * during VOTING, EXTRACTING, DEBATING, and SYNTHESIZING stages.
 */
final class TeamPrompts {

    private TeamPrompts() {}

    // ========== VOTING (Scheme C) ==========

    static String schemeC_InitialRanking(String userQuestion, String anonymizedProposals) {
        return "You are an impartial judge evaluating multiple proposals for a user's question.\n\n"
                + "User question: " + userQuestion + "\n\n"
                + "Below are anonymized proposals from different team members. "
                + "Each proposal is labeled with a letter (A, B, C, ...).\n\n"
                + anonymizedProposals + "\n\n"
                + "Rank ALL proposals from best to worst. "
                + "Consider: correctness, completeness, clarity, and practical usefulness.\n\n"
                + "Respond with ONLY a JSON object in this exact format:\n"
                + "{\"ranking\": [\"A\", \"B\", \"C\"], \"reasoning\": \"brief explanation\"}\n"
                + "The ranking array must contain all proposal letters, best first.";
    }

    static String schemeC_PositionFlipVerification(String userQuestion,
                                                    String proposalFirst,
                                                    String labelFirst,
                                                    String proposalSecond,
                                                    String labelSecond) {
        return "You are an impartial judge. Compare these two proposals for the user's question.\n\n"
                + "User question: " + userQuestion + "\n\n"
                + "--- Proposal " + labelFirst + " ---\n" + proposalFirst + "\n\n"
                + "--- Proposal " + labelSecond + " ---\n" + proposalSecond + "\n\n"
                + "Which proposal is better overall? Consider correctness, completeness, "
                + "clarity, and practical usefulness.\n\n"
                + "Respond with ONLY a JSON object:\n"
                + "{\"winner\": \"" + labelFirst + "\" or \"" + labelSecond + "\", "
                + "\"confidence\": 0.0-1.0, \"reasoning\": \"brief explanation\"}";
    }

    // ========== EXTRACTING ==========

    static String extractIssues(String userQuestion, String allProposals,
                                String priorContext) {
        String contextBlock = (priorContext == null || priorContext.isBlank())
                ? ""
                : "\n\nPrior conversation context:\n" + priorContext + "\n";

        return "You are the team captain. Your job is to identify the key points of "
                + "DIVERGENCE between the team members' proposals.\n\n"
                + "User question: " + userQuestion + "\n"
                + contextBlock + "\n"
                + "Team proposals:\n" + allProposals + "\n\n"
                + "Extract the main issues where team members disagree or have different approaches. "
                + "For each issue, provide a clear title and description. You MUST output the title and description in Chinese (\u4e2d\u6587).\n\n"
                + "Respond with ONLY a JSON array:\n"
                + "[{\"issueId\": \"issue-1\", \"title\": \"...\", \"description\": \"...\"}, ...]\n\n"
                + "If all proposals substantially agree, return an empty array [].";
    }

    // ========== DEBATING ==========

    static String debateOnIssue(String userQuestion, String issueTitle, String issueDescription,
                                String otherStances, String modelProposal,
                                String priorContext) {
        String contextBlock = (priorContext == null || priorContext.isBlank())
                ? ""
                : "\n\nPrior conversation context:\n" + priorContext + "\n";

        return "You are a team member in a collaborative discussion.\n\n"
                + "User question: " + userQuestion + "\n"
                + contextBlock + "\n"
                + "Current issue under debate:\n"
                + "Title: " + issueTitle + "\n"
                + "Description: " + issueDescription + "\n\n"
                + "Your original proposal:\n" + modelProposal + "\n\n"
                + "Other team members' positions on this issue:\n" + otherStances + "\n\n"
                + "Review the other positions and respond. You may:\n"
                + "1. Maintain your stance with stronger arguments\n"
                + "2. Update your stance based on compelling points from others\n"
                + "3. Propose a synthesis that combines the best elements\n\n"
                + "You MUST output all text in Chinese (\u4e2d\u6587).\n\n"
                + "Respond with ONLY a JSON object:\n"
                + "{\"stance\": \"your position summary\", "
                + "\"argument\": \"your detailed response\", "
                + "\"stanceChanged\": true/false}";
    }

    // ========== SYNTHESIZING ==========

    static String synthesize(String userQuestion, String allProposals,
                             String debateSummary, String priorContext) {
        String contextBlock = (priorContext == null || priorContext.isBlank())
                ? ""
                : "\n\nPrior conversation context:\n" + priorContext + "\n";

        return "You are the team captain. Synthesize the team's discussion into a single, "
                + "optimal final answer for the user.\n\n"
                + "User question: " + userQuestion + "\n"
                + contextBlock + "\n"
                + "Team proposals and debate summary:\n" + allProposals + "\n\n"
                + "Key debate outcomes:\n" + debateSummary + "\n\n"
                + "Guidelines:\n"
                + "- Integrate the best insights from all team members\n"
                + "- Resolve remaining disagreements with the strongest evidence\n"
                + "- Provide a clear, comprehensive, and actionable answer\n"
                + "- Do NOT mention the team process, voting, or debate to the user\n"
                + "- Write as if you are directly answering the user's question\n"
                + "- You MUST output the final answer in Chinese (\u4e2d\u6587).\n\n"
                + "Provide the final answer directly:";
    }

    // ========== MEMORY COMPRESSION ==========

    static String compressMemory(String userQuestion, String finalAnswer,
                                 String debateSummary, String priorSummary) {
        String priorBlock = (priorSummary == null || priorSummary.isBlank())
                ? ""
                : "\n\nPrior shared summary:\n" + priorSummary + "\n";

        return "Compress the following conversation turn into a concise shared summary "
                + "that captures the essential information for future turns.\n\n"
                + "User question: " + userQuestion + "\n"
                + "Final answer: " + finalAnswer + "\n"
                + "Key debate points: " + debateSummary + "\n"
                + priorBlock + "\n"
                + "Rules:\n"
                + "- Keep the summary under 500 words\n"
                + "- Focus on decisions made, key facts established, and unresolved issues\n"
                + "- Do NOT include any model's thinking/reasoning process\n"
                + "- Do NOT include voting or captain election details\n"
                + "- You MUST output the summary in Chinese (\u4e2d\u6587).\n\n"
                + "Respond with the compressed summary text only:";
    }
}
