import { http } from './http'
import { PLATFORM_TOOL_ROUTE_CONTRACT } from './platformToolRouteContract'

export interface ToolCatalogItem {
  name: string
  displayName: string
  description: string
  source: 'builtin' | 'mcp'
  serverName?: string
}

export interface ToolCatalogConfig {
  enabled: boolean
  featureKey: string
  maxSteps: number
  tools: ToolCatalogItem[]
}

export function getToolCatalog() {
  return http.get<ToolCatalogConfig>(PLATFORM_TOOL_ROUTE_CONTRACT.config, undefined, { auth: true, silent: true })
}
