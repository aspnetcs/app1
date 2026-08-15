/**
 * Team Debate API client
 * Endpoints for multi-turn team debate conversations.
 */
import { http } from './http'

// -- Types --

export interface TeamChatStartRequest {
  message: string
  modelIds: string[]
  captainSelectionMode?: 'auto' | 'fixed_first'
  knowledgeBaseIds?: string[]
}

export interface TeamChatStartResponse {
  conversationId: string
  turnId: string
  turnNumber: number
  captainSelectionMode: string
  models: string[]
  transportMode: string
}

export interface TeamChatContinueRequest {
  conversationId: string
  message: string
  knowledgeBaseIds?: string[]
}

export interface TeamChatContinueResponse {
  conversationId: string
  turnId: string
  turnNumber: number
  transportMode: string
}

export interface TeamChatDebateArgument {
  issueId: string
  argument: string
  stance: string
}

export interface TeamChatMemberStatus {
  modelId: string
  proposalStatus: 'pending' | 'completed' | 'timeout' | 'failed'
  debateStatus: 'pending' | 'completed' | 'failed'
  /** Proposal answer text (answerText from backend MemberProposal). Absent until COLLECTING completes. */
  summary?: string | null
  /** Debate arguments per issue from DEBATING stage. */
  debateArguments?: TeamChatDebateArgument[] | null
}

export interface TeamChatTurnStatus {
  turnId: string
  turnNumber: number
  stage: string
  captainModelId: string | null
  captainSource: string | null
  captainExplanation?: string | null
  userMessage: string
  issueCount: number
  issues: Array<{ issueId: string; title: string; resolved: boolean }>
  memberStatuses: TeamChatMemberStatus[]
  finalAnswer: string | null
  errorMessage: string | null
  failedModels: string[]
  stageTimestamps: Record<string, string>
}

export interface TeamChatStatusResponse {
  conversationId: string
  activeTurnId?: string | null
  models?: string[]
  completedTurns: number
  captainSelectionMode: string
  memoryVersion: number
  sharedSummary: string | null
  captainHistory: string[]
  turn?: TeamChatTurnStatus | { status: string }
}

export interface DecisionRecord {
  turnId: string
  turnNumber: number
  captainModelId: string | null
  captainSource: string | null
  userQuestion: string
  finalAnswerSummary: string
  keyIssues: string[]
  timestamp: string | null
}

export interface TeamChatHistoryResponse {
  conversationId: string
  activeTurnId?: string | null
  models: string[]
  captainSelectionMode: string
  completedTurns: number
  decisionHistory: DecisionRecord[]
  sharedSummary: string | null
  memoryVersion: number
}

export interface TeamChatEventRecord {
  cursor: number
  event: string
  data: Record<string, unknown>
  timestamp: string | null
}

export interface TeamChatEventsResponse {
  conversationId: string
  turnId: string | null
  cursor: number
  nextCursor: number
  completed: boolean
  stage: string
  events: TeamChatEventRecord[]
}

// -- API calls --

const BASE = '/v1/team-chat'

export const teamChatStart = (data: TeamChatStartRequest) =>
  http.post<TeamChatStartResponse>(`${BASE}/start`, data, { auth: true })

export const teamChatContinue = (data: TeamChatContinueRequest) =>
  http.post<TeamChatContinueResponse>(`${BASE}/continue`, data, { auth: true })

export const teamChatStatus = (conversationId: string, turnId?: string) => {
  const params: Record<string, string> = { conversationId }
  if (turnId) params.turnId = turnId
  return http.get<TeamChatStatusResponse>(`${BASE}/status`, params, { auth: true })
}

export const teamChatHistory = (conversationId: string) =>
  http.get<TeamChatHistoryResponse>(`${BASE}/history`, { conversationId }, { auth: true })

export const teamChatEvents = (conversationId: string, turnId?: string, cursor = 0) => {
  const params: Record<string, string | number> = { conversationId, cursor: Math.max(0, cursor) }
  if (turnId) params.turnId = turnId
  return http.get<TeamChatEventsResponse>(`${BASE}/events`, params, { auth: true })
}
