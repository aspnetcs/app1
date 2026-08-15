import { apiFetch } from "./http";
import { ADMIN_AI_ROUTE_CONTRACT } from "./adminAiRouteContract";
import { buildAdminApiPath } from "./authRouteContract";

export type ResearchAdminConfig = {
  enabled: boolean;
  maxConcurrentPipelines: number;
  stageTimeoutMinutes: number;
  llm: {
    model: string;
    maxTokens: number;
    temperature: number;
  };
  literature: {
    enabled: boolean;
    sources: string;
    maxResultsPerSource: number;
  };
  experiment: {
    enabled: boolean;
    mode: string;
    timeBudgetSec: number;
  };
  paper: {
    enabled: boolean;
    maxIterations: number;
    qualityThreshold: number;
  };
};

export type ResearchAdminProject = {
  id: string;
  userId: string;
  name: string;
  topic: string;
  status: string;
  mode: string;
  createdAt: string | null;
  currentStage?: number;
  runStatus?: string;
  executionMode?: string;
  templateApplied?: boolean;
  pauseStages?: number[];
};

export type ResearchAdminProjectPage = {
  items: ResearchAdminProject[];
  total: number;
  page: number;
  size: number;
};

export type ResearchAdminStats = {
  enabled: boolean;
  totalProjects: number;
  activeProjects: number;
  totalRuns: number;
  activeRuns: number;
  completedRuns: number;
  avgCompletionSeconds: number;
};

const RESEARCH_PROJECTS_PATH = buildAdminApiPath("research/projects");
const RESEARCH_STATS_PATH = buildAdminApiPath("research/stats");

export function getResearchAdminConfig() {
  return apiFetch<ResearchAdminConfig>(ADMIN_AI_ROUTE_CONTRACT.researchConfig);
}

export function saveResearchAdminConfig(payload: ResearchAdminConfig) {
  return apiFetch<ResearchAdminConfig>(ADMIN_AI_ROUTE_CONTRACT.researchConfig, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function getResearchAdminProjects(page = 0, size = 20) {
  return apiFetch<ResearchAdminProjectPage>(`${RESEARCH_PROJECTS_PATH}?page=${page}&size=${size}`);
}

export function getResearchAdminStats() {
  return apiFetch<ResearchAdminStats>(RESEARCH_STATS_PATH);
}
