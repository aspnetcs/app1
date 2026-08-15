import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

// -- Types --

export interface ResearchProject {
  id: string
  name: string
  topic: string
  status: 'draft' | 'active' | 'completed' | 'archived'
  mode: string
  createdAt: string
  currentStage?: number
  totalStages?: number
  runStatus?: string
}

export interface ResearchProjectDetail extends ResearchProject {
  latestRun?: ResearchRun
  runs?: ResearchRun[]
}

export interface ResearchRun {
  id: string
  runNumber: number
  currentStage: number
  totalStages: number
  status: string
  iteration: number
  qualityScore: number | null
  startedAt: string | null
  completedAt: string | null
}

export interface ResearchStageLog {
  id: string
  stageNumber: number
  stageName: string
  status: string
  decision: string | null
  elapsedMs: number | null
  tokensUsed: number
  errorMessage: string | null
  outputJson: string | null
  startedAt: string | null
  completedAt: string | null
}

export interface LiteratureItem {
  source: string
  externalId: string
  title: string
  authors: string[]
  abstract: string
  year: number
  doi: string | null
  url: string
  citationCount: number
}

export interface ResearchExportPayload {
  filename: string
  mimeType: string
  contentBase64: string
  stageOutputs?: Record<string, { format: 'markdown' | 'text'; content: string }>
  runId: string
  projectId: string
}

export interface ResearchFeatureConfig {
  enabled: boolean
  literatureEnabled: boolean
  experimentEnabled: boolean
  paperEnabled: boolean
  message?: string
}

// -- Project API --

const BASE = buildPlatformApiPath('research')

export function getResearchFeatureConfig() {
  return http.get<ResearchFeatureConfig>(`${BASE}/config`, undefined, { silent: true })
}

export function listResearchProjects() {
  return http.get<ResearchProject[]>(`${BASE}/projects`, undefined, { auth: true })
}

export function getResearchProject(id: string) {
  return http.get<ResearchProjectDetail>(`${BASE}/projects/${id}`, undefined, { auth: true })
}

export function createResearchProject(
  name: string,
  topic: string,
  mode: string = 'single',
  options?: { templateText?: string; executionMode?: string; pauseStages?: string[] },
) {
  return http.post<ResearchProject>(`${BASE}/projects`, {
    name,
    topic,
    mode,
    ...(options?.templateText ? { templateText: options.templateText } : {}),
    ...(options?.executionMode ? { executionMode: options.executionMode } : {}),
    ...(options?.pauseStages?.length ? { pauseStages: options.pauseStages } : {}),
  }, { auth: true })
}

export function deleteResearchProject(id: string) {
  return http.delete<void>(`${BASE}/projects/${id}`, undefined, { auth: true })
}

export function updateResearchProject(
  id: string,
  data: { templateText?: string; executionMode?: string; pauseStages?: number[] },
) {
  return http.put<ResearchProject>(`${BASE}/projects/${id}`, data, { auth: true })
}

export function startResearchRun(
  projectId: string,
  options?: { sourceRunId?: string; restartFromStage?: number; branchPrompt?: string },
) {
  return http.post<ResearchRun>(`${BASE}/projects/${projectId}/runs`, options ?? {}, { auth: true })
}

export function getResearchRunStatus(projectId: string, runId: string) {
  return http.get<ResearchRun & { stageLogs: ResearchStageLog[] }>(
    `${BASE}/projects/${projectId}/runs/${runId}`, undefined, { auth: true }
  )
}

export function approveResearchGate(projectId: string, runId: string, decision: string) {
  return http.post<ResearchRun>(
    `${BASE}/projects/${projectId}/runs/${runId}/approve`, { decision }, { auth: true }
  )
}

export function exportResearchProject(projectId: string, mode: 'result' | 'full' = 'result') {
  return http.get<ResearchExportPayload>(`${BASE}/projects/${projectId}/export`, { mode }, { auth: true })
}

// -- Literature API --

export function searchLiterature(query: string, limit = 20) {
  return http.get<LiteratureItem[]>(
    `${BASE}/literature/search`, { query, limit }, { auth: true }
  )
}
