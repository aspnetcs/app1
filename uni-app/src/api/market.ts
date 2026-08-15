import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

export type MarketAssetType = 'AGENT' | 'KNOWLEDGE' | 'MCP' | 'SKILL'

export interface MarketAssetSource {
  id?: string
  name?: string
  description?: string
  category?: string
  tags?: string[] | string
  author?: string
  featured?: boolean
  installCount?: number
  documentCount?: number
  transportType?: string
  contentFormat?: string
  usageMode?: string
  aiUsageInstruction?: string
  aiUsageEntry?: {
    title?: string
    usageMode?: string
    instruction?: string
  }
  content?: string
  contentPreview?: string
  absolutePath?: string
  relativePath?: string
  entryFile?: string
  contentBytes?: number
  available?: boolean
  [key: string]: unknown
}

export interface MarketAsset {
  assetType: MarketAssetType
  sourceId: string
  catalogId?: string | null
  title?: string
  summary?: string
  description?: string
  category?: string
  tags?: string[] | string
  cover?: string
  featured?: boolean
  sortOrder?: number
  saved: boolean
  saveMode?: string
  enabled?: boolean
  available?: boolean
  contentFormat?: string
  usageMode?: string
  aiUsageInstruction?: string
  aiUsageEntry?: {
    title?: string
    usageMode?: string
    instruction?: string
  }
  extraConfig?: Record<string, unknown>
  source?: MarketAssetSource
  savedAt?: string
  updatedAt?: string
}

export interface MarketAssetQuery {
  assetType?: MarketAssetType
  keyword?: string
}

const MARKET_ASSETS_PATH = buildPlatformApiPath('market/assets')
const MARKET_SAVED_ASSETS_PATH = buildPlatformApiPath('market/saved-assets')

function normalizeString(value: unknown) {
  if (value == null) return null
  const text = String(value).trim()
  return text || null
}

function buildQueryPath(basePath: string, query: Record<string, string | undefined>) {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    const normalized = normalizeString(value)
    if (normalized) {
      params.set(key, normalized)
    }
  })
  const suffix = params.toString()
  return suffix ? `${basePath}?${suffix}` : basePath
}

export function listMarketAssets(query: MarketAssetQuery = {}) {
  return http.get<MarketAsset[]>(
    buildQueryPath(MARKET_ASSETS_PATH, {
      assetType: query.assetType,
      keyword: query.keyword,
    }),
    undefined,
    { auth: true, silent: true },
  )
}

export function listSavedMarketAssets(assetType?: MarketAssetType) {
  return http.get<MarketAsset[]>(
    buildQueryPath(MARKET_SAVED_ASSETS_PATH, {
      assetType,
    }),
    undefined,
    { auth: true, silent: true },
  )
}

export function saveMarketAsset(assetType: MarketAssetType, sourceId: string) {
  return http.post<MarketAsset>(
    `${MARKET_ASSETS_PATH}/${encodeURIComponent(assetType)}/${encodeURIComponent(sourceId)}/save`,
    undefined,
    { auth: true, silent: true },
  )
}

export function removeSavedMarketAsset(assetType: MarketAssetType, sourceId: string) {
  return http.delete<void>(
    `${MARKET_ASSETS_PATH}/${encodeURIComponent(assetType)}/${encodeURIComponent(sourceId)}/save`,
    undefined,
    { auth: true, silent: true },
  )
}

export function getMarketAssetTitle(asset: MarketAsset) {
  return normalizeString(asset.title)
    ?? normalizeString(asset.source?.name)
    ?? asset.sourceId
}

export function getMarketAssetSummary(asset: MarketAsset) {
  return normalizeString(asset.summary)
    ?? normalizeString(asset.description)
    ?? normalizeString(asset.source?.description)
    ?? ''
}

export function getMarketAssetTags(asset: MarketAsset) {
  if (Array.isArray(asset.tags)) {
    return asset.tags.map((item) => String(item).trim()).filter(Boolean)
  }
  const raw = normalizeString(asset.tags)
  if (!raw) return []
  return raw.split(/[，,]/).map((item) => item.trim()).filter(Boolean)
}

export function resolveAgentAssetRuntimeId(asset: MarketAsset) {
  const installedAgentId = normalizeString(asset.extraConfig?.installedAgentId)
  return installedAgentId ?? asset.sourceId
}

export function agentAssetMatchesId(asset: MarketAsset, agentId: string | null | undefined) {
  const normalizedAgentId = normalizeString(agentId)
  if (!normalizedAgentId) return false
  return normalizedAgentId === asset.sourceId || normalizedAgentId === resolveAgentAssetRuntimeId(asset)
}

export function getMarketAssetSourceId(asset: MarketAsset) {
  return normalizeString(asset.source?.id) ?? asset.sourceId
}

export function getMarketAssetUsageMode(asset: MarketAsset) {
  return normalizeString(asset.usageMode)
    ?? normalizeString(asset.source?.usageMode)
    ?? null
}

export function getMarketAssetUsageInstruction(asset: MarketAsset) {
  return normalizeString(asset.aiUsageInstruction)
    ?? normalizeString(asset.source?.aiUsageInstruction)
    ?? normalizeString(asset.source?.aiUsageEntry?.instruction)
    ?? ''
}

export function getMarketAssetContentPreview(asset: MarketAsset) {
  return normalizeString(asset.source?.contentPreview)
    ?? normalizeString(asset.source?.content)
    ?? ''
}
