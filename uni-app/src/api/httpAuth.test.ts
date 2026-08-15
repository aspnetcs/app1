import { describe, expect, it, vi } from 'vitest'
import { buildRequestHeaders, setAuthStoreGetter } from '@/api/httpAuth'
import { voiceChatPipeline } from '@/api/voicechat'
import { setStorage } from '@/utils/storage'

vi.mock('@/config', () => ({
  config: {
    apiBaseUrl: 'https://api.example.test',
    wsBaseUrl: 'wss://ws.example.test',
    features: {
      guestAuth: false,
    },
  },
  APP_CONFIG: {
    wsHeartbeatInterval: 10_000,
    wsMaxReconnect: 3,
  },
}))

vi.mock('@/api/auth', () => ({
  getWsTicket: vi.fn().mockResolvedValue({ data: { ticket: 'ticket-test' } }),
}))

function createUniStorage() {
  const store = new Map<string, string>()

  const uni = {
    getStorageSync: (key: string) => store.get(key) ?? '',
    setStorageSync: (key: string, value: string) => {
      store.set(key, value)
    },
    removeStorageSync: (key: string) => {
      store.delete(key)
    },
  }

  return { store, uni }
}

describe('token storage contract (quoted vs raw)', () => {
  it('buildRequestHeaders uses unquoted token when token is persisted via setStorage (JSON string)', () => {
    const { uni } = createUniStorage()
    ;(globalThis as Record<string, unknown>).uni = uni

    // Force storage fallback by not wiring any auth store.
    setAuthStoreGetter(() => null)

    setStorage('token', 'abc')

    const headers = buildRequestHeaders(true, {})
    expect(headers.Authorization).toBe('Bearer abc')
  })

  it('buildRequestHeaders remains correct when token is a legacy raw string in storage', () => {
    const { uni } = createUniStorage()
    ;(globalThis as Record<string, unknown>).uni = uni

    setAuthStoreGetter(() => null)

    // Legacy behavior: uni.setStorageSync('token', 'abc') without JSON encoding.
    uni.setStorageSync('token', 'abc')

    const headers = buildRequestHeaders(true, {})
    expect(headers.Authorization).toBe('Bearer abc')
  })

  it('voiceChatPipeline sends Authorization without quotes for JSON-stringified token', async () => {
    const { uni } = createUniStorage()

    const uploadFile = vi.fn((opts: Record<string, unknown>) => {
      const header = opts.header as Record<string, string> | undefined
      expect(header?.Authorization).toBe('Bearer abc')
      const success = opts.success as ((res: { data: string }) => void) | undefined
      success?.({ data: JSON.stringify({ code: 0, data: { transcript: 't', aiResponse: 'a' } }) })
      return {}
    })

    ;(globalThis as Record<string, unknown>).uni = {
      ...uni,
      uploadFile,
    }

    setStorage('token', 'abc')

    const result = await voiceChatPipeline('/tmp/audio.m4a')
    expect(result).toEqual({ transcript: 't', aiResponse: 'a' })
    expect(uploadFile).toHaveBeenCalledTimes(1)
  })

  it('voiceChatPipeline remains correct when token is a legacy raw string in storage', async () => {
    const { uni } = createUniStorage()

    const uploadFile = vi.fn((opts: Record<string, unknown>) => {
      const header = opts.header as Record<string, string> | undefined
      expect(header?.Authorization).toBe('Bearer abc')
      const success = opts.success as ((res: { data: string }) => void) | undefined
      success?.({ data: JSON.stringify({ code: 0, data: { transcript: 't', aiResponse: 'a' } }) })
      return {}
    })

    ;(globalThis as Record<string, unknown>).uni = {
      ...uni,
      uploadFile,
    }

    uni.setStorageSync('token', 'abc')

    const result = await voiceChatPipeline('/tmp/audio.m4a')
    expect(result).toEqual({ transcript: 't', aiResponse: 'a' })
    expect(uploadFile).toHaveBeenCalledTimes(1)
  })
})
