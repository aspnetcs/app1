import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  clearActiveChatSession,
  readActiveChatSession,
  writeActiveChatSession,
} from './chatSessionStorage'

describe('chatSessionStorage', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()
    ;(globalThis as { uni?: unknown }).uni = {
      getStorageSync: vi.fn((key: string) => storage.get(key) ?? ''),
      setStorageSync: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeStorageSync: vi.fn((key: string) => {
        storage.delete(key)
      }),
    }
  })

  it('round-trips a compare session with model ids', () => {
    writeActiveChatSession({
      conversationId: 'conv-1',
      mode: 'compare',
      multiModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      title: 'Compare chat',
    })

    expect(readActiveChatSession()).toEqual({
      conversationId: 'conv-1',
      mode: 'compare',
      multiModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      title: 'Compare chat',
      captainMode: undefined,
    })
  })

  it('drops malformed payloads instead of returning a broken session', () => {
    storage.set('activeChatSession', JSON.stringify({ mode: 'team' }))

    expect(readActiveChatSession()).toBeNull()
  })

  it('clears the persisted session', () => {
    writeActiveChatSession({
      conversationId: 'conv-2',
      mode: 'team',
      multiModelIds: ['model-a', 'model-b'],
      captainMode: 'fixed_first',
    })

    clearActiveChatSession()

    expect(readActiveChatSession()).toBeNull()
  })
})
