import type { StoredChatSession } from '@/stores/chatSessionStorage'
import type { Conversation } from '@/stores/chatState'

type HistoryPageStoreLike = {
  fetchHistory: () => Promise<unknown> | unknown
  fetchArchivedHistory: () => Promise<unknown> | unknown
  startConversationDraft: (scope?: 'persistent' | 'temporary') => void
  setPendingAnchorMessageId?: (messageId?: string | null) => void
  loadConversation?: (conversationId: string) => Promise<boolean> | boolean
}

type HistoryTopicSearchResult = {
  id?: string
  conversationId: string
  title: string
  snippet?: string
}

type HistoryMessageSearchResult = {
  conversationId: string
  messageId: string
  anchorMessageId?: string
  conversationTitle: string
  snippet?: string
}

type HistoryFileSearchResult = {
  conversationId: string
  messageId: string
  anchorMessageId?: string
  conversationTitle: string
  fileLabel?: string
  snippet?: string
}

type HistoryConversationLike = Pick<
  Conversation,
  'title' | 'mode' | 'compareModelIds' | 'captainMode'
>

export function buildHistoryConversationSession(
  conversationId: string,
  conversation?: HistoryConversationLike,
  options: {
    fallbackTitle?: string
    inferredCompareIds?: string[]
  } = {},
): StoredChatSession {
  if (conversation?.mode === 'team') {
    return {
      conversationId,
      mode: 'team',
      multiModelIds:
        conversation.compareModelIds?.length ? [...conversation.compareModelIds] : undefined,
      captainMode: conversation.captainMode,
      title: conversation.title ?? options.fallbackTitle,
    }
  }

  const compareIds = conversation?.compareModelIds?.length
    ? [...conversation.compareModelIds]
    : options.inferredCompareIds?.filter(Boolean) ?? []

  return {
    conversationId,
    mode: compareIds.length > 1 ? 'compare' : 'single',
    multiModelIds: compareIds.length > 1 ? compareIds : undefined,
    title: conversation?.title ?? options.fallbackTitle,
  }
}

export async function loadHistoryPageData(store: HistoryPageStoreLike) {
  await Promise.all([
    Promise.resolve(store.fetchHistory()),
    Promise.resolve(store.fetchArchivedHistory()),
  ])
}

export function startPersistentHistoryDraft(store: HistoryPageStoreLike) {
  store.startConversationDraft('persistent')
}

export async function openHistorySearchHit(
  store: HistoryPageStoreLike,
  result: HistoryTopicSearchResult | HistoryMessageSearchResult | HistoryFileSearchResult,
) {
  const ok = store.loadConversation ? await store.loadConversation(result.conversationId) : true
  if (!ok) return false
  if ('messageId' in result) {
    store.setPendingAnchorMessageId?.(result.anchorMessageId ?? result.messageId)
  } else {
    store.setPendingAnchorMessageId?.(null)
  }
  return true
}
