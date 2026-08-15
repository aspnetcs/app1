import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

export interface KnowledgeBaseOption {
  id: string
  name: string
  status?: string
  documentCount?: number
}

export interface ConversationKnowledgeBinding {
  conversationId?: string
  knowledgeBaseIds: string[]
}

const KNOWLEDGE_BASES_PATH = buildPlatformApiPath('knowledge/bases')
const KNOWLEDGE_BINDINGS_PATH = buildPlatformApiPath('knowledge/conversations')

export function listKnowledgeBaseOptions() {
  return http.get<KnowledgeBaseOption[]>(KNOWLEDGE_BASES_PATH, undefined, { auth: true, silent: true })
}

export function getConversationKnowledgeBinding(conversationId: string) {
  return http.get<ConversationKnowledgeBinding>(`${KNOWLEDGE_BINDINGS_PATH}/${encodeURIComponent(conversationId)}`, undefined, {
    auth: true,
    silent: true,
  })
}

export function updateConversationKnowledgeBinding(conversationId: string, knowledgeBaseIds: string[]) {
  return http.put<ConversationKnowledgeBinding>(`${KNOWLEDGE_BINDINGS_PATH}/${encodeURIComponent(conversationId)}`, {
    knowledgeBaseIds,
  }, {
    auth: true,
    silent: true,
  })
}
