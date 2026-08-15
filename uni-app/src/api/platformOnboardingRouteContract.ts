import { buildPlatformApiPath } from './platformUserRouteContract'

const PLATFORM_ONBOARDING_PATH = buildPlatformApiPath('onboarding')

export const PLATFORM_ONBOARDING_ROUTE_CONTRACT = {
  state: `${PLATFORM_ONBOARDING_PATH}/state`,
} as const