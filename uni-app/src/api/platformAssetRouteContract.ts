import { buildPlatformApiPath } from './platformUserRouteContract'

const PLATFORM_ASSET_BASE_PATH = buildPlatformApiPath('asset')

export const PLATFORM_ASSET_ROUTE_CONTRACT = {
  presign: `${PLATFORM_ASSET_BASE_PATH}/presign`,
  confirm: `${PLATFORM_ASSET_BASE_PATH}/confirm`,
} as const
