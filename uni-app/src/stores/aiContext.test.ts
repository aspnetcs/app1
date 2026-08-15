import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAiContextStore } from './aiContext'
import {
  getConversationKnowledgeBinding,
  listKnowledgeBaseOptions,
  updateConversationKnowledgeBinding,
} from '@/api/knowledge'
import {
  deleteMemoryEntry,
  getMemoryRuntimeConfig,
  listMemoryEntries,
  updateMemoryConsent,
} from '@/api/memory'

vi.mock('@/api/knowledge', () => ({
  getConversationKnowledgeBinding: vi.fn(),
  listKnowledgeBaseOptions: vi.fn(),
  updateConversationKnowledgeBinding: vi.fn(),
}))

vi.mock('@/api/memory', () => ({
  deleteMemoryEntry: vi.fn(),
  getMemoryRuntimeConfig: vi.fn(),
  listMemoryEntries: vi.fn(),
  updateMemoryConsent: vi.fn(),
}))

describe('useAiContextStore knowledge binding guards', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    vi.mocked(listKnowledgeBaseOptions).mockResolvedValue({ code: 0, message: 'ok', data: [] })
    vi.mocked(getMemoryRuntimeConfig).mockResolvedValue({ code: 0, message: 'ok', data: { enabled: false } })
    vi.mocked(listMemoryEntries).mockResolvedValue({ code: 0, message: 'ok', data: [] })
    vi.mocked(updateMemoryConsent).mockResolvedValue({ code: 0, message: 'ok', data: { enabled: false } })
    vi.mocked(deleteMemoryEntry).mockResolvedValue({ code: 0, message: 'ok', data: undefined })
  })

  it('skips loading knowledge bindings for non-uuid conversation ids', async () => {
    const store = useAiContextStore()
    store.selectedKnowledgeBaseIds = ['kb-1']
    store.knowledgeSyncError = 'old-error'

    await store.loadConversationKnowledgeSelection('temp-conv-1')

    expect(getConversationKnowledgeBinding).not.toHaveBeenCalled()
    expect(store.selectedKnowledgeBaseIds).toEqual([])
    expect(store.knowledgeSyncError).toBe('')
  })

  it('skips syncing knowledge bindings for non-uuid conversation ids', async () => {
    const store = useAiContextStore()
    store.selectedKnowledgeBaseIds = ['kb-1']
    store.knowledgeSyncError = 'old-error'

    await store.syncConversationKnowledgeSelection('team-1')

    expect(updateConversationKnowledgeBinding).not.toHaveBeenCalled()
    expect(store.knowledgeSyncError).toBe('')
  })

  it('loads knowledge bindings for uuid conversation ids', async () => {
    vi.mocked(getConversationKnowledgeBinding).mockResolvedValue({
      code: 0,
      message: 'ok',
      data: { knowledgeBaseIds: ['kb-1', 'kb-2'] },
    })
    const store = useAiContextStore()

    await store.loadConversationKnowledgeSelection('123e4567-e89b-12d3-a456-426614174000')

    expect(getConversationKnowledgeBinding).toHaveBeenCalledWith('123e4567-e89b-12d3-a456-426614174000')
    expect(store.selectedKnowledgeBaseIds).toEqual(['kb-1', 'kb-2'])
    expect(store.knowledgeSyncError).toBe('')
  })

  it('keeps the previous memory runtime state when consent sync fails', async () => {
    vi.mocked(updateMemoryConsent).mockRejectedValue(new Error('sync failed'))
    const store = useAiContextStore()
    store.memoryRuntime = {
      enabled: false,
      consentRequired: true,
      consentGranted: false,
      summary: '记忆功能已关闭。',
    }

    await store.updateMemoryRuntimeConsent(true)

    expect(store.memoryRuntime).toEqual({
      enabled: false,
      consentRequired: true,
      consentGranted: false,
      summary: '记忆功能已关闭。',
    })
    expect(store.memoryRuntimeError).toBe('记忆开关同步失败，请稍后重试')
  })
})
