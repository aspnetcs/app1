import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useModelStore } from './models'
import { getModelCatalog } from '@/api/models'

const ensureProfileLoaded = vi.fn(async () => null)
const isModelAllowed = vi.fn(() => true)

vi.mock('@/api/models', () => ({
  getModelCatalog: vi.fn(),
}))

vi.mock('@/stores/group', () => ({
  useGroupStore: () => ({
    ensureProfileLoaded,
    isModelAllowed,
  }),
}))

function installUniStorage() {
  const storage = new Map<string, unknown>()

  ;(globalThis as Record<string, unknown>).uni = {
    getStorageSync: vi.fn((key: string) => storage.get(key) ?? ''),
    setStorageSync: vi.fn((key: string, value: unknown) => {
      storage.set(key, value)
    }),
    removeStorageSync: vi.fn((key: string) => {
      storage.delete(key)
    }),
    showToast: vi.fn(),
  }
}

describe('useModelStore', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    installUniStorage()
  })

  it('preserves the last successful catalog and selection when a later fetch fails', async () => {
    vi.mocked(getModelCatalog).mockResolvedValueOnce({
      code: 0,
      message: '',
      data: [
        { id: 'gpt-4o', name: 'GPT-4o', avatar: 'openai', description: 'A' },
        { id: 'claude-3-5-sonnet', name: 'Claude 3.5 Sonnet', avatar: 'claude', description: 'B' },
      ],
    })

    const store = useModelStore()
    await store.fetchModels()
    store.selectModel('claude-3-5-sonnet')

    vi.mocked(getModelCatalog).mockRejectedValueOnce(new Error('模型目录服务暂不可用'))
    await store.fetchModels()

    expect(store.loadError).toBe('模型目录服务暂不可用')
    expect(store.models.map(model => model.id)).toEqual(['claude-3-5-sonnet', 'gpt-4o'])
    expect(store.selectedModelId).toBe('claude-3-5-sonnet')
    expect(store.loaded).toBe(true)
    expect(store.loading).toBe(false)
  })
})
