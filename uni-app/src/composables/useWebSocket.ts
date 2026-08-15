import { ref, onUnmounted } from 'vue'
import { config, APP_CONFIG } from '@/config'
import { getWsTicket } from '@/api/auth'
import { useAppStore } from '@/stores/app'
import { logger } from '@/utils/logger'
import { getStorage } from '@/utils/storage'

type WsEventHandler = (data: unknown) => void
type WsPayload = Record<string, unknown>

const connected = ref(false)
let socketTask: UniApp.SocketTask | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectCount = 0
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let closed = true

const handlers: Record<string, Set<WsEventHandler>> = {}
const STORAGE_MISS = Symbol('storage_miss')

export function buildChatAbortFrame(requestId: string, reason = 'user_cancel') {
  return {
    type: 'chat.abort',
    data: {
      requestId,
      reason,
    },
  }
}

export function createAbortDeduper(ttlMs: number) {
  const lastSentAt = new Map<string, number>()
  const normalizedTtl = Math.max(0, Number(ttlMs) || 0)

  return {
    shouldSend(requestId: string, now = Date.now()) {
      const id = (requestId || '').trim()
      if (!id) return false
      const ts = Number(now) || Date.now()
      const prev = lastSentAt.get(id)
      if (typeof prev === 'number' && ts - prev < normalizedTtl) {
        return false
      }
      lastSentAt.set(id, ts)
      return true
    },
    clear(requestId?: string) {
      if (!requestId) {
        lastSentAt.clear()
        return
      }
      lastSentAt.delete(requestId.trim())
    },
  }
}

const abortDeduper = createAbortDeduper(2000)

function emit(event: string, data: unknown) {
  handlers[event]?.forEach(handler => {
    try {
      handler(data)
    } catch (e) {
      logger.error(`[ws] handler error for event ${event}:`, e)
    }
  })
}

function hasToken() {
  const storedToken = getStorage<unknown | typeof STORAGE_MISS>('token', STORAGE_MISS)
  if (storedToken !== STORAGE_MISS) {
    return typeof storedToken === 'string' && storedToken.trim().length > 0
  }

  // Defensive fallback for legacy deployments that wrote raw strings to storage.
  try {
    const raw = uni.getStorageSync('token')
    return typeof raw === 'string' && raw.trim().length > 0
  } catch {
    return false
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (socketTask && connected.value) {
      socketTask.send({
        data: JSON.stringify({ type: 'ping' }),
        fail: () => logger.warn('[ws] heartbeat send failed'),
      })
    }
  }, APP_CONFIG.wsHeartbeatInterval)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

async function connect() {
  if (!closed) return
  if (!hasToken()) {
    connected.value = false
    return
  }
  closed = false
  await _doConnect()
}

async function _doConnect() {
  if (closed || !hasToken()) return
  if (socketTask) return
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }

  try {
    const res = await getWsTicket()
    const ticket = res.data.ticket
    const wsUrl = config.wsBaseUrl + '?ticket=' + encodeURIComponent(ticket)

    socketTask = uni.connectSocket({
      url: wsUrl,
      complete: () => {},
    })

    socketTask.onOpen(() => {
      logger.log('[ws] connected')
      connected.value = true
      reconnectCount = 0
      if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
      }
      const appStore = useAppStore()
      appStore.wsConnected = true
      startHeartbeat()
    })

    socketTask.onMessage((res) => {
      try {
        const messageText = typeof res.data === 'string' ? res.data : String(res.data ?? '')
        const msg = JSON.parse(messageText) as WsPayload
        const type = typeof msg.type === 'string' ? msg.type : 'message'
        const data = msg.data ?? msg.payload ?? msg

        if (type === 'pong') return
        if (type === 'ws.ready') {
          emit('ready', data)
          return
        }

        emit(type, data)
      } catch (e) {
        logger.warn('[ws] parse message failed:', e)
      }
    })

    socketTask.onClose(() => {
      logger.log('[ws] closed')
      connected.value = false
      socketTask = null
      const appStore = useAppStore()
      appStore.wsConnected = false
      stopHeartbeat()
      tryReconnect()
    })

    socketTask.onError((err) => {
      logger.error('[ws] error:', err)
      connected.value = false
      socketTask = null
      const appStore = useAppStore()
      appStore.wsConnected = false
      stopHeartbeat()
      tryReconnect()
    })
  } catch (e) {
    logger.error('[ws] connect failed:', e)
    tryReconnect()
  }
}

function tryReconnect() {
  if (closed || !hasToken()) return
  if (reconnectTimer) return
  if (reconnectCount >= APP_CONFIG.wsMaxReconnect) {
    logger.warn('[ws] max reconnect reached')
    return
  }

  const delay = Math.min(1000 * Math.pow(2, reconnectCount), 30000)
  reconnectCount++
  logger.log(`[ws] reconnecting in ${delay}ms (attempt ${reconnectCount})`)

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    _doConnect()
  }, delay)
}

function disconnect() {
  closed = true
  stopHeartbeat()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (socketTask) {
    socketTask.close({})
    socketTask = null
  }
  connected.value = false
}

function send(data: unknown) {
  if (socketTask && connected.value) {
    socketTask.send({
      data: typeof data === 'string' ? data : JSON.stringify(data),
    })
  }
}

export function useWebSocket() {
  const localHandlers: Array<{ event: string; handler: WsEventHandler }> = []

  function on(event: string, handler: WsEventHandler) {
    if (!handlers[event]) handlers[event] = new Set()
    handlers[event].add(handler)
    localHandlers.push({ event, handler })
  }

  function off(event: string, handler?: WsEventHandler) {
    if (!handler) {
      if (handlers[event]) {
        localHandlers.filter(item => item.event === event).forEach(item => handlers[event].delete(item.handler))
      }
    } else if (handlers[event]) {
      handlers[event].delete(handler)
    }
  }

  onUnmounted(() => {
    localHandlers.forEach(({ event, handler }) => {
      handlers[event]?.delete(handler)
    })
  })

  return {
    connected,
    connect,
    disconnect,
    send,
    sendChatAbort: (requestId: string, reason = 'user_cancel') => {
      const normalized = (requestId || '').trim()
      if (!normalized) return false
      if (!abortDeduper.shouldSend(normalized)) return false
      try {
        send(buildChatAbortFrame(normalized, reason))
        return true
      } catch {
        return false
      }
    },
    on,
    off,
  }
}
