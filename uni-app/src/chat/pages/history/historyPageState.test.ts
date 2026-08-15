import { describe, expect, it, vi } from 'vitest'
import {
  buildHistoryConversationSession,
  loadHistoryPageData,
  openHistorySearchHit,
  startPersistentHistoryDraft,
} from './historyPageState'

describe('historyPageState', () => {
  it('loads both active and archived history for the page', async () => {
    const store = {
      fetchHistory: vi.fn(),
      fetchArchivedHistory: vi.fn(),
      startConversationDraft: vi.fn(),
    }

    await loadHistoryPageData(store)

    expect(store.fetchHistory).toHaveBeenCalledTimes(1)
    expect(store.fetchArchivedHistory).toHaveBeenCalledTimes(1)
  })

  it('starts a fresh persistent draft instead of leaving the store in temporary scope', () => {
    const store = {
      fetchHistory: vi.fn(),
      fetchArchivedHistory: vi.fn(),
      startConversationDraft: vi.fn(),
    }

    startPersistentHistoryDraft(store)

    expect(store.startConversationDraft).toHaveBeenCalledWith('persistent')
  })

  it('opens a message search hit and stores the pending anchor', async () => {
    const store = {
      fetchHistory: vi.fn(),
      fetchArchivedHistory: vi.fn(),
      startConversationDraft: vi.fn(),
      setPendingAnchorMessageId: vi.fn(),
      loadConversation: vi.fn().mockResolvedValue(true),
    }

    const opened = await openHistorySearchHit(store, {
      conversationId: 'conv-1',
      conversationTitle: 'topic',
      messageId: 'msg-1',
      anchorMessageId: 'msg-1',
      snippet: 'hello',
    })

    expect(opened).toBe(true)
    expect(store.loadConversation).toHaveBeenCalledWith('conv-1')
    expect(store.setPendingAnchorMessageId).toHaveBeenCalledWith('msg-1')
  })

  it('preserves team mode when building a history conversation session', () => {
    expect(
      buildHistoryConversationSession('team-1', {
        title: '团队会话',
        mode: 'team',
        compareModelIds: ['model-a', 'model-b'],
        captainMode: 'fixed_first',
      }),
    ).toEqual({
      conversationId: 'team-1',
      mode: 'team',
      multiModelIds: ['model-a', 'model-b'],
      captainMode: 'fixed_first',
      title: '团队会话',
    })
  })

  it('falls back to inferred compare ids for non-team sessions', () => {
    expect(
      buildHistoryConversationSession(
        'compare-1',
        {
          title: '多模型',
          mode: 'compare',
          compareModelIds: [],
        },
        {
          inferredCompareIds: ['model-a', 'model-b'],
        },
      ),
    ).toEqual({
      conversationId: 'compare-1',
      mode: 'compare',
      multiModelIds: ['model-a', 'model-b'],
      title: '多模型',
    })
  })
})
