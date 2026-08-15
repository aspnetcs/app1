import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

export type AgentRunStatus =
  | 'pending'
  | 'approved'
  | 'rejected'
  | 'running'
  | 'completed'
  | 'failed'
  | (string & {})

export type AgentRunApprovalStatus = 'pending' | 'approved' | 'rejected' | (string & {})

export interface AgentRunApproval {
  status: AgentRunApprovalStatus
  decidedBy?: string | null
  decidedAt?: string | null
  note?: string | null
}

export interface AgentRun {
  id: string
  userId?: string | null
  agentId?: string | null
  requestedChannelId?: number | null
  boundChannelId?: number | null
  status: AgentRunStatus
  errorMessage?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  startedAt?: string | null
  completedAt?: string | null
  approval?: AgentRunApproval | null
}

export interface AgentRunListResponse {
  items: AgentRun[]
  total: number
  page: number
  size: number
}

const AGENT_RUNS_ROUTE = buildPlatformApiPath('agent-runs')

function asText(value: unknown): string | null {
  if (value == null) return null
  const text = String(value).trim()
  return text ? text : null
}

function asNumber(value: unknown): number | null {
  if (value == null) return null
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

function normalizeApproval(raw: unknown): AgentRunApproval | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>
  const status = asText(record.status)
  if (!status) return null
  return {
    status: status as AgentRunApprovalStatus,
    decidedBy: asText(record.decidedBy),
    decidedAt: asText(record.decidedAt),
    note: asText(record.note),
  }
}

function normalizeAgentRun(raw: unknown): AgentRun | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>
  const id = asText(record.id)
  const status = asText(record.status)
  if (!id || !status) return null
  return {
    id,
    userId: asText(record.userId),
    agentId: asText(record.agentId),
    requestedChannelId: asNumber(record.requestedChannelId),
    boundChannelId: asNumber(record.boundChannelId),
    status: status as AgentRunStatus,
    errorMessage: asText(record.errorMessage),
    createdAt: asText(record.createdAt),
    updatedAt: asText(record.updatedAt),
    startedAt: asText(record.startedAt),
    completedAt: asText(record.completedAt),
    approval: normalizeApproval(record.approval),
  }
}

function normalizeListPayload(payload: unknown, page: number, size: number): AgentRunListResponse {
  if (!payload || typeof payload !== 'object') return { items: [], total: 0, page, size }
  const record = payload as Record<string, unknown>
  const items = Array.isArray(record.items)
    ? record.items.map(normalizeAgentRun).filter((item): item is AgentRun => Boolean(item))
    : []
  const total = asNumber(record.total) ?? items.length
  const resolvedPage = asNumber(record.page) ?? page
  const resolvedSize = asNumber(record.size) ?? size
  return { items, total, page: resolvedPage, size: resolvedSize }
}

export async function createAgentRun(input: { agentId: string; requestedChannelId?: number | null }) {
  const payload = await http.post<Record<string, unknown>>(AGENT_RUNS_ROUTE, input, { auth: true, silent: false })
  return { ...payload, data: normalizeAgentRun(payload.data) ?? (payload.data as any) }
}

export async function listAgentRuns(input: { page?: number; size?: number } = {}) {
  const page = input.page ?? 0
  const size = input.size ?? 20
  const payload = await http.get<unknown>(AGENT_RUNS_ROUTE, { page, size }, { auth: true, silent: true })
  return { ...payload, data: normalizeListPayload(payload.data, page, size) }
}

export async function getAgentRun(id: string) {
  const payload = await http.get<Record<string, unknown>>(`${AGENT_RUNS_ROUTE}/${encodeURIComponent(id)}`, undefined, {
    auth: true,
    silent: true,
  })
  return { ...payload, data: normalizeAgentRun(payload.data) ?? (payload.data as any) }
}

export async function startAgentRun(id: string) {
  const payload = await http.post<Record<string, unknown>>(`${AGENT_RUNS_ROUTE}/${encodeURIComponent(id)}/start`, undefined, {
    auth: true,
    silent: false,
  })
  return { ...payload, data: normalizeAgentRun(payload.data) ?? (payload.data as any) }
}

export async function completeAgentRun(id: string) {
  const payload = await http.post<Record<string, unknown>>(`${AGENT_RUNS_ROUTE}/${encodeURIComponent(id)}/complete`, undefined, {
    auth: true,
    silent: false,
  })
  return { ...payload, data: normalizeAgentRun(payload.data) ?? (payload.data as any) }
}

export async function failAgentRun(id: string, errorMessage?: string) {
  const payload = await http.post<Record<string, unknown>>(
    `${AGENT_RUNS_ROUTE}/${encodeURIComponent(id)}/fail`,
    { errorMessage },
    { auth: true, silent: false }
  )
  return { ...payload, data: normalizeAgentRun(payload.data) ?? (payload.data as any) }
}

