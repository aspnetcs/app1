import { http } from './http'
import {
  PLATFORM_CONVERSATION_ROUTE_CONTRACT,
  buildConversationMessagesPath,
  buildConversationByIdPath,
  buildConversationPinPath,
  buildConversationRestorePath,
  buildConversationStarPath,
  buildMessageVersionsPath,
} from './platformConversationRouteContract'
import type { ChatMessage, MessageVersionsResponse } from './types'

export interface ConversationSummaryResponse {
  id: string
  title?: string
  model?: string
   mode?: 'chat' | 'compare' | 'team'
  compare_models?: string[]
   captainMode?: 'auto' | 'fixed_first'
   captain_selection_mode?: 'auto' | 'fixed_first' | null
  system_prompt?: string | null
  pinned?: boolean
  starred?: boolean
  message_count?: number
  is_temporary?: boolean
  created_at?: string
  updated_at?: string
  pinned_at?: string | null
  deleted_at?: string | null
  source_conversation_id?: string | null
  source_message_id?: string | null
}

export interface SaveConversationMessagePayload {
  role: ChatMessage['role']
  content: string
  content_type?: string
  media_url?: string
  model?: string
  multi_round_id?: string
  branch_index?: number
  token_count?: number
  channel_id?: number
  parent_message_id?: string | null
  version?: number | null
  blocks?: Array<Record<string, unknown>>
}

export interface ConversationMessageResponse {
  id: string
  role: ChatMessage['role']
  content: string
  content_type?: string | null
  media_url?: string | null
  parent_message_id?: string | null
  multi_round_id?: string | null
  branch_index?: number | null
  version?: number | null
  token_count?: number | null
  model?: string | null
  blocks?: Array<Record<string, unknown>>
  created_at?: string
}

export const listConversations = () =>
  http.get<ConversationSummaryResponse[]>(PLATFORM_CONVERSATION_ROUTE_CONTRACT.conversations, undefined, {
    auth: true,
    silent: true,
  })

export const createConversationRemote = (data: {
  title?: string
  model?: string
  mode?: 'chat' | 'compare' | 'team'
  compare_models?: string[]
  captain_selection_mode?: 'auto' | 'fixed_first'
  system_prompt?: string
  isTemporary?: boolean
}) =>
  http.post<ConversationSummaryResponse>(PLATFORM_CONVERSATION_ROUTE_CONTRACT.conversations, data, {
    auth: true,
    silent: true,
  })

export const listConversationMessages = (conversationId: string) =>
  http.get<ConversationMessageResponse[]>(buildConversationMessagesPath(conversationId), undefined, {
    auth: true,
    silent: true,
  })

export const saveConversationMessage = (conversationId: string, data: SaveConversationMessagePayload) =>
  http.post<ConversationMessageResponse>(buildConversationMessagesPath(conversationId), data, {
    auth: true,
    silent: true,
  })

export const updateConversationRemote = (conversationId: string, data: {
  title?: string
  model?: string
  mode?: 'chat' | 'compare' | 'team'
  compare_models?: string[]
  captain_selection_mode?: 'auto' | 'fixed_first'
  system_prompt?: string
}) =>
  http.put<ConversationSummaryResponse>(buildConversationByIdPath(conversationId), data, {
    auth: true,
    silent: true,
  })

export const deleteConversationRemote = (conversationId: string) =>
  http.delete<void>(buildConversationByIdPath(conversationId), undefined, {
    auth: true,
    silent: true,
  })

export const listArchivedConversations = () =>
  http.get<ConversationSummaryResponse[]>(PLATFORM_CONVERSATION_ROUTE_CONTRACT.archived, undefined, {
    auth: true,
    silent: true,
  })

export const restoreConversationRemote = (conversationId: string) =>
  http.put<ConversationSummaryResponse>(buildConversationRestorePath(conversationId), {}, {
    auth: true,
    silent: true,
  })

export const listMessageVersions = (parentMessageId: string) =>
  http.get<MessageVersionsResponse>(buildMessageVersionsPath(parentMessageId), undefined, {
    auth: true,
    silent: true,
  })

export const pinConversationRemote = (conversationId: string, pinned: boolean) =>
  http.put<ConversationSummaryResponse>(buildConversationPinPath(conversationId), { pinned }, {
    auth: true,
    silent: true,
  })

export const starConversationRemote = (conversationId: string, starred: boolean) =>
  http.put<ConversationSummaryResponse>(buildConversationStarPath(conversationId), { starred }, {
    auth: true,
    silent: true,
  })

