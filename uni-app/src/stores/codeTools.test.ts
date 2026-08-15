import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCodeToolsStore } from './codeTools'

vi.mock('@/api/codeTools', () => {
  return {
    listCodeToolsTasks: vi.fn(async () => ({
      code: 0,
      message: 'ok',
      data: { items: [{ id: 't1', kind: 'shell', status: 'pending', approval: { status: 'pending' } }], total: 1, page: 0, size: 20 },
    })),
    createCodeToolsTask: vi.fn(async () => ({
      code: 0,
      message: 'ok',
      data: { id: 't2', kind: 'shell', status: 'pending', approval: { status: 'pending' } },
    })),
    getCodeToolsTask: vi.fn(async () => ({
      code: 0,
      message: 'ok',
      data: { id: 't2', kind: 'shell', status: 'pending', approval: { status: 'pending' }, inputJson: '{"command":"echo hi"}' },
    })),
    listCodeToolsTaskLogs: vi.fn(async () => ({
      code: 0,
      message: 'ok',
      data: [{ id: 'l1', taskId: 't2', level: 'INFO', message: 'created', createdAt: '2026-04-18T00:00:00Z' }],
    })),
    listCodeToolsTaskArtifacts: vi.fn(async () => ({
      code: 0,
      message: 'ok',
      data: [{ id: 'a1', taskId: 't2', artifactType: 'text', name: 'out.txt', contentText: 'hello' }],
    })),
  }
})

describe('codeTools store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('fetchList populates the list', async () => {
    const store = useCodeToolsStore()
    expect(store.items).toEqual([])
    await store.fetchList({ page: 0, size: 20 })
    expect(store.items.length).toBe(1)
    expect(store.items[0]?.id).toBe('t1')
  })

  it('create refreshes list and sets current', async () => {
    const store = useCodeToolsStore()
    const task = await store.create({ kind: 'shell', input: { command: 'echo hi' } })
    expect(task.id).toBe('t2')
    expect(store.current?.id).toBe('t2')
  })

  it('fetchTask loads detail/logs/artifacts', async () => {
    const store = useCodeToolsStore()
    await store.fetchTask('t2')
    expect(store.current?.id).toBe('t2')
    expect(store.currentLogs?.length).toBe(1)
    expect(store.currentArtifacts?.length).toBe(1)
  })
})

