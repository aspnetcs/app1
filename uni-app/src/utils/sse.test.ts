import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchSse } from '@/utils/sse'

function encodeChunk(text: string): ArrayBuffer {
  return new TextEncoder().encode(text).buffer
}

describe('fetchSse', () => {
  const originalFetch = (globalThis as Record<string, unknown>).fetch

  afterEach(() => {
    ;(globalThis as Record<string, unknown>).fetch = originalFetch
    vi.restoreAllMocks()
  })

  it('parses event-stream chunks in H5 fetch mode', async () => {
    const events: Array<{ event: string; data: string }> = []
    const done = vi.fn()

    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: () => '',
    }

    const chunks = [
      'event: message\ndata: hello\n\n',
      'data: world\n\n',
      'event: done\ndata: [DONE]\n\n',
    ]

    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        for (const chunk of chunks) {
          controller.enqueue(new TextEncoder().encode(chunk))
        }
        controller.close()
      },
    })

    const response = new Response(stream, { status: 200 })
    ;(globalThis as Record<string, unknown>).fetch = vi.fn().mockResolvedValue(response)

    await new Promise<void>((resolve, reject) => {
      fetchSse({
        url: 'https://example.test/sse',
        data: { q: 'x' },
        onEvent: (event, data) => events.push({ event, data }),
        onDone: () => {
          done()
          resolve()
        },
        onError: (err) => reject(err),
      })
    })

    expect(events).toEqual([
      { event: 'message', data: 'hello' },
      { event: 'message', data: 'world' },
      { event: 'done', data: '[DONE]' },
    ])
    expect(done).toHaveBeenCalledTimes(1)
  })

  it('retries once when the first H5 fetch attempt fails before receiving payload', async () => {
    const events: Array<{ event: string; data: string }> = []

    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: () => '',
    }

    const response = new Response(
      new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('event: done\ndata: [DONE]\n\n'))
          controller.close()
        },
      }),
      { status: 200 },
    )

    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('network down'))
      .mockResolvedValueOnce(response)
    ;(globalThis as Record<string, unknown>).fetch = fetchMock

    await new Promise<void>((resolve, reject) => {
      fetchSse({
        url: 'https://example.test/retry',
        retry: {
          attempts: 1,
          baseDelayMs: 0,
          maxDelayMs: 0,
          timeoutMs: 1_000,
        },
        onEvent: (event, data) => events.push({ event, data }),
        onDone: resolve,
        onError: (err) => reject(err),
      })
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(events).toEqual([{ event: 'done', data: '[DONE]' }])
  })

  it('retries with Last-Event-ID after receiving a resumable chunk', async () => {
    const events: Array<{ event: string; data: string }> = []

    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: () => '',
    }

    let firstPullCount = 0
    const firstResponse = new Response(
      new ReadableStream<Uint8Array>({
        pull(controller) {
          if (firstPullCount === 0) {
            firstPullCount += 1
            controller.enqueue(new TextEncoder().encode('id: 7\nevent: message\ndata: hello\n\n'))
            return
          }
          controller.error(new Error('stream lost'))
        },
      }),
      { status: 200 },
    )

    const secondResponse = new Response(
      new ReadableStream<Uint8Array>({
        start(controller) {
          controller.enqueue(new TextEncoder().encode('event: done\ndata: [DONE]\n\n'))
          controller.close()
        },
      }),
      { status: 200 },
    )

    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(firstResponse)
      .mockImplementationOnce((_input: unknown, init?: RequestInit) => {
        expect(init?.headers).toMatchObject({
          'Last-Event-ID': '7',
          'X-Last-Event-ID': '7',
        })
        return Promise.resolve(secondResponse)
      })

    ;(globalThis as Record<string, unknown>).fetch = fetchMock

    await new Promise<void>((resolve, reject) => {
      fetchSse({
        url: 'https://example.test/resume',
        retry: {
          attempts: 1,
          baseDelayMs: 0,
          maxDelayMs: 0,
          timeoutMs: 1_000,
        },
        onEvent: (event, data) => events.push({ event, data }),
        onDone: resolve,
        onError: (err) => reject(err),
      })
    })

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(events).toEqual([
      { event: 'message', data: 'hello' },
      { event: 'done', data: '[DONE]' },
    ])
  })

  it('parses chunked responses via uni.request when fetch is unavailable', async () => {
    const events: Array<{ event: string; data: string }> = []

    ;(globalThis as Record<string, unknown>).fetch = undefined

    let onChunkReceived: ((res: { data: ArrayBuffer }) => void) | null = null
    let requestOptions: Record<string, unknown> | null = null
    const abort = vi.fn()
    let cancel: (() => void) | null = null

    const requestTask = {
      onChunkReceived: (cb: (res: { data: ArrayBuffer }) => void) => {
        onChunkReceived = cb
      },
      abort,
    }

    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: () => '',
      request: (opts: Record<string, unknown>) => {
        requestOptions = opts
        return requestTask
      },
    }

    const donePromise = new Promise<void>((resolve, reject) => {
      cancel = fetchSse({
        url: 'https://example.test/sse',
        data: { q: 'x' },
        onEvent: (event, data) => events.push({ event, data }),
        onDone: resolve,
        onError: (err) => reject(err),
      })
    })

    expect(typeof onChunkReceived).toBe('function')
    expect(requestOptions).not.toBeNull()
    expect(typeof cancel).toBe('function')

    const chunkFn = onChunkReceived as unknown as (res: { data: ArrayBuffer }) => void
    const opts = requestOptions as unknown as { success?: () => void }
    const cancelFn = cancel as unknown as () => void

    chunkFn({ data: encodeChunk('event: message\ndata: hello\n\n') })
    chunkFn({ data: encodeChunk('data: world\n\n') })
    chunkFn({ data: encodeChunk('event: done\ndata: [DONE]\n\n') })

    opts.success?.()

    await donePromise

    cancelFn()
    expect(abort).toHaveBeenCalledTimes(1)

    expect(events).toEqual([
      { event: 'message', data: 'hello' },
      { event: 'message', data: 'world' },
      { event: 'done', data: '[DONE]' },
    ])
  })
})
