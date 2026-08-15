import type { MarketAssetType } from '@/api/market'

export type MarketFilter = 'ALL' | MarketAssetType

export const MARKET_FILTER_OPTIONS: Array<{ value: MarketFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'AGENT', label: '智能体' },
  { value: 'KNOWLEDGE', label: '知识' },
  { value: 'MCP', label: 'MCP' },
  { value: 'SKILL', label: '技能' },
]

const ASSET_TYPE_LABELS: Record<MarketAssetType, string> = {
  AGENT: '智能体',
  KNOWLEDGE: '知识',
  MCP: 'MCP',
  SKILL: '技能',
}

export function getMarketAssetTypeLabel(assetType: MarketAssetType) {
  return ASSET_TYPE_LABELS[assetType]
}

export function getMarketCatalogEmptyCopy(filter: MarketFilter) {
  if (filter === 'KNOWLEDGE') {
    return '公共知识目录暂未开放，当前只展示你已经保存的知识资产。'
  }
  if (filter === 'SKILL') {
    return '技能目录暂未开放，当前不会伪造技能数据。'
  }
  if (filter === 'MCP') {
    return '暂时没有可展示的 MCP 目录项。你仍然可以在已保存区域管理已有服务。'
  }
  if (filter === 'AGENT') {
    return '暂时没有可展示的智能体目录项。'
  }
  return '暂时没有可展示的市场目录项。'
}

export function getSavedEmptyCopy(filter: MarketFilter) {
  if (filter === 'ALL') {
    return '你还没有保存任何市场资源。'
  }
  return `你还没有保存任何${filter === 'MCP' ? 'MCP 服务' : getMarketAssetTypeLabel(filter)}。`
}
