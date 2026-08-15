import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  watchCallbacks: [] as Array<() => void>,
  ws: {
    connected: { value: false },
    connect: vi.fn().mockResolvedValue(undefined),
    disconnect: vi.fn(),
    send: vi.fn(),
    on: vi.fn(),
    off: vi.fn(),
  },
  debateStore: {
    isActive: true,
    currentTurnId: 'turn-1',
    currentStage: 'SYNTHESIZING',
    eventCursorPrimed: false,
    get isInProgress() {
      return this.currentStage !== 'IDLE' && this.currentStage !== 'COMPLETED' && this.currentStage !== 'FAILED'
    },
    fetchStatus: vi.fn().mockResolvedValue(true),
    fetchEvents: vi.fn().mockResolvedValue(true),
    primeEventCursor: vi.fn().mockImplementation(async () => {
      mocks.debateStore.eventCursorPrimed = true
      return true
    }),
    handleWsEvent: vi.fn(),
    markCurrentTurnFailed: vi.fn(),
    startTeamChat: vi.fn().mockResolvedValue('conv-1'),
    continueTeamChat: vi.fn().mockResolvedValue('turn-2'),
    recoverSession: vi.fn().mockResolvedValue(undefined),
    reset: vi.fn(),
  },
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (callback: () => void) => callback(),
    onUnmounted: vi.fn(),
    watch: (_source: unknown, callback: () => void, options?: { immediate?: boolean }) => {
      mocks.watchCallbacks.push(callback)
      if (options?.immediate) {
        callback()
      }
      return vi.fn()
    },
  }
})

vi.mock('@/composables/useWebSocket', () => ({
  useWebSocket: () => mocks.ws,
}))

vi.mock('@/stores/debate', () => ({
  useDebateStore: () => mocks.debateStore,
}))

import { useTeamDebate } from './useTeamDebate'

describe('useTeamDebate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    mocks.watchCallbacks.length = 0
    mocks.ws.connected.value = false
    mocks.debateStore.isActive = true
    mocks.debateStore.currentTurnId = 'turn-1'
    mocks.debateStore.currentStage = 'SYNTHESIZING'
    mocks.debateStore.eventCursorPrimed = false
    mocks.debateStore.fetchStatus.mockResolvedValue(true)
    mocks.debateStore.fetchEvents.mockResolvedValue(true)
    mocks.debateStore.primeEventCursor.mockImplementation(async () => {
      mocks.debateStore.eventCursorPrimed = true
      return true
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('subscribes to final answer delta and done events', () => {
    useTeamDebate()

    expect(mocks.ws.on).toHaveBeenCalledWith('team.final_answer_delta', expect.any(Function))
    expect(mocks.ws.on).toHaveBeenCalledWith('team.final_answer_done', expect.any(Function))
  })

  it('polls status and cursor events when websocket is unavailable', async () => {
    useTeamDebate()

    await vi.advanceTimersByTimeAsync(1300)

    expect(mocks.debateStore.fetchStatus).toHaveBeenCalled()
    expect(mocks.debateStore.primeEventCursor).toHaveBeenCalled()
    expect(mocks.debateStore.fetchEvents).toHaveBeenCalled()
  })
})
