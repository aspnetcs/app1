import { getStorage } from '@/utils/storage'

export interface SseRetryOptions {
  attempts?: number
  baseDelayMs?: number
  maxDelayMs?: number
  timeoutMs?: number
}

export interface SseOptions {
  url: string
  data?: unknown
  headers?: Record<string, string>
  onMessage?: (data: string) => void
  onEvent?: (event: string, data: string) => void
  onError?: (err: unknown) => void
  onDone?: () => void
  retry?: SseRetryOptions
}

type NormalizedRetryOptions = Required<SseRetryOptions>

type ParserState = {
  buffer: string
  lastEventId: string
  hasReceivedPayload: boolean
  done: boolean
}

const DEFAULT_RETRY_OPTIONS: NormalizedRetryOptions = {
  attempts: 2,
  baseDelayMs: 600,
  maxDelayMs: 1_800,
  timeoutMs: 45_000,
}

const RETRYABLE_HTTP_STATUS = new Set([408, 425, 429, 500, 502, 503, 504])

class SseHttpError extends Error {
  status: number

  constructor(status: number) {
    super(`HTTP error! status: ${status}`)
    this.name = 'SseHttpError'
    this.status = status
  }
}

function normalizeRetryOptions(retry?: SseRetryOptions): NormalizedRetryOptions {
  return {
    attempts: Math.max(0, Math.floor(retry?.attempts ?? DEFAULT_RETRY_OPTIONS.attempts)),
    baseDelayMs: Math.max(0, Math.floor(retry?.baseDelayMs ?? DEFAULT_RETRY_OPTIONS.baseDelayMs)),
    maxDelayMs: Math.max(0, Math.floor(retry?.maxDelayMs ?? DEFAULT_RETRY_OPTIONS.maxDelayMs)),
    timeoutMs: Math.max(0, Math.floor(retry?.timeoutMs ?? DEFAULT_RETRY_OPTIONS.timeoutMs)),
  }
}

function computeRetryDelay(attempt: number, retryOptions: NormalizedRetryOptions): number {
  const multiplier = Math.max(1, 2 ** attempt)
  return Math.min(retryOptions.maxDelayMs, retryOptions.baseDelayMs * multiplier)
}

function toRequestData(data: unknown): UniApp.RequestOptions['data'] {
  if (data === undefined || data === null) return undefined
  if (typeof data === 'string' || data instanceof ArrayBuffer) return data
  if (typeof data === 'object') return data as Record<string, unknown>
  return String(data)
}

function createSseParser(
  onEvent: (event: string, data: string) => void,
  onDone: (() => void) | undefined,
  state: ParserState,
) {
  let currentEvent = 'message'
  let currentEventId = ''
  let dataLines: string[] = []

  const resetFrame = () => {
    currentEvent = 'message'
    currentEventId = ''
    dataLines = []
  }

  const finish = () => {
    if (state.done) return
    state.done = true
    onDone?.()
  }

  const flush = () => {
    if (state.done) return
    const payload = dataLines.join('\n')
    if (!payload) {
      resetFrame()
      return
    }

    state.hasReceivedPayload = true
    if (currentEventId) {
      state.lastEventId = currentEventId
    }
    onEvent(currentEvent || 'message', payload)
    if (payload === '[DONE]' || currentEvent === 'done' || currentEvent === 'chat.done') {
      finish()
    }
    resetFrame()
  }

  const consumeLine = (line: string) => {
    if (state.done) return
    const normalized = line.replace(/\r$/, '')
    if (!normalized) {
      flush()
      return
    }
    if (normalized.startsWith(':')) return
    if (normalized.startsWith('event:')) {
      currentEvent = normalized.slice(6).trim() || 'message'
      return
    }
    if (normalized.startsWith('id:')) {
      currentEventId = normalized.slice(3).trim()
      return
    }
    if (normalized.startsWith('data:')) {
      dataLines.push(normalized.slice(5).trimStart())
    }
  }

  return {
    consumeChunk(text: string) {
      state.buffer += text
      const lines = state.buffer.split('\n')
      state.buffer = lines.pop() || ''
      for (const line of lines) {
        consumeLine(line)
      }
    },
    finalize() {
      if (state.done) return
      if (state.buffer) {
        consumeLine(state.buffer)
        state.buffer = ''
      }
      flush()
      if (!state.done) {
        finish()
      }
    },
  }
}

function buildHeaders(options: SseOptions, lastEventId: string): Record<string, string> {
  const token = getStorage<string>('token') || getStorage<string>('access_token') || ''
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
    ...options.headers,
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  if (lastEventId) {
    headers['Last-Event-ID'] = lastEventId
    headers['X-Last-Event-ID'] = lastEventId
  }

  return headers
}

function shouldRetryStream(
  error: unknown,
  attempt: number,
  state: ParserState,
  retryOptions: NormalizedRetryOptions,
): boolean {
  if (attempt >= retryOptions.attempts || state.done) {
    return false
  }

  if (error instanceof SseHttpError) {
    return RETRYABLE_HTTP_STATUS.has(error.status)
  }

  if (!state.hasReceivedPayload) {
    return true
  }

  return Boolean(state.lastEventId)
}

function decodeArrayBufferUtf8(data: ArrayBuffer) {
  const bytes = new Uint8Array(data)
  try {
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    const chars = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('')
    return decodeURIComponent(escape(chars))
  }
}

export const fetchSse = (options: SseOptions) => {
  const retryOptions = normalizeRetryOptions(options.retry)
  const parserState: ParserState = {
    buffer: '',
    lastEventId: '',
    hasReceivedPayload: false,
    done: false,
  }
  const parser = createSseParser(
    (event, data) => {
      options.onEvent?.(event, data)
      options.onMessage?.(data)
    },
    options.onDone,
    parserState,
  )

  let aborted = false
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let timeoutTimer: ReturnType<typeof setTimeout> | null = null
  let activeAbort: (() => void) | null = null

  const clearRetryTimer = () => {
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  const clearTimeoutTimer = () => {
    if (timeoutTimer) {
      clearTimeout(timeoutTimer)
      timeoutTimer = null
    }
  }

  const clearTimers = () => {
    clearRetryTimer()
    clearTimeoutTimer()
  }

  const scheduleTimeout = (onTimeout: () => void) => {
    clearTimeoutTimer()
    if (retryOptions.timeoutMs <= 0) return
    timeoutTimer = setTimeout(onTimeout, retryOptions.timeoutMs)
  }

  const finalizeFailure = (error: unknown) => {
    if (aborted || parserState.done) return
    clearTimers()
    options.onError?.(error)
  }

  const scheduleRetry = (attempt: number, error: unknown, runAttempt: (nextAttempt: number) => void) => {
    if (!shouldRetryStream(error, attempt, parserState, retryOptions)) {
      finalizeFailure(error)
      return
    }

    const delay = computeRetryDelay(attempt, retryOptions)
    retryTimer = setTimeout(() => {
      retryTimer = null
      runAttempt(attempt + 1)
    }, delay)
  }

  // #ifdef H5
  if (typeof fetch === 'function') {
    const runFetchAttempt = (attempt: number) => {
      if (aborted) return

      const controller = new AbortController()
      let timedOut = false
      activeAbort = () => controller.abort()

      const expireRequest = () => {
        timedOut = true
        controller.abort()
      }

      scheduleTimeout(expireRequest)

      fetch(options.url, {
        method: 'POST',
        headers: buildHeaders(options, parserState.lastEventId),
        body: JSON.stringify(options.data ?? null),
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok) {
            throw new SseHttpError(response.status)
          }
          const reader = response.body?.getReader()
          if (!reader) {
            throw new Error('No reader attached to response')
          }
          const decoder = new TextDecoder('utf-8')

          while (true) {
            const { done, value } = await reader.read()
            if (done) break
            scheduleTimeout(expireRequest)
            if (value) {
              parser.consumeChunk(decoder.decode(value, { stream: true }))
            }
            if (parserState.done) {
              break
            }
          }

          clearTimeoutTimer()
          parser.finalize()
        })
        .catch((error) => {
          if (aborted || parserState.done) return
          clearTimeoutTimer()
          const normalizedError = timedOut ? new Error('SSE request timed out') : error
          scheduleRetry(attempt, normalizedError, runFetchAttempt)
        })
    }

    runFetchAttempt(0)

    return () => {
      aborted = true
      clearTimers()
      activeAbort?.()
    }
  }
  // #endif

  const runRequestAttempt = (attempt: number) => {
    if (aborted) return

    let chunkReceived = false
    let timedOut = false

    const requestTask = uni.request({
      url: options.url,
      method: 'POST',
      data: toRequestData(options.data),
      header: buildHeaders(options, parserState.lastEventId),
      enableChunked: true,
      dataType: 'text',
      responseType: 'text',
      success: (res) => {
        if (aborted || parserState.done) return
        clearTimeoutTimer()

        if (typeof res?.statusCode === 'number' && (res.statusCode < 200 || res.statusCode >= 300)) {
          scheduleRetry(attempt, new SseHttpError(res.statusCode), runRequestAttempt)
          return
        }

        if (!chunkReceived && res?.data != null) {
          const text =
            typeof res.data === 'string'
              ? res.data
              : res.data instanceof ArrayBuffer
                ? decodeArrayBufferUtf8(res.data)
                : String(res.data)
          if (text) {
            parser.consumeChunk(text)
          }
        }

        parser.finalize()
      },
      fail: (error) => {
        if (aborted || parserState.done) return
        clearTimeoutTimer()
        const normalizedError = timedOut ? new Error('SSE request timed out') : error
        scheduleRetry(attempt, normalizedError, runRequestAttempt)
      },
    }) as UniApp.RequestTask

    activeAbort = () => requestTask.abort()

    const expireRequest = () => {
      timedOut = true
      requestTask.abort()
    }

    scheduleTimeout(expireRequest)

    // @ts-ignore uni-app exposes onChunkReceived only when chunked responses are supported
    if (typeof requestTask.onChunkReceived === 'function') {
      // @ts-ignore
      requestTask.onChunkReceived((res: { data: ArrayBuffer }) => {
        if (aborted || parserState.done) return
        chunkReceived = true
        scheduleTimeout(expireRequest)
        parser.consumeChunk(decodeArrayBufferUtf8(res.data))
      })
    }
  }

  runRequestAttempt(0)

  return () => {
    aborted = true
    clearTimers()
    activeAbort?.()
  }
}
