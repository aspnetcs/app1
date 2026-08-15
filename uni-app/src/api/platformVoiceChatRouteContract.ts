import { buildPlatformApiPath } from './platformUserRouteContract'

export const PLATFORM_VOICE_CHAT_ROUTE_CONTRACT = {
  config: buildPlatformApiPath('voice-chat/config'),
  pipeline: buildPlatformApiPath('voice-chat/pipeline'),
} as const

export function buildVoiceChatPipelineUrl(apiBaseUrl: string) {
  return apiBaseUrl.replace(/\/$/, '') + PLATFORM_VOICE_CHAT_ROUTE_CONTRACT.pipeline
}
