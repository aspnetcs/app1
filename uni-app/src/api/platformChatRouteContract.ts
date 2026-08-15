import { buildPlatformApiPath } from './platformUserRouteContract'

export const PLATFORM_CHAT_ROUTE_CONTRACT = {
  completions: buildPlatformApiPath('chat/completions'),
  completionsMulti: buildPlatformApiPath('chat/completions/multi'),
  completionsSse: buildPlatformApiPath('chat/completions/sse'),
} as const

export function buildConversationForkPath(conversationId: string) {
  return buildPlatformApiPath(`conversations/${encodeURIComponent(conversationId)}/fork`)
}

export function buildChatCompletionsSseUrl(apiBaseUrl: string) {
  return apiBaseUrl.replace(/\/$/, '') + PLATFORM_CHAT_ROUTE_CONTRACT.completionsSse
}

export function buildChatCompatUrl(apiBaseUrl: string) {
  const baseUrl = apiBaseUrl.replace(/\/$/, '')
  return baseUrl.replace(/\/api$/, '') + PLATFORM_CHAT_ROUTE_CONTRACT.completions
}

