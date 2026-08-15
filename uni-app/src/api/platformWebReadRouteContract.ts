import { buildPlatformApiPath } from './platformUserRouteContract'

export const PLATFORM_WEB_READ_ROUTE_CONTRACT = {
  fetch: buildPlatformApiPath('web-read/fetch'),
} as const
