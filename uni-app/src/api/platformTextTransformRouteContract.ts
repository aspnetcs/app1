import { buildPlatformApiPath } from './platformUserRouteContract'

export const PLATFORM_TEXT_TRANSFORM_ROUTE_CONTRACT = {
  promptOptimize: buildPlatformApiPath('prompt-optimize'),
  translationMessages: buildPlatformApiPath('translation/messages'),
} as const
