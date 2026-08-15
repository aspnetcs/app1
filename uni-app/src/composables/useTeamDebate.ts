/**
 * useTeamDebate - composable that bridges WebSocket events to the debate store,
 * and provides high-level actions for the chat page to consume.
 */
import { onMounted, onUnmounted, watch } from 'vue'
import { useWebSocket } from '@/composables/useWebSocket'
import { useDebateStore } from '@/stores/debate'

const TEAM_EVENTS = [
  'team.turn_started',
  'team.stage_changed',
  'team.member_proposal',
  'team.captain_elected',
  'team.issues_extracted',
  'team.debate_entry',
  'team.final_answer_delta',
  'team.final_answer_done',
  'team.turn_completed',
  'team.turn_failed',
] as const

const STATUS_POLL_INTERVAL_MS = 1200
const EVENT_POLL_INTERVAL_MS = 800
const MAX_POLL_FAILURES = 3

export function useTeamDebate() {
  const ws = useWebSocket()
  const debateStore = useDebateStore()
  let statusPollTimer: ReturnType<typeof setInterval> | null = null
  let eventPollTimer: ReturnType<typeof setInterval> | null = null
  let statusPollFailures = 0
  let eventPollFailures = 0
  let eventCursorSyncPending = false

  function resetStatusPollFailures() {
    statusPollFailures = 0
  }

  function resetEventPollFailures() {
    eventPollFailures = 0
  }

  function handleStatusPollFailure() {
    statusPollFailures += 1
    if (statusPollFailures < MAX_POLL_FAILURES) {
      return
    }
    debateStore.markCurrentTurnFailed('状态同步失败，请稍后重试')
    stopStatusPolling()
  }

  function handleEventPollFailure() {
    eventPollFailures += 1
    if (eventPollFailures < MAX_POLL_FAILURES) {
      return
    }
    stopEventPolling()
  }

  function stopStatusPolling() {
    if (statusPollTimer) {
      clearInterval(statusPollTimer)
      statusPollTimer = null
    }
    resetStatusPollFailures()
  }

  function stopEventPolling() {
    if (eventPollTimer) {
      clearInterval(eventPollTimer)
      eventPollTimer = null
    }
    eventCursorSyncPending = false
    resetEventPollFailures()
  }

  function shouldPollStatus() {
    return debateStore.isActive && debateStore.currentTurnId && debateStore.isInProgress
  }

  function shouldPollEvents() {
    return shouldPollStatus() && !ws.connected.value
  }

  function startStatusPolling() {
    if (statusPollTimer) return
    statusPollTimer = setInterval(() => {
      if (!shouldPollStatus()) {
        stopStatusPolling()
        return
      }
      void debateStore.fetchStatus().then((ok) => {
        if (ok === false) {
          handleStatusPollFailure()
          return
        }
        resetStatusPollFailures()
      })
    }, STATUS_POLL_INTERVAL_MS)
  }

  async function syncEventCursor() {
    if (!debateStore.currentTurnId || debateStore.eventCursorPrimed || eventCursorSyncPending) {
      return true
    }
    eventCursorSyncPending = true
    try {
      const statusOk = await debateStore.fetchStatus()
      if (statusOk === false) {
        handleStatusPollFailure()
        return false
      }
      const primed = await debateStore.primeEventCursor()
      if (primed === false) {
        handleEventPollFailure()
        return false
      }
      resetStatusPollFailures()
      resetEventPollFailures()
      return true
    } finally {
      eventCursorSyncPending = false
    }
  }

  function startEventPolling() {
    if (eventPollTimer) return
    void syncEventCursor()
    eventPollTimer = setInterval(() => {
      if (!shouldPollEvents()) {
        stopEventPolling()
        return
      }
      void (async () => {
        const ready = await syncEventCursor()
        if (!ready) {
          return
        }
        const ok = await debateStore.fetchEvents()
        if (ok === false) {
          handleEventPollFailure()
          return
        }
        resetEventPollFailures()
      })()
    }, EVENT_POLL_INTERVAL_MS)
  }

  function syncStatusPolling() {
    if (shouldPollStatus()) {
      startStatusPolling()
      return
    }
    stopStatusPolling()
  }

  function syncEventPolling() {
    if (shouldPollEvents()) {
      startEventPolling()
      return
    }
    stopEventPolling()
  }

  function handleTeamEvent(event: string) {
    return (data: unknown) => {
      const payload = data && typeof data === 'object'
        ? (data as Record<string, unknown>)
        : {}
      debateStore.handleWsEvent(event, payload)
      resetStatusPollFailures()
      resetEventPollFailures()
      if (event === 'team.turn_failed' && debateStore.currentTurnId) {
        void debateStore.fetchStatus()
      }
    }
  }

  onMounted(() => {
    void ws.connect()
    for (const event of TEAM_EVENTS) {
      ws.on(event, handleTeamEvent(event))
    }
    syncStatusPolling()
    syncEventPolling()
  })

  onUnmounted(() => {
    stopStatusPolling()
    stopEventPolling()
    for (const event of TEAM_EVENTS) {
      ws.off(event)
    }
  })

  watch(
    () => [debateStore.isActive, debateStore.currentTurnId, debateStore.currentStage, ws.connected.value],
    () => {
      syncStatusPolling()
      syncEventPolling()
    },
    { immediate: true }
  )

  /**
   * Start a new team debate session.
   * Call this instead of the normal SSE chat flow when mode=team.
   */
  async function startDebate(message: string, modelIds: string[], captainMode: 'auto' | 'fixed_first' = 'auto') {
    const conversationId = await debateStore.startTeamChat(message, modelIds, captainMode)
    void debateStore.fetchStatus()
    syncStatusPolling()
    syncEventPolling()
    return conversationId
  }

  /**
   * Send a follow-up question in the current team session.
   */
  async function continueDebate(message: string) {
    const turnId = await debateStore.continueTeamChat(message)
    void debateStore.fetchStatus()
    syncStatusPolling()
    syncEventPolling()
    return turnId
  }

  /**
   * Recover an existing team session after page refresh.
   */
  async function recoverDebate(conversationId: string) {
    const result = await debateStore.recoverSession(conversationId)
    syncStatusPolling()
    syncEventPolling()
    return result
  }

  /**
   * End the current team session and reset state.
   */
  function endDebate() {
    stopStatusPolling()
    stopEventPolling()
    debateStore.reset()
  }

  return {
    debateStore,
    startDebate,
    continueDebate,
    recoverDebate,
    endDebate,
  }
}
