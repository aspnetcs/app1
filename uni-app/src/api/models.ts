import { http } from './http'
import { buildPlatformApiPath, PLATFORM_USER_ROUTE_CONTRACT } from './platformUserRouteContract'

export type NumericLike = number | string | null | undefined

export interface ModelCatalogItem {
  id: string
  name: string
  avatar: string
  description: string
  pinned?: boolean
  sortOrder?: number
  defaultSelected?: boolean
  isDefault?: boolean
  multiChatEnabled?: boolean
  billingEnabled?: boolean
  requestPriceUsd?: NumericLike
  promptPriceUsd?: NumericLike
  inputPriceUsdPer1M?: NumericLike
  outputPriceUsdPer1M?: NumericLike
  supportsImageParsing?: boolean
  supportsImageParsingSource?: 'manual' | 'inferred' | 'unknown'
}

export function getModelCatalog() {
  return http.get<ModelCatalogItem[]>(PLATFORM_USER_ROUTE_CONTRACT.models, undefined, { auth: true })
}

export interface UserQuotaDailyQuota {
  used?: number | null
  limit?: number | null
}

export interface UserQuotaRateLimit {
  perMinute?: number | null
  modelPerMinute?: number | null
}

export interface UserQuotaCreditsSummary {
  hasAccount?: boolean
  creditsSystemEnabled?: boolean
  freeModeEnabled?: boolean
  creditBalance?: NumericLike
  creditUsed?: NumericLike
  manualCreditAdjustment?: NumericLike
  manualAdjustment?: NumericLike
  effectiveBalance?: NumericLike
  periodCredits?: NumericLike
  periodType?: string | null
  periodStartAt?: string | null
  periodEndAt?: string | null
  role?: string | null
  unlimited?: boolean
}

export interface UserQuotaResponse {
  role?: string | null
  dailyQuota?: UserQuotaDailyQuota | null
  rateLimit?: UserQuotaRateLimit | null
  allowedModelCount?: number | null
  credits?: UserQuotaCreditsSummary | null
}

const USER_QUOTA_PATH = buildPlatformApiPath('user/quota')

export function getUserQuota() {
  return http.get<UserQuotaResponse>(USER_QUOTA_PATH, undefined, { auth: true })
}
