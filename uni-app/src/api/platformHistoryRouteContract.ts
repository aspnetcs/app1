import { buildPlatformApiPath } from './platformUserRouteContract'

const PLATFORM_HISTORY_PATH = buildPlatformApiPath('history')

export const PLATFORM_HISTORY_ROUTE_CONTRACT = {
  topics: `${PLATFORM_HISTORY_PATH}/topics`,
  messages: `${PLATFORM_HISTORY_PATH}/messages`,
  files: `${PLATFORM_HISTORY_PATH}/files`,
} as const

export function buildHistorySearchPath(
  type: keyof typeof PLATFORM_HISTORY_ROUTE_CONTRACT,
  params: {
    keyword: string
    topicId?: string
    page?: number
    size?: number
  },
) {
  const query = new URLSearchParams()
  query.set('keyword', params.keyword.trim())
  query.set('page', String(params.page ?? 0))
  query.set('size', String(params.size ?? 20))
  if (params.topicId?.trim()) {
    query.set('topicId', params.topicId.trim())
  }
  return `${PLATFORM_HISTORY_ROUTE_CONTRACT[type]}?${query.toString()}`
}