import { buildPlatformApiPath } from './platformUserRouteContract'

const PLATFORM_AUDIO_BASE_PATH = buildPlatformApiPath('audio')
const PLATFORM_EXPORT_BASE_PATH = buildPlatformApiPath('export')

export const PLATFORM_MEDIA_ROUTE_CONTRACT = {
  audioSpeech: `${PLATFORM_AUDIO_BASE_PATH}/speech`,
  exportChatImageConfig: `${PLATFORM_EXPORT_BASE_PATH}/chat-image-config`,
} as const
