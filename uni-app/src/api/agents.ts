import { http } from './http'
import {
  buildAgentMarketPath,
} from './platformAgentRouteContract'

export type AgentScope = 'SYSTEM' | 'USER'
export type AgentMessageRole = 'system' | 'user' | 'assistant'
export type AgentCategory = 'prompt' | 'preset' | 'template' | (string & {})

export interface AgentContextMessage {
  role: AgentMessageRole
  content: string
}

export interface Agent {
  id: string
  name: string
  avatar?: string
  icon?: string
  description?: string
  category?: AgentCategory
  scope: AgentScope
  modelId?: string
  systemPrompt?: string
  firstMessage?: string
  contextMessages?: AgentContextMessage[]
  temperature?: number
  topP?: number
  maxTokens?: number
  enabled?: boolean
  featured?: boolean
  sortOrder?: number
  installCount?: number
  author?: string
  tags?: string
  userId?: string
  createdAt?: string
  updatedAt?: string
}

export interface AgentMarketQuery {
  category?: AgentCategory
}

type AgentMarketInput = AgentMarketQuery | AgentCategory

function normalizeAgentMarketQuery(input?: AgentMarketInput): AgentMarketQuery {
  if (!input) return {}
  if (typeof input === 'string') {
    const trimmed = input.trim()
    return trimmed ? { category: trimmed } : {}
  }
  const trimmed = input.category?.trim()
  return trimmed ? { category: trimmed } : {}
}

/** List public agents from the marketplace */
export function listMarketAgents(query?: AgentMarketInput) {
  const normalized = normalizeAgentMarketQuery(query)
  const path = buildAgentMarketPath(normalized.category)
  return http.get<Agent[]>(path, undefined, { auth: true, silent: true })
}

