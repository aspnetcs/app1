import { PLATFORM_USER_ROUTE_CONTRACT } from './platformUserRouteContract'

const PLATFORM_AGENTS_PATH = PLATFORM_USER_ROUTE_CONTRACT.agents

export const PLATFORM_AGENT_ROUTE_CONTRACT = {
  market: PLATFORM_AGENTS_PATH,
} as const

export function buildAgentMarketPath(category?: string) {
  if (!category) return PLATFORM_AGENT_ROUTE_CONTRACT.market
  return `${PLATFORM_AGENT_ROUTE_CONTRACT.market}?category=${encodeURIComponent(category)}`
}

function buildAgentResourcePath(agentId: string) {
  return `${PLATFORM_AGENTS_PATH}/${encodeURIComponent(agentId)}`
}

export function buildAgentInstallPath(agentId: string) {
  return `${buildAgentResourcePath(agentId)}/install`
}
