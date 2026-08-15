/**
 * Team Debate Store (Pinia)
 *
 * Manages multi-turn team debate conversation state.
 * Consumes both REST API responses and WebSocket events.
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useAiContextStore } from './aiContext'
import {
  teamChatStart,
  teamChatContinue,
  teamChatStatus,
  teamChatHistory,
  teamChatEvents,
} from '@/api/debate'
import type {
  TeamChatStartRequest,
  TeamChatStatusResponse,
  TeamChatHistoryResponse,
  TeamChatEventsResponse,
  DecisionRecord,
  TeamChatTurnStatus,
  TeamChatMemberStatus,
  TeamChatDebateArgument,
} from '@/api/debate'

// -- Constants --

const AGENT_AVATAR_COLORS = [
  '#6366f1',
  '#10b981',
  '#f59e0b',
  '#ef4444',
  '#8b5cf6',
  '#06b6d4',
  '#f97316',
  '#84cc16',
]

// -- Types --

export type DebateStage =
  | 'IDLE'
  | 'COLLECTING'
  | 'VOTING'
  | 'EXTRACTING'
  | 'DEBATING'
  | 'SYNTHESIZING'
  | 'COMPLETED'
  | 'FAILED'

export interface DebateIssue {
  issueId: string
  title: string
  resolved: boolean
}

export interface MemberStatus {
  modelId: string
  proposalStatus: 'pending' | 'completed' | 'timeout' | 'failed'
  debateStatus: 'pending' | 'completed' | 'failed'
}

export interface TeamAgentCard {
  modelId: string
  displayName: string
  avatarColor: string
  proposalStatus: 'pending' | 'completed' | 'timeout' | 'failed'
  debateStatus: 'pending' | 'completed' | 'failed'
  isLeader: boolean
  summary: string | null
  debateArguments: TeamChatDebateArgument[]
  updatedAt: number | null
}

export interface TeamAgentDetailEntry {
  id: string
  kind: 'summary' | 'argument'
  label: string
  content: string
  stance?: string | null
  issueId?: string | null
}

export interface TeamProcessSummary {
  title: string
  stageLabel: string
  agentCount: number
  respondedAgentCount: number
  captainModelId: string | null
  captainSource: string | null
  startedAt: number | null
  hasAnyDetail: boolean
}

export interface ReasoningState {
  supported: boolean
  visible: boolean
  status: 'thinking' | 'completed' | 'unavailable'
  durationMs: number
  text: string | null
}

export interface TurnRecord {
  turnId: string
  turnNumber: number
  captainModelId: string | null
  captainSource: string | null
  userQuestion: string
  finalAnswer: string | null
  issues: DebateIssue[]
  stage: DebateStage
  errorMessage: string | null
}

export { type DecisionRecord } from '@/api/debate'

interface DebateStateSnapshot {
  conversationId: string | null
  selectedModelIds: string[]
  captainSelectionMode: 'auto' | 'fixed_first'
  sharedSummary: string | null
  decisionHistory: DecisionRecord[]
  memoryVersion: number
  completedTurns: number
  captainHistory: string[]
  currentTurnId: string | null
  currentTurnNumber: number
  currentStage: DebateStage
  currentCaptainModel: string | null
  captainSource: string | null
  captainExplanation: string | null
  issues: DebateIssue[]
  finalAnswer: string | null
  errorMessage: string | null
  memberStatuses: MemberStatus[]
  turnSummaries: TurnRecord[]
  eventCursor: number
  eventCursorPrimed: boolean
  isActive: boolean
  isLoading: boolean
  agentCards: TeamAgentCard[]
  expandedAgentIds: string[]
  currentTurnStartedAt: number | null
}

type DebateEventPayload = Record<string, unknown>

function asString(value: unknown): string | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  return normalized ? normalized : null
}

function asTextChunk(value: unknown): string | null {
  if (typeof value !== 'string') return null
  return value.length > 0 ? value : null
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => asString(item))
    .filter((item): item is string => Boolean(item))
}

function asDebateStage(value: unknown): DebateStage | null {
  if (typeof value !== 'string') return null
  switch (value) {
    case 'IDLE':
    case 'COLLECTING':
    case 'VOTING':
    case 'EXTRACTING':
    case 'DEBATING':
    case 'SYNTHESIZING':
    case 'COMPLETED':
    case 'FAILED':
      return value
    default:
      return null
  }
}

function asIssueList(value: unknown): DebateIssue[] {
  if (!Array.isArray(value)) return []
  return value.map((item, index) => {
    const record = item && typeof item === 'object'
      ? (item as Record<string, unknown>)
      : {}
    return {
      issueId: asString(record.issueId) || `issue-${index + 1}`,
      title: asString(record.title) || '',
      resolved: Boolean(record.resolved),
    }
  })
}

function asMemberStatusList(value: unknown): TeamChatMemberStatus[] {
  if (!Array.isArray(value)) return []
  return value.flatMap((item) => {
    const record = item && typeof item === 'object'
      ? (item as Record<string, unknown>)
      : {}
    const modelId = asString(record.modelId)
    if (!modelId) {
      return []
    }
    const proposalStatus = asString(record.proposalStatus)
    const debateStatus = asString(record.debateStatus)
    return [{
      modelId,
      proposalStatus: proposalStatus === 'completed' || proposalStatus === 'timeout' || proposalStatus === 'failed'
        ? proposalStatus
        : 'pending',
      debateStatus: debateStatus === 'completed' || debateStatus === 'failed'
        ? debateStatus
        : 'pending',
    }]
  })
}

function asTimestampMs(value: unknown): number | null {
  if (typeof value !== 'string') return null
  const parsed = Date.parse(value)
  return Number.isNaN(parsed) ? null : parsed
}

export function getDebateStageLabel(stage: DebateStage): string {
  const labels: Record<DebateStage, string> = {
    IDLE: '就绪',
    COLLECTING: '收集提案',
    VOTING: '选举队长',
    EXTRACTING: '提炼议题',
    DEBATING: '模型辩论',
    SYNTHESIZING: '综合回答',
    COMPLETED: '已完成',
    FAILED: '失败',
  }
  return labels[stage]
}

function resolveTurnStartedAt(stageTimestamps?: Record<string, string>): number | null {
  if (!stageTimestamps) return null
  const timestamps = Object.values(stageTimestamps)
    .map((value) => asTimestampMs(value))
    .filter((value): value is number => value !== null)
  if (timestamps.length === 0) return null
  return Math.min(...timestamps)
}

// -- Store --

export const useDebateStore = defineStore('debate', () => {
  const aiContextStore = useAiContextStore()
  // Session-level state
  const conversationId = ref<string | null>(null)
  const selectedModelIds = ref<string[]>([])
  const captainSelectionMode = ref<'auto' | 'fixed_first'>('auto')
  const sharedSummary = ref<string | null>(null)
  const decisionHistory = ref<DecisionRecord[]>([])
  const memoryVersion = ref(0)
  const completedTurns = ref(0)
  const captainHistory = ref<string[]>([])

  // Current turn state
  const currentTurnId = ref<string | null>(null)
  const currentTurnNumber = ref(0)
  const currentStage = ref<DebateStage>('IDLE')
  const currentCaptainModel = ref<string | null>(null)
  const captainSource = ref<string | null>(null)
  const captainExplanation = ref<string | null>(null)
  const issues = ref<DebateIssue[]>([])
  const finalAnswer = ref<string | null>(null)
  const errorMessage = ref<string | null>(null)
  const memberStatuses = ref<MemberStatus[]>([])
  const turnSummaries = ref<TurnRecord[]>([])
  const eventCursor = ref(0)
  const eventCursorPrimed = ref(false)

  // UI state
  const isActive = ref(false)
  const isLoading = ref(false)

  // Agent feed state (Grok-style right panel)
  const agentCards = ref<TeamAgentCard[]>([])
  const expandedAgentIds = ref<Set<string>>(new Set())
  const currentTurnStartedAt = ref<number | null>(null)

  // -- Computed --

  const hasActiveSession = computed(() => conversationId.value !== null)
  const isInProgress = computed(() =>
    currentStage.value !== 'IDLE' &&
    currentStage.value !== 'COMPLETED' &&
    currentStage.value !== 'FAILED'
  )
  const captainModeLabel = computed(() =>
    captainSelectionMode.value === 'fixed_first' ? '固定队长' : '自动选举'
  )
  const processSummary = computed<TeamProcessSummary>(() => {
    const respondedAgentCount = agentCards.value.filter((card) =>
      card.proposalStatus !== 'pending' ||
      card.debateStatus !== 'pending' ||
      Boolean(card.summary) ||
      card.debateArguments.length > 0
    ).length
    return {
      title: '团队过程',
      stageLabel: getDebateStageLabel(currentStage.value),
      agentCount: agentCards.value.length,
      respondedAgentCount,
      captainModelId: currentCaptainModel.value,
      captainSource: captainSource.value,
      startedAt: currentTurnStartedAt.value,
      hasAnyDetail: agentCards.value.some((card) =>
        Boolean(card.summary) || card.debateArguments.length > 0
      ),
    }
  })

  // -- Actions --

  function resetEventCursor() {
    eventCursor.value = 0
    eventCursorPrimed.value = false
  }

  function reset() {
    conversationId.value = null
    selectedModelIds.value = []
    captainSelectionMode.value = 'auto'
    sharedSummary.value = null
    decisionHistory.value = []
    memoryVersion.value = 0
    completedTurns.value = 0
    captainHistory.value = []
    resetTurn()
    turnSummaries.value = []
    isActive.value = false
    isLoading.value = false
    resetEventCursor()
  }

  function resetTurn() {
    currentTurnId.value = null
    currentTurnNumber.value = 0
    currentStage.value = 'IDLE'
    currentCaptainModel.value = null
    captainSource.value = null
    captainExplanation.value = null
    issues.value = []
    finalAnswer.value = null
    errorMessage.value = null
    memberStatuses.value = []
    agentCards.value = []
    expandedAgentIds.value = new Set()
    currentTurnStartedAt.value = null
    resetEventCursor()
  }

  function snapshotState(): DebateStateSnapshot {
    return {
      conversationId: conversationId.value,
      selectedModelIds: [...selectedModelIds.value],
      captainSelectionMode: captainSelectionMode.value,
      sharedSummary: sharedSummary.value,
      decisionHistory: decisionHistory.value.map((record) => ({
        ...record,
        keyIssues: [...record.keyIssues],
      })),
      memoryVersion: memoryVersion.value,
      completedTurns: completedTurns.value,
      captainHistory: [...captainHistory.value],
      currentTurnId: currentTurnId.value,
      currentTurnNumber: currentTurnNumber.value,
      currentStage: currentStage.value,
      currentCaptainModel: currentCaptainModel.value,
      captainSource: captainSource.value,
      captainExplanation: captainExplanation.value,
      issues: issues.value.map((issue) => ({ ...issue })),
      finalAnswer: finalAnswer.value,
      errorMessage: errorMessage.value,
      memberStatuses: memberStatuses.value.map((member) => ({ ...member })),
      turnSummaries: turnSummaries.value.map((turn) => ({
        ...turn,
        issues: turn.issues.map((issue) => ({ ...issue })),
      })),
      eventCursor: eventCursor.value,
      eventCursorPrimed: eventCursorPrimed.value,
      isActive: isActive.value,
      isLoading: isLoading.value,
      agentCards: agentCards.value.map((card) => ({
        ...card,
        debateArguments: [...card.debateArguments],
      })),
      expandedAgentIds: [...expandedAgentIds.value],
      currentTurnStartedAt: currentTurnStartedAt.value,
    }
  }

  function restoreState(snapshot: DebateStateSnapshot) {
    conversationId.value = snapshot.conversationId
    selectedModelIds.value = [...snapshot.selectedModelIds]
    captainSelectionMode.value = snapshot.captainSelectionMode
    sharedSummary.value = snapshot.sharedSummary
    decisionHistory.value = snapshot.decisionHistory.map((record) => ({
      ...record,
      keyIssues: [...record.keyIssues],
    }))
    memoryVersion.value = snapshot.memoryVersion
    completedTurns.value = snapshot.completedTurns
    captainHistory.value = [...snapshot.captainHistory]
    currentTurnId.value = snapshot.currentTurnId
    currentTurnNumber.value = snapshot.currentTurnNumber
    currentStage.value = snapshot.currentStage
    currentCaptainModel.value = snapshot.currentCaptainModel
    captainSource.value = snapshot.captainSource
    captainExplanation.value = snapshot.captainExplanation
    issues.value = snapshot.issues.map((issue) => ({ ...issue }))
    finalAnswer.value = snapshot.finalAnswer
    errorMessage.value = snapshot.errorMessage
    memberStatuses.value = snapshot.memberStatuses.map((member) => ({ ...member }))
    turnSummaries.value = snapshot.turnSummaries.map((turn) => ({
      ...turn,
      issues: turn.issues.map((issue) => ({ ...issue })),
    }))
    eventCursor.value = snapshot.eventCursor
    eventCursorPrimed.value = snapshot.eventCursorPrimed
    isActive.value = snapshot.isActive
    isLoading.value = snapshot.isLoading
    agentCards.value = (snapshot.agentCards || []).map((card) => ({
      ...card,
      debateArguments: [...(card.debateArguments || [])],
    }))
    expandedAgentIds.value = new Set(snapshot.expandedAgentIds || [])
    currentTurnStartedAt.value = snapshot.currentTurnStartedAt ?? null
  }

  function markCurrentTurnFailed(message: string) {
    currentStage.value = 'FAILED'
    errorMessage.value = message
    memberStatuses.value = memberStatuses.value.map((member) => ({
      ...member,
      proposalStatus: member.proposalStatus === 'pending' ? 'failed' : member.proposalStatus,
      debateStatus: member.debateStatus === 'pending' ? 'failed' : member.debateStatus,
    }))
  }

  // -- Agent Card Helpers --

  function getAvatarColor(index: number): string {
    return AGENT_AVATAR_COLORS[index % AGENT_AVATAR_COLORS.length]
  }

  function buildAgentCards(modelIds: string[]): TeamAgentCard[] {
    return modelIds.map((modelId, index) => ({
      modelId,
      displayName: modelId,
      avatarColor: getAvatarColor(index),
      proposalStatus: 'pending',
      debateStatus: 'pending',
      isLeader: false,
      summary: null,
      debateArguments: [],
      updatedAt: null,
    }))
  }

  function toggleAgentExpanded(modelId: string): void {
    const next = new Set(expandedAgentIds.value)
    if (next.has(modelId)) {
      next.delete(modelId)
    } else {
      next.add(modelId)
    }
    expandedAgentIds.value = next
  }

  function isAgentExpanded(modelId: string): boolean {
    return expandedAgentIds.value.has(modelId)
  }

  function createPendingMemberStatuses(modelIds: string[]): MemberStatus[] {
    return modelIds.map(id => ({
      modelId: id,
      proposalStatus: 'pending',
      debateStatus: 'pending',
    }))
  }

  function normalizeMemberStatuses(
    allModelIds: string[],
    statuses?: TeamChatMemberStatus[]
  ): MemberStatus[] {
    if (!statuses || statuses.length === 0) {
      return createPendingMemberStatuses(allModelIds)
    }

    const byModelId = new Map(statuses.map(status => [status.modelId, status]))
    return allModelIds.map(modelId => {
      const status = byModelId.get(modelId)
      return {
        modelId,
        proposalStatus: status?.proposalStatus || 'pending',
        debateStatus: status?.debateStatus || 'pending',
      }
    })
  }

  function syncAgentCardsFromStatuses(
    allModelIds: string[],
    captainId: string | null,
    statuses?: TeamChatMemberStatus[]
  ): void {
    const byModelId = new Map((statuses || []).map(s => [s.modelId, s]))
    // Preserve existing cards (keep expanded state, colors stay stable)
    const existingByModelId = new Map(agentCards.value.map(c => [c.modelId, c]))

    agentCards.value = allModelIds.map((modelId, index) => {
      const existing = existingByModelId.get(modelId)
      const status = byModelId.get(modelId)
      return {
        modelId,
        displayName: existing?.displayName || modelId,
        avatarColor: existing?.avatarColor || getAvatarColor(index),
        proposalStatus: status?.proposalStatus || 'pending',
        debateStatus: status?.debateStatus || 'pending',
        isLeader: modelId === captainId,
        summary: status?.summary ?? existing?.summary ?? null,
        debateArguments: status?.debateArguments ?? existing?.debateArguments ?? [],
        updatedAt: existing?.updatedAt || null,
      }
    })
  }

  async function startTeamChat(message: string, modelIds: string[], mode: 'auto' | 'fixed_first' = 'auto') {
    isLoading.value = true
    try {
      const req: TeamChatStartRequest = {
        message,
        modelIds,
        captainSelectionMode: mode,
        knowledgeBaseIds: [...aiContextStore.selectedKnowledgeBaseIds],
      }
      const res = await teamChatStart(req)
      const data = res.data

      conversationId.value = data.conversationId
      currentTurnId.value = data.turnId
      currentTurnNumber.value = data.turnNumber || 1
      selectedModelIds.value = data.models
      captainSelectionMode.value = mode
      isActive.value = true
      currentStage.value = 'COLLECTING'
      resetEventCursor()

      // Initialize member statuses
      memberStatuses.value = createPendingMemberStatuses(data.models)
      agentCards.value = buildAgentCards(data.models)
      currentTurnStartedAt.value = Date.now()

      return data.conversationId
    } finally {
      isLoading.value = false
    }
  }

  async function continueTeamChat(message: string) {
    if (!conversationId.value) throw new Error('当前没有活跃的团队会话')

    isLoading.value = true
    resetTurn()
    currentStage.value = 'COLLECTING'

    try {
      const res = await teamChatContinue({
        conversationId: conversationId.value,
        message,
        knowledgeBaseIds: [...aiContextStore.selectedKnowledgeBaseIds],
      })
      const data = res.data
      currentTurnId.value = data.turnId
      currentTurnNumber.value = data.turnNumber || (completedTurns.value + 1)

      // Re-initialize member statuses for new turn
      memberStatuses.value = createPendingMemberStatuses(selectedModelIds.value)
      agentCards.value = buildAgentCards(selectedModelIds.value)
      currentTurnStartedAt.value = Date.now()

      return data.turnId
    } finally {
      isLoading.value = false
    }
  }

  async function fetchStatus(options: { strict?: boolean } = {}) {
    if (!conversationId.value) return

    try {
      const res = await teamChatStatus(
        conversationId.value,
        currentTurnId.value || undefined
      )
      applyStatusResponse(res.data)
      return true
    } catch (error) {
      if (options.strict) {
        throw error
      }
      return false
    }
  }

  async function fetchHistory(options: { strict?: boolean } = {}) {
    if (!conversationId.value) return

    try {
      const res = await teamChatHistory(conversationId.value)
      applyHistoryResponse(res.data)
      return true
    } catch (error) {
      if (options.strict) {
        throw error
      }
      return false
    }
  }

  async function recoverSession(convId: string) {
    conversationId.value = convId
    isActive.value = true
    isLoading.value = true

    try {
      await fetchHistory({ strict: true })
      await fetchStatus({ strict: true })
    } catch (error) {
      reset()
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function fetchEvents(options: {
    consume?: boolean
    strict?: boolean
    cursor?: number
  } = {}) {
    if (!conversationId.value || !currentTurnId.value) return false

    const requestCursor = typeof options.cursor === 'number'
      ? Math.max(0, options.cursor)
      : eventCursor.value

    try {
      const res = await teamChatEvents(
        conversationId.value,
        currentTurnId.value,
        requestCursor
      )
      applyEventsResponse(res.data, options.consume !== false)
      return true
    } catch (error) {
      if (options.strict) {
        throw error
      }
      return false
    }
  }

  async function primeEventCursor(options: { strict?: boolean } = {}) {
    return fetchEvents({
      consume: false,
      strict: options.strict,
      cursor: 0,
    })
  }

  // -- WebSocket Event Handlers --

  function archiveCurrentTurn() {
    if (currentTurnId.value && currentStage.value === 'COMPLETED' && finalAnswer.value) {
      turnSummaries.value.push({
        turnId: currentTurnId.value,
        turnNumber: currentTurnNumber.value,
        captainModelId: currentCaptainModel.value,
        captainSource: captainSource.value,
        userQuestion: '',
        finalAnswer: finalAnswer.value,
        issues: [...issues.value],
        stage: 'COMPLETED',
        errorMessage: null,
      })
    }
  }

  function handleTurnStarted(payload: DebateEventPayload) {
    // Archive previous completed turn to history before starting new one
    archiveCurrentTurn()

    currentTurnId.value = asString(payload.turnId)
    currentTurnNumber.value = typeof payload.turnNumber === 'number' ? payload.turnNumber : 0
    currentStage.value = 'COLLECTING'
    const models = asStringArray(payload.models)
    if (models.length > 0) {
      selectedModelIds.value = models
    }
    currentCaptainModel.value = null
    captainSource.value = null
    captainExplanation.value = null
    issues.value = []
    finalAnswer.value = null
    errorMessage.value = null
    resetEventCursor()
    const activeModels = models.length > 0 ? models : selectedModelIds.value
    memberStatuses.value = createPendingMemberStatuses(activeModels)
    agentCards.value = buildAgentCards(activeModels)
    expandedAgentIds.value = new Set()
    currentTurnStartedAt.value = Date.now()
  }

  function handleStageChanged(payload: DebateEventPayload) {
    const stage = asDebateStage(payload.stage)
    if (stage) currentStage.value = stage
    const captainModelId = asString(payload.captainModelId)
    if (captainModelId) currentCaptainModel.value = captainModelId
  }

  function handleMemberProposal(payload: DebateEventPayload) {
    const modelId = asString(payload.modelId)
    if (!modelId) return
    const status = asString(payload.status)
    const proposalStatus = status === 'completed'
      ? 'completed'
      : status === 'timeout'
        ? 'timeout'
        : 'failed'

    const member = memberStatuses.value.find(m => m.modelId === modelId)
    if (member) {
      member.proposalStatus = proposalStatus
    }

    const card = agentCards.value.find(c => c.modelId === modelId)
    if (card) {
      card.proposalStatus = proposalStatus
      const summary = asString(payload.answerText)
      if (summary) {
        card.summary = summary
      }
      card.updatedAt = Date.now()
    }
  }

  function handleCaptainElected(payload: DebateEventPayload) {
    const captainId = asString(payload.captainModelId)
    currentCaptainModel.value = captainId
    captainSource.value = asString(payload.captainSource)
    captainExplanation.value = asString(payload.explanation)
    // Update leader badge on agent cards
    for (const card of agentCards.value) {
      card.isLeader = card.modelId === captainId
    }
  }

  function handleIssuesExtracted(payload: DebateEventPayload) {
    issues.value = asIssueList(payload.issues).map((issue) => ({
      ...issue,
      resolved: false,
    }))
  }

  function handleDebateEntry(payload: DebateEventPayload) {
    const modelId = asString(payload.modelId)
    if (!modelId) return
    const member = memberStatuses.value.find(m => m.modelId === modelId)
    if (member) {
      member.debateStatus = 'completed'
    }
    const card = agentCards.value.find(c => c.modelId === modelId)
    if (card) {
      card.debateStatus = 'completed'
      const issueId = asString(payload.issueId)
      const argument = asString(payload.argument)
      const stance = asString(payload.stance)
      if (issueId && argument) {
        card.debateArguments = [
          ...card.debateArguments.filter(a => a.issueId !== issueId),
          { issueId, argument, stance: stance || '' },
        ]
      }
      card.updatedAt = Date.now()
    }
  }

  function handleFinalAnswerDelta(payload: DebateEventPayload) {
    const delta = asTextChunk(payload.delta)
    if (!delta || currentStage.value === 'COMPLETED' || currentStage.value === 'FAILED') {
      return
    }
    currentStage.value = 'SYNTHESIZING'
    finalAnswer.value = (finalAnswer.value || '') + delta
  }

  function handleFinalAnswerDone() {
    if (currentStage.value !== 'COMPLETED' && currentStage.value !== 'FAILED') {
      currentStage.value = 'SYNTHESIZING'
    }
  }

  function handleTurnCompleted(payload: DebateEventPayload) {
    const wasCompleted = currentStage.value === 'COMPLETED'
    currentStage.value = 'COMPLETED'
    finalAnswer.value = asString(payload.finalAnswer) || finalAnswer.value
    currentCaptainModel.value = asString(payload.captainModelId) || currentCaptainModel.value
    captainSource.value = asString(payload.captainSource) || captainSource.value

    // Mark all members as completed
    for (const member of memberStatuses.value) {
      if (member.proposalStatus === 'pending') member.proposalStatus = 'completed'
      if (member.debateStatus === 'pending') member.debateStatus = 'completed'
    }

    // Mark all agent cards as completed and update leader
    const captainId = currentCaptainModel.value
    for (const card of agentCards.value) {
      if (card.proposalStatus === 'pending') card.proposalStatus = 'completed'
      if (card.debateStatus === 'pending') card.debateStatus = 'completed'
      card.isLeader = card.modelId === captainId
    }

    if (!wasCompleted) {
      completedTurns.value++
    }
  }

  function handleTurnFailed(payload: DebateEventPayload) {
    currentTurnId.value = asString(payload.turnId) || currentTurnId.value
    markCurrentTurnFailed(asString(payload.error) || '未知错误')
    const incomingStatuses = asMemberStatusList(payload.memberStatuses)
    if (incomingStatuses.length > 0) {
      memberStatuses.value = normalizeMemberStatuses(selectedModelIds.value, incomingStatuses)
      return
    }
    memberStatuses.value = memberStatuses.value.map((member) => ({
      ...member,
      proposalStatus: member.proposalStatus === 'pending' ? 'failed' : member.proposalStatus,
      debateStatus: member.debateStatus === 'pending' ? 'failed' : member.debateStatus,
    }))
  }

  /**
   * Central WebSocket event dispatcher.
   * Call this from the WS message handler with event name and payload.
   */
  function handleWsEvent(event: string, payload: DebateEventPayload) {
    const payloadConversationId = asString(payload.conversationId)
    const payloadTurnId = asString(payload.turnId)
    if (!isActive.value) {
      return
    }
    if (payloadConversationId && conversationId.value && payloadConversationId !== conversationId.value) {
      return
    }
    if (event !== 'team.turn_started' && payloadTurnId) {
      if (!currentTurnId.value || payloadTurnId !== currentTurnId.value) {
        return
      }
    }
    switch (event) {
      case 'team.turn_started':
        handleTurnStarted(payload)
        break
      case 'team.stage_changed':
        handleStageChanged(payload)
        break
      case 'team.member_proposal':
        handleMemberProposal(payload)
        break
      case 'team.captain_elected':
        handleCaptainElected(payload)
        break
      case 'team.issues_extracted':
        handleIssuesExtracted(payload)
        break
      case 'team.debate_entry':
        handleDebateEntry(payload)
        break
      case 'team.final_answer_delta':
        handleFinalAnswerDelta(payload)
        break
      case 'team.final_answer_done':
        handleFinalAnswerDone()
        break
      case 'team.turn_completed':
        handleTurnCompleted(payload)
        break
      case 'team.turn_failed':
        handleTurnFailed(payload)
        break
    }
  }

  // -- Internal Helpers --

  function applyEventsResponse(data: TeamChatEventsResponse, consume: boolean) {
    const responseTurnId = data.turnId || null
    eventCursor.value = Math.max(0, data.nextCursor || data.cursor || 0)
    eventCursorPrimed.value = true

    if (!consume || !responseTurnId || responseTurnId !== currentTurnId.value) {
      return
    }

    for (const eventRecord of data.events || []) {
      const payload = eventRecord?.data && typeof eventRecord.data === 'object'
        ? eventRecord.data
        : {}
      handleWsEvent(eventRecord.event, payload)
    }
  }

  function applyStatusResponse(data: TeamChatStatusResponse) {
    const previousTurnId = currentTurnId.value
    currentTurnId.value = data.activeTurnId || currentTurnId.value
    if (data.activeTurnId && data.activeTurnId !== previousTurnId) {
      resetEventCursor()
    }
    if (data.models?.length) {
      selectedModelIds.value = data.models
    }
    completedTurns.value = data.completedTurns || 0
    captainSelectionMode.value = data.captainSelectionMode === 'fixed_first' ? 'fixed_first' : 'auto'
    memoryVersion.value = data.memoryVersion || 0
    sharedSummary.value = data.sharedSummary || null
    captainHistory.value = data.captainHistory || []

    if (data.turn && 'turnId' in data.turn) {
      const turn = data.turn as TeamChatTurnStatus
      const sameTurn = previousTurnId === turn.turnId
      if (!sameTurn) {
        resetEventCursor()
      }
      currentTurnId.value = turn.turnId
      currentTurnNumber.value = turn.turnNumber
      currentStage.value = (turn.stage as DebateStage) || 'IDLE'
      currentCaptainModel.value = turn.captainModelId || null
      captainSource.value = turn.captainSource || null
      captainExplanation.value = turn.captainExplanation || null
      const resolvedTurnStartedAt = resolveTurnStartedAt(turn.stageTimestamps)
      if (resolvedTurnStartedAt !== null) {
        currentTurnStartedAt.value = resolvedTurnStartedAt
      } else if (!sameTurn) {
        currentTurnStartedAt.value = null
      }
      if (typeof turn.finalAnswer === 'string') {
        finalAnswer.value = turn.finalAnswer
      } else if (!sameTurn || turn.stage === 'COMPLETED' || turn.stage === 'FAILED') {
        finalAnswer.value = null
      }
      errorMessage.value = turn.errorMessage || null
      memberStatuses.value = normalizeMemberStatuses(selectedModelIds.value, turn.memberStatuses)
      syncAgentCardsFromStatuses(selectedModelIds.value, turn.captainModelId || null, turn.memberStatuses)
      issues.value = (turn.issues || []).map(i => ({
        issueId: i.issueId,
        title: i.title,
        resolved: i.resolved,
      }))
    } else if (selectedModelIds.value.length > 0 && memberStatuses.value.length === 0) {
      memberStatuses.value = createPendingMemberStatuses(selectedModelIds.value)
      if (agentCards.value.length === 0) {
        agentCards.value = buildAgentCards(selectedModelIds.value)
      }
    }
  }

  function applyHistoryResponse(data: TeamChatHistoryResponse) {
    currentTurnId.value = data.activeTurnId || currentTurnId.value
    selectedModelIds.value = data.models || []
    captainSelectionMode.value = data.captainSelectionMode === 'fixed_first' ? 'fixed_first' : 'auto'
    completedTurns.value = data.completedTurns || 0
    decisionHistory.value = data.decisionHistory || []
    sharedSummary.value = data.sharedSummary || null
    memoryVersion.value = data.memoryVersion || 0

    // Rebuild turn summaries from decision history
    turnSummaries.value = decisionHistory.value.map(r => ({
      turnId: r.turnId,
      turnNumber: r.turnNumber,
      captainModelId: r.captainModelId,
      captainSource: r.captainSource,
      userQuestion: r.userQuestion || '',
      finalAnswer: r.finalAnswerSummary || null,
      issues: (r.keyIssues || []).map((t, i) => ({
        issueId: `issue-${i + 1}`,
        title: t,
        resolved: true,
      })),
      stage: 'COMPLETED' as DebateStage,
      errorMessage: null,
    }))
  }

  return {
    // State
    conversationId,
    selectedModelIds,
    captainSelectionMode,
    sharedSummary,
    decisionHistory,
    memoryVersion,
    completedTurns,
    captainHistory,
    currentTurnId,
    currentTurnNumber,
    currentStage,
    currentCaptainModel,
    captainSource,
    captainExplanation,
    issues,
    finalAnswer,
    errorMessage,
    memberStatuses,
    turnSummaries,
    eventCursor,
    eventCursorPrimed,
    isActive,
    isLoading,
    agentCards,
    expandedAgentIds,
    currentTurnStartedAt,

    // Computed
    hasActiveSession,
    isInProgress,
    captainModeLabel,
    processSummary,

    // Actions
    reset,
    resetTurn,
    startTeamChat,
    continueTeamChat,
    fetchStatus,
    fetchHistory,
    fetchEvents,
    primeEventCursor,
    recoverSession,
    snapshotState,
    restoreState,
    markCurrentTurnFailed,
    handleWsEvent,
    toggleAgentExpanded,
    isAgentExpanded,
  }
})
