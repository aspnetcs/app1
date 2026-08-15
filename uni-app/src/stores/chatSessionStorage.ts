import { getStorage, removeStorage, setStorage } from '@/utils/storage'

export type StoredChatSessionMode = 'single' | 'compare' | 'team'
export type StoredCaptainMode = 'auto' | 'fixed_first'

export interface StoredChatSession {
  conversationId: string
  mode: StoredChatSessionMode
  multiModelIds?: string[]
  captainMode?: StoredCaptainMode
  title?: string
}

const ACTIVE_CHAT_SESSION_STORAGE_KEY = 'activeChatSession'

function normalizeModelIds(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined
  const modelIds = value
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean)
  return modelIds.length > 0 ? modelIds : undefined
}

function normalizeMode(value: unknown): StoredChatSessionMode | null {
  return value === 'compare' || value === 'team' || value === 'single' ? value : null
}

function normalizeCaptainMode(value: unknown): StoredCaptainMode | undefined {
  return value === 'fixed_first' || value === 'auto' ? value : undefined
}

export function readActiveChatSession(): StoredChatSession | null {
  const raw = getStorage<Record<string, unknown>>(ACTIVE_CHAT_SESSION_STORAGE_KEY)
  if (!raw) return null

  const conversationId =
    typeof raw.conversationId === 'string' ? raw.conversationId.trim() : ''
  const mode = normalizeMode(raw.mode)
  if (!conversationId || !mode) {
    removeStorage(ACTIVE_CHAT_SESSION_STORAGE_KEY)
    return null
  }

  return {
    conversationId,
    mode,
    multiModelIds: normalizeModelIds(raw.multiModelIds),
    captainMode: normalizeCaptainMode(raw.captainMode),
    title: typeof raw.title === 'string' && raw.title.trim() ? raw.title.trim() : undefined,
  }
}

export function writeActiveChatSession(session: StoredChatSession) {
  setStorage(ACTIVE_CHAT_SESSION_STORAGE_KEY, {
    conversationId: session.conversationId,
    mode: session.mode,
    multiModelIds: session.multiModelIds,
    captainMode: session.captainMode,
    title: session.title,
  })
}

export function clearActiveChatSession() {
  removeStorage(ACTIVE_CHAT_SESSION_STORAGE_KEY)
}
