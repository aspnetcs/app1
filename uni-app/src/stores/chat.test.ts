import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChatStore } from './chat'
import { writeActiveChatSession } from './chatSessionStorage'
import {
  createConversationData,
  deleteConversationData,
  fetchArchivedConversationHistoryData,
  fetchConversationHistoryData,
  fetchConversationMessagesData,
  persistConversationMessageData,
} from './chatPersistence'

vi.mock('./chatPersistence', () => ({
  createConversationData: vi.fn(),
  deleteConversationData: vi.fn(),
  fetchArchivedConversationHistoryData: vi.fn(),
  fetchConversationHistoryData: vi.fn(),
  fetchConversationMessagesData: vi.fn(),
  fetchMessageVersionData: vi.fn(),
  persistConversationMessageData: vi.fn(),
  pinConversationData: vi.fn(),
  restoreConversationData: vi.fn(),
  updateConversationData: vi.fn(),
}))

describe('useChatStore temporary conversation flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    const storage = new Map<string, string>()
    ;(globalThis as { uni?: unknown }).uni = {
      showToast: vi.fn(),
      getStorageSync: vi.fn((key: string) => storage.get(key) ?? ''),
      setStorageSync: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeStorageSync: vi.fn((key: string) => {
        storage.delete(key)
      }),
    }
  })

  it('creates a temporary conversation without inserting it into normal history', async () => {
    vi.mocked(createConversationData).mockResolvedValue({
      id: 'temp-conv-1',
      title: '临时对话',
      updatedAt: 1,
      model: 'gpt-temp',
      pinned: false,
      starred: false,
      messageCount: 0,
    })

    const store = useChatStore()
    store.selectedModel = 'gpt-temp'
    store.startConversationDraft('temporary')

    const conversationId = await store.ensureConversation('隐身提问')

    expect(conversationId).toBe('temp-conv-1')
    expect(createConversationData).toHaveBeenCalledWith({
      title: '隐身提问',
      model: 'gpt-temp',
      isTemporary: true,
    })
    expect(store.currentConversationId).toBe('temp-conv-1')
    expect(store.conversationScope).toBe('temporary')
    expect(store.history).toEqual([])
  })

  it('persists compare model ids when creating a compare conversation', async () => {
    vi.mocked(createConversationData).mockResolvedValue({
      id: 'compare-conv-1',
      title: 'Compare',
      updatedAt: 1,
      model: 'gpt-4o',
      compareModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      mode: 'compare',
      pinned: false,
      starred: false,
      messageCount: 0,
    })

    const store = useChatStore()
    store.selectedModel = 'gpt-4o'

    await store.ensureConversation('Compare', {
      compareModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
    })

    expect(createConversationData).toHaveBeenCalledWith({
      title: 'Compare',
      model: 'gpt-4o',
      compareModels: ['gpt-4o', 'claude-3-7-sonnet'],
      isTemporary: false,
    })
  })

  it('skips normal message persistence while the store stays in temporary scope', async () => {
    const store = useChatStore()
    store.startConversationDraft('temporary')
    store.currentConversationId = 'temp-conv-1'

    const result = await store.persistMessage({
      id: 'msg-1',
      role: 'user',
      content: '不要写入普通历史',
      createdAt: Date.now(),
      status: 'success',
    })

    expect(result).toBeNull()
    expect(persistConversationMessageData).not.toHaveBeenCalled()
  })

  it('keeps the active temporary draft when history refresh does not contain that conversation', async () => {
    vi.mocked(fetchConversationHistoryData).mockResolvedValue({
      history: [
        {
          id: 'persistent-1',
          title: '普通会话',
          updatedAt: 2,
          model: 'gpt-4o',
          pinned: false,
          starred: false,
          messageCount: 2,
        },
      ],
    })

    const store = useChatStore()
    store.startConversationDraft('temporary')
    store.currentConversationId = 'temp-conv-1'
    store.addMessage({
      id: 'local-msg',
      role: 'user',
      content: '保留临时草稿',
      createdAt: Date.now(),
      status: 'success',
    })

    await store.fetchHistory()

    expect(store.currentConversationId).toBe('temp-conv-1')
    expect(store.messages).toHaveLength(1)
    expect(store.history.map((item) => item.id)).toEqual(['persistent-1'])
  })

  it('merges the active team session back into history after a refresh', async () => {
    vi.mocked(fetchConversationHistoryData).mockResolvedValue({
      history: [
        {
          id: 'persistent-1',
          title: 'Normal',
          updatedAt: 2,
          mode: 'chat',
          pinned: false,
          starred: false,
          messageCount: 2,
        },
      ],
    })

    writeActiveChatSession({
      conversationId: 'team-1',
      mode: 'team',
      multiModelIds: ['model-a', 'model-b'],
      captainMode: 'auto',
      title: '[Team] Session',
    })

    const store = useChatStore()
    await store.fetchHistory()

    expect(store.history.map((item) => item.id)).toEqual(['team-1', 'persistent-1'])
    expect(store.history[0]).toMatchObject({
      id: 'team-1',
      title: '[Team] Session',
      mode: 'team',
      compareModelIds: ['model-a', 'model-b'],
      captainMode: 'auto',
    })
  })

  it('restores persistent scope when a saved conversation is opened from history', async () => {
    vi.mocked(fetchConversationMessagesData).mockResolvedValue([
      {
        id: 'server-msg-1',
        role: 'assistant',
        content: '已恢复持久会话',
        createdAt: Date.now(),
        status: 'success',
      },
    ])

    const store = useChatStore()
    store.startConversationDraft('temporary')

    const loaded = await store.loadConversation('persistent-1')

    expect(loaded).toBe(true)
    expect(fetchConversationMessagesData).toHaveBeenCalledWith('persistent-1')
    expect(store.conversationScope).toBe('persistent')
    expect(store.currentConversationId).toBe('persistent-1')
    expect(store.messages).toHaveLength(1)
  })

  it('can clear stale compare and captain metadata when returning a conversation to single mode', () => {
    const store = useChatStore()
    store.history = [
      {
        id: 'compare-1',
        title: 'Compare',
        updatedAt: 1,
        mode: 'compare',
        compareModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
        captainMode: 'fixed_first',
        pinned: false,
        starred: false,
        messageCount: 2,
      },
    ]

    store.touchConversation('compare-1', 'Compare', {
      bumpUpdatedAt: false,
      mode: 'chat',
      compareModelIds: null,
      captainMode: null,
    })

    expect(store.history[0]).toMatchObject({
      id: 'compare-1',
      mode: 'chat',
    })
    expect(store.history[0].compareModelIds).toBeUndefined()
    expect(store.history[0].captainMode).toBeUndefined()
  })

  it('updates an existing team conversation title when a fuller title becomes available', () => {
    const store = useChatStore()
    store.history = [
      {
        id: 'team-1',
        title: '[团队]',
        updatedAt: 1,
        mode: 'team',
        compareModelIds: ['model-a', 'model-b'],
        captainMode: 'auto',
        pinned: false,
        starred: false,
        messageCount: 0,
      },
    ]

    store.touchConversation('team-1', '[团队] 什么是量子计算？', {
      bumpUpdatedAt: false,
      mode: 'team',
      compareModelIds: ['model-a', 'model-b'],
      captainMode: 'auto',
    })

    expect(store.history[0].title).toBe('[团队] 什么是量子计算？')
  })

  it('clears the persisted active session when deleting that conversation', async () => {
    vi.mocked(deleteConversationData).mockResolvedValue(undefined)
    vi.mocked(fetchConversationHistoryData).mockResolvedValue({
      history: [],
    })

    writeActiveChatSession({
      conversationId: 'team-1',
      mode: 'team',
      multiModelIds: ['model-a', 'model-b'],
      captainMode: 'auto',
      title: '[Team] Session',
    })

    const store = useChatStore()
    store.history = [
      {
        id: 'team-1',
        title: '[Team] Session',
        updatedAt: 1,
        model: 'model-a',
        pinned: false,
        starred: false,
        messageCount: 0,
      },
    ]
    store.currentConversationId = 'team-1'

    await store.deleteConversation('team-1')
    await store.fetchHistory()

    expect(store.currentConversationId).toBeNull()
    expect(store.history).toEqual([])
  })

  it('shows a toast when history loading fails and there is no cached history to fall back to', async () => {
    vi.mocked(fetchConversationHistoryData).mockRejectedValue(new Error('history unavailable'))

    const store = useChatStore()
    const uniMock = globalThis as typeof globalThis & {
      uni: { showToast: ReturnType<typeof vi.fn> }
    }

    await store.fetchHistory()

    expect(uniMock.uni.showToast).toHaveBeenCalledWith({
      title: '历史对话加载失败，请稍后重试',
      icon: 'none',
    })
    expect(store.history).toEqual([])
  })

  it('shows a toast when archived history loading fails and there is no cached archive to fall back to', async () => {
    vi.mocked(fetchArchivedConversationHistoryData).mockRejectedValue(new Error('archived unavailable'))

    const store = useChatStore()
    const uniMock = globalThis as typeof globalThis & {
      uni: { showToast: ReturnType<typeof vi.fn> }
    }

    await store.fetchArchivedHistory()

    expect(uniMock.uni.showToast).toHaveBeenCalledWith({
      title: 'archived unavailable',
      icon: 'none',
    })
    expect(store.archivedHistory).toEqual([])
  })

  it('still shows a toast when archived history refresh fails with cached archive data present', async () => {
    vi.mocked(fetchArchivedConversationHistoryData).mockRejectedValue(new Error('archived refresh failed'))

    const store = useChatStore()
    const uniMock = globalThis as typeof globalThis & {
      uni: { showToast: ReturnType<typeof vi.fn> }
    }
    store.archivedHistory = [
      {
        id: 'archived-1',
        title: '已归档会话',
        updatedAt: 1,
        deletedAt: 1,
        model: 'gpt-4o',
        pinned: false,
        starred: false,
        messageCount: 1,
      },
    ]

    const result = await store.fetchArchivedHistory()

    expect(uniMock.uni.showToast).toHaveBeenCalledWith({
      title: 'archived refresh failed',
      icon: 'none',
    })
    expect(result).toEqual(store.archivedHistory)
  })
})
