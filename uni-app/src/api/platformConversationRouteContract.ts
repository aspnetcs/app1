import { buildPlatformApiPath } from './platformUserRouteContract'

const PLATFORM_CONVERSATIONS_PATH = buildPlatformApiPath('conversations')
const PLATFORM_MESSAGES_PATH = buildPlatformApiPath('messages')

export const PLATFORM_CONVERSATION_ROUTE_CONTRACT = {
  conversations: PLATFORM_CONVERSATIONS_PATH,
  archived: buildPlatformApiPath('conversations/archived'),
} as const

export function buildConversationMessagesPath(conversationId: string) {
  return `${PLATFORM_CONVERSATIONS_PATH}/${encodeURIComponent(conversationId)}/messages`
}

export function buildConversationByIdPath(conversationId: string) {
  return `${PLATFORM_CONVERSATIONS_PATH}/${encodeURIComponent(conversationId)}`
}

export function buildConversationPinPath(conversationId: string) {
  return `${buildConversationByIdPath(conversationId)}/pin`
}

export function buildConversationStarPath(conversationId: string) {
  return `${buildConversationByIdPath(conversationId)}/star`
}

export function buildConversationRestorePath(conversationId: string) {
  return `${buildConversationByIdPath(conversationId)}/restore`
}

export function buildMessageVersionsPath(parentMessageId: string) {
  return `${PLATFORM_MESSAGES_PATH}/${encodeURIComponent(parentMessageId)}/versions`
}
