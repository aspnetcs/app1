import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAiContextStore } from './aiContext'
import { getDebateStageLabel, useDebateStore } from './debate'
import { teamChatContinue, teamChatEvents, teamChatHistory, teamChatStart, teamChatStatus } from '@/api/debate'

vi.mock('@/api/debate', () => ({
  teamChatStart: vi.fn(),
  teamChatContinue: vi.fn(),
  teamChatStatus: vi.fn(),
  teamChatHistory: vi.fn(),
  teamChatEvents: vi.fn(),
}))

describe('useDebateStore', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('recovers the active turn even when status is requested without an explicit turn id', async () => {
    vi.mocked(teamChatHistory).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        activeTurnId: 'turn-9',
        models: ['model-a', 'model-b'],
        captainSelectionMode: 'auto',
        completedTurns: 1,
        decisionHistory: [],
        sharedSummary: 'summary',
        memoryVersion: 2,
      },
    } as never)
    vi.mocked(teamChatStatus).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        activeTurnId: 'turn-9',
        models: ['model-a', 'model-b'],
        completedTurns: 1,
        captainSelectionMode: 'auto',
        memoryVersion: 2,
        sharedSummary: 'summary',
        captainHistory: ['model-a'],
        turn: {
          turnId: 'turn-9',
          turnNumber: 2,
          stage: 'COLLECTING',
          captainModelId: null,
          captainSource: null,
          userMessage: 'follow up',
          issueCount: 0,
          issues: [],
          memberStatuses: [
            { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'pending' },
            { modelId: 'model-b', proposalStatus: 'pending', debateStatus: 'pending' },
          ],
          finalAnswer: null,
          errorMessage: null,
          failedModels: [],
          stageTimestamps: {},
        },
      },
    } as never)

    const store = useDebateStore()
    await store.recoverSession('conv-1')

    expect(teamChatStatus).toHaveBeenCalledWith('conv-1', 'turn-9')
    expect(store.currentTurnId).toBe('turn-9')
    expect(store.currentStage).toBe('COLLECTING')
    expect(store.memberStatuses).toEqual([
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'pending' },
      { modelId: 'model-b', proposalStatus: 'pending', debateStatus: 'pending' },
    ])
  })

  it('passes selected knowledge base ids when starting a team chat', async () => {
    vi.mocked(teamChatStart).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        turnId: 'turn-1',
        turnNumber: 1,
        captainSelectionMode: 'auto',
        models: ['model-a', 'model-b'],
        transportMode: 'websocket',
      },
    } as never)

    const aiContextStore = useAiContextStore()
    aiContextStore.selectedKnowledgeBaseIds = ['kb-1', 'kb-2']
    const store = useDebateStore()

    await store.startTeamChat('hello', ['model-a', 'model-b'])

    expect(teamChatStart).toHaveBeenCalledWith({
      message: 'hello',
      modelIds: ['model-a', 'model-b'],
      captainSelectionMode: 'auto',
      knowledgeBaseIds: ['kb-1', 'kb-2'],
    })
  })

  it('passes selected knowledge base ids when continuing a team chat', async () => {
    vi.mocked(teamChatContinue).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        turnId: 'turn-2',
        turnNumber: 2,
        transportMode: 'websocket',
      },
    } as never)

    const aiContextStore = useAiContextStore()
    aiContextStore.selectedKnowledgeBaseIds = ['kb-9']
    const store = useDebateStore()
    store.conversationId = 'conv-1'
    store.selectedModelIds = ['model-a', 'model-b']

    await store.continueTeamChat('follow up')

    expect(teamChatContinue).toHaveBeenCalledWith({
      conversationId: 'conv-1',
      message: 'follow up',
      knowledgeBaseIds: ['kb-9'],
    })
  })

  it('derives process summary and turn start time from restored status payload', async () => {
    vi.mocked(teamChatStatus).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        activeTurnId: 'turn-3',
        models: ['model-a', 'model-b'],
        completedTurns: 2,
        captainSelectionMode: 'auto',
        memoryVersion: 5,
        sharedSummary: 'shared',
        captainHistory: ['model-a'],
        turn: {
          turnId: 'turn-3',
          turnNumber: 3,
          stage: 'DEBATING',
          captainModelId: 'model-a',
          captainSource: 'auto_elected',
          captainExplanation: 'picked by vote',
          userMessage: 'follow up',
          issueCount: 1,
          issues: [],
          memberStatuses: [
            {
              modelId: 'model-a',
              proposalStatus: 'completed',
              debateStatus: 'completed',
              summary: 'summary-a',
              debateArguments: [{ issueId: 'issue-1', argument: 'arg-a', stance: 'support' }],
            },
            {
              modelId: 'model-b',
              proposalStatus: 'completed',
              debateStatus: 'pending',
              summary: 'summary-b',
              debateArguments: [],
            },
          ],
          finalAnswer: null,
          errorMessage: null,
          failedModels: [],
          stageTimestamps: {
            COLLECTING: '2026-04-20T10:00:00Z',
            DEBATING: '2026-04-20T10:00:08Z',
          },
        },
      },
    } as never)

    const store = useDebateStore()
    store.conversationId = 'conv-1'
    store.currentTurnId = 'turn-3'
    store.selectedModelIds = ['model-a', 'model-b']

    await store.fetchStatus({ strict: true })

    expect(store.currentTurnStartedAt).toBe(Date.parse('2026-04-20T10:00:00Z'))
    expect(store.processSummary).toMatchObject({
      title: '团队过程',
      stageLabel: getDebateStageLabel('DEBATING'),
      agentCount: 2,
      respondedAgentCount: 2,
      captainModelId: 'model-a',
      captainSource: 'auto_elected',
      startedAt: Date.parse('2026-04-20T10:00:00Z'),
      hasAnyDetail: true,
    })
    expect(store.agentCards[0]).toMatchObject({
      modelId: 'model-a',
      isLeader: true,
      summary: 'summary-a',
    })
    expect(store.agentCards[1]).toMatchObject({
      modelId: 'model-b',
      summary: 'summary-b',
    })
  })

  it('rejects recoverSession when history loading fails', async () => {
    const historyError = new Error('history failed')
    vi.mocked(teamChatHistory).mockRejectedValue(historyError)

    const store = useDebateStore()
    store.selectedModelIds = ['model-a']
    store.isActive = true

    await expect(store.recoverSession('conv-1')).rejects.toBe(historyError)
    expect(store.isLoading).toBe(false)
    expect(store.isActive).toBe(false)
    expect(store.conversationId).toBeNull()
    expect(store.selectedModelIds).toEqual([])
  })

  it('rejects recoverSession when status loading fails', async () => {
    const statusError = new Error('status failed')
    vi.mocked(teamChatHistory).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        activeTurnId: 'turn-9',
        models: ['model-a', 'model-b'],
        captainSelectionMode: 'auto',
        completedTurns: 1,
        decisionHistory: [],
        sharedSummary: 'summary',
        memoryVersion: 2,
      },
    } as never)
    vi.mocked(teamChatStatus).mockRejectedValue(statusError)

    const store = useDebateStore()

    await expect(store.recoverSession('conv-1')).rejects.toBe(statusError)
    expect(store.isLoading).toBe(false)
    expect(store.isActive).toBe(false)
    expect(store.conversationId).toBeNull()
  })

  it('can restore a previous debate snapshot after a failed recovery clears the store', async () => {
    const historyError = new Error('history failed')
    vi.mocked(teamChatHistory).mockRejectedValue(historyError)

    const store = useDebateStore()
    store.conversationId = 'existing-conv'
    store.selectedModelIds = ['model-a', 'model-b']
    store.captainSelectionMode = 'fixed_first'
    store.sharedSummary = 'existing summary'
    store.decisionHistory = [{
      turnId: 'turn-1',
      turnNumber: 1,
      captainModelId: 'model-a',
      captainSource: 'fixed_first',
      userQuestion: 'old question',
      finalAnswerSummary: 'old answer',
      keyIssues: ['issue-a'],
      timestamp: '2025-01-01T00:00:00Z',
    }]
    store.memoryVersion = 3
    store.completedTurns = 1
    store.captainHistory = ['model-a']
    store.currentTurnId = 'turn-2'
    store.currentTurnNumber = 2
    store.currentStage = 'COLLECTING'
    store.currentCaptainModel = 'model-a'
    store.captainSource = 'fixed_first'
    store.captainExplanation = 'kept captain'
    store.issues = [{ issueId: 'issue-1', title: 'issue-a', resolved: false }]
    store.finalAnswer = 'partial answer'
    store.errorMessage = null
    store.memberStatuses = [
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'pending' },
      { modelId: 'model-b', proposalStatus: 'pending', debateStatus: 'pending' },
    ]
    store.turnSummaries = [{
      turnId: 'turn-1',
      turnNumber: 1,
      captainModelId: 'model-a',
      captainSource: 'fixed_first',
      userQuestion: 'old question',
      finalAnswer: 'old answer',
      issues: [{ issueId: 'issue-1', title: 'issue-a', resolved: true }],
      stage: 'COMPLETED',
      errorMessage: null,
    }]
    store.eventCursor = 9
    store.eventCursorPrimed = true
    store.isActive = true
    store.isLoading = false

    const snapshot = store.snapshotState()

    await expect(store.recoverSession('conv-1')).rejects.toBe(historyError)

    expect(store.conversationId).toBeNull()
    expect(store.selectedModelIds).toEqual([])

    store.restoreState(snapshot)

    expect(store.conversationId).toBe('existing-conv')
    expect(store.selectedModelIds).toEqual(['model-a', 'model-b'])
    expect(store.captainSelectionMode).toBe('fixed_first')
    expect(store.sharedSummary).toBe('existing summary')
    expect(store.decisionHistory).toEqual([{
      turnId: 'turn-1',
      turnNumber: 1,
      captainModelId: 'model-a',
      captainSource: 'fixed_first',
      userQuestion: 'old question',
      finalAnswerSummary: 'old answer',
      keyIssues: ['issue-a'],
      timestamp: '2025-01-01T00:00:00Z',
    }])
    expect(store.currentTurnId).toBe('turn-2')
    expect(store.currentStage).toBe('COLLECTING')
    expect(store.memberStatuses).toEqual([
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'pending' },
      { modelId: 'model-b', proposalStatus: 'pending', debateStatus: 'pending' },
    ])
    expect(store.turnSummaries).toEqual([{
      turnId: 'turn-1',
      turnNumber: 1,
      captainModelId: 'model-a',
      captainSource: 'fixed_first',
      userQuestion: 'old question',
      finalAnswer: 'old answer',
      issues: [{ issueId: 'issue-1', title: 'issue-a', resolved: true }],
      stage: 'COMPLETED',
      errorMessage: null,
    }])
    expect(store.eventCursor).toBe(9)
    expect(store.eventCursorPrimed).toBe(true)
    expect(store.isActive).toBe(true)
  })

  it('marks the current turn as failed when polling cannot recover', () => {
    const store = useDebateStore()
    store.isActive = true
    store.currentStage = 'COLLECTING'
    store.memberStatuses = [
      { modelId: 'model-a', proposalStatus: 'pending', debateStatus: 'pending' },
      { modelId: 'model-b', proposalStatus: 'completed', debateStatus: 'pending' },
    ]

    store.markCurrentTurnFailed('status sync failed')

    expect(store.currentStage).toBe('FAILED')
    expect(store.errorMessage).toBe('status sync failed')
    expect(store.memberStatuses).toEqual([
      { modelId: 'model-a', proposalStatus: 'failed', debateStatus: 'failed' },
      { modelId: 'model-b', proposalStatus: 'completed', debateStatus: 'failed' },
    ])
  })

  it('marks pending members as failed when a turn fails before status polling can continue', () => {
    const store = useDebateStore()
    store.isActive = true
    store.selectedModelIds = ['model-a', 'model-b']
    store.memberStatuses = [
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'pending' },
      { modelId: 'model-b', proposalStatus: 'pending', debateStatus: 'pending' },
    ]
    store.currentTurnId = 'turn-1'

    store.handleWsEvent('team.turn_failed', { turnId: 'turn-1', error: 'boom' })

    expect(store.currentStage).toBe('FAILED')
    expect(store.errorMessage).toBe('boom')
    expect(store.memberStatuses).toEqual([
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'failed' },
      { modelId: 'model-b', proposalStatus: 'failed', debateStatus: 'failed' },
    ])
  })

  it('ignores late websocket events after the local debate state has been reset', () => {
    const store = useDebateStore()
    store.conversationId = 'conv-1'
    store.currentTurnId = 'turn-1'
    store.currentStage = 'COLLECTING'
    store.isActive = true

    store.reset()
    store.handleWsEvent('team.stage_changed', { conversationId: 'conv-1', turnId: 'turn-1', stage: 'DEBATING' })

    expect(store.currentStage).toBe('IDLE')
    expect(store.currentTurnId).toBeNull()
    expect(store.conversationId).toBeNull()
  })

  it('appends streamed final answer deltas and lets turn_completed finalize once', () => {
    const store = useDebateStore()
    store.isActive = true
    store.conversationId = 'conv-1'
    store.currentTurnId = 'turn-1'
    store.currentStage = 'SYNTHESIZING'
    store.memberStatuses = [
      { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'completed' },
      { modelId: 'model-b', proposalStatus: 'completed', debateStatus: 'completed' },
    ]

    store.handleWsEvent('team.final_answer_delta', {
      conversationId: 'conv-1',
      turnId: 'turn-1',
      delta: 'hello ',
    })
    store.handleWsEvent('team.final_answer_delta', {
      conversationId: 'conv-1',
      turnId: 'turn-1',
      delta: 'world',
    })
    store.handleWsEvent('team.final_answer_done', {
      conversationId: 'conv-1',
      turnId: 'turn-1',
    })
    store.handleWsEvent('team.turn_completed', {
      conversationId: 'conv-1',
      turnId: 'turn-1',
      finalAnswer: null,
      captainModelId: 'model-a',
      captainSource: 'auto_elected',
    })
    store.handleWsEvent('team.turn_completed', {
      conversationId: 'conv-1',
      turnId: 'turn-1',
      finalAnswer: null,
      captainModelId: 'model-a',
      captainSource: 'auto_elected',
    })

    expect(store.finalAnswer).toBe('hello world')
    expect(store.currentStage).toBe('COMPLETED')
    expect(store.completedTurns).toBe(1)
  })

  it('replays cursor events for the active turn and advances the stored cursor', async () => {
    vi.mocked(teamChatEvents).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        turnId: 'turn-1',
        cursor: 0,
        nextCursor: 2,
        completed: false,
        stage: 'SYNTHESIZING',
        events: [
          {
            cursor: 0,
            event: 'team.final_answer_delta',
            data: {
              conversationId: 'conv-1',
              turnId: 'turn-1',
              delta: 'hello',
            },
            timestamp: null,
          },
          {
            cursor: 1,
            event: 'team.final_answer_delta',
            data: {
              conversationId: 'conv-1',
              turnId: 'turn-1',
              delta: ' world',
            },
            timestamp: null,
          },
        ],
      },
    } as never)

    const store = useDebateStore()
    store.isActive = true
    store.conversationId = 'conv-1'
    store.currentTurnId = 'turn-1'
    store.currentStage = 'SYNTHESIZING'

    const ok = await store.fetchEvents()

    expect(ok).toBe(true)
    expect(teamChatEvents).toHaveBeenCalledWith('conv-1', 'turn-1', 0)
    expect(store.finalAnswer).toBe('hello world')
    expect(store.eventCursor).toBe(2)
    expect(store.eventCursorPrimed).toBe(true)
  })

  it('keeps the local partial final answer when same-turn status has not flushed yet', async () => {
    vi.mocked(teamChatStatus).mockResolvedValue({
      data: {
        conversationId: 'conv-1',
        activeTurnId: 'turn-9',
        models: ['model-a', 'model-b'],
        completedTurns: 1,
        captainSelectionMode: 'auto',
        memoryVersion: 2,
        sharedSummary: 'summary',
        captainHistory: ['model-a'],
        turn: {
          turnId: 'turn-9',
          turnNumber: 2,
          stage: 'SYNTHESIZING',
          captainModelId: 'model-a',
          captainSource: 'auto_elected',
          userMessage: 'follow up',
          issueCount: 0,
          issues: [],
          memberStatuses: [
            { modelId: 'model-a', proposalStatus: 'completed', debateStatus: 'completed' },
            { modelId: 'model-b', proposalStatus: 'completed', debateStatus: 'completed' },
          ],
          finalAnswer: null,
          errorMessage: null,
          failedModels: [],
          stageTimestamps: {},
        },
      },
    } as never)

    const store = useDebateStore()
    store.conversationId = 'conv-1'
    store.currentTurnId = 'turn-9'
    store.currentStage = 'SYNTHESIZING'
    store.finalAnswer = 'partial answer'

    await store.fetchStatus({ strict: true })

    expect(store.finalAnswer).toBe('partial answer')
  })

  it('stores expanded agent ids inside snapshots and restores them intact', () => {
    const store = useDebateStore()
    store.agentCards = [
      {
        modelId: 'model-a',
        displayName: 'Model A',
        avatarColor: '#111111',
        proposalStatus: 'completed',
        debateStatus: 'pending',
        isLeader: false,
        summary: 'summary-a',
        debateArguments: [],
        updatedAt: Date.now(),
      },
      {
        modelId: 'model-b',
        displayName: 'Model B',
        avatarColor: '#222222',
        proposalStatus: 'completed',
        debateStatus: 'completed',
        isLeader: true,
        summary: null,
        debateArguments: [{ issueId: 'issue-1', argument: 'arg-b', stance: 'support' }],
        updatedAt: Date.now(),
      },
    ]
    store.currentTurnStartedAt = Date.parse('2026-04-20T10:00:00Z')

    store.toggleAgentExpanded('model-a')
    store.toggleAgentExpanded('model-b')

    const snapshot = store.snapshotState()
    store.resetTurn()
    store.restoreState(snapshot)

    expect(store.isAgentExpanded('model-a')).toBe(true)
    expect(store.isAgentExpanded('model-b')).toBe(true)
    expect(store.currentTurnStartedAt).toBe(Date.parse('2026-04-20T10:00:00Z'))
    expect(store.processSummary.agentCount).toBe(2)
  })
})
