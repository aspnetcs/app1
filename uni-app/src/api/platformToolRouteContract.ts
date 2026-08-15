import { buildPlatformApiPath } from './platformUserRouteContract'

export const PLATFORM_TOOL_ROUTE_CONTRACT = {
  config: buildPlatformApiPath('tools/config'),
} as const
