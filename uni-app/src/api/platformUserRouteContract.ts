const PLATFORM_API_BASE_PATH = '/v1'

function normalizePath(path: string) {
  return path.trim().replace(/^\/+|\/+$/g, '')
}

function joinPath(basePath: string, path: string) {
  const normalizedPath = normalizePath(path)
  if (!normalizedPath) return basePath
  return `${basePath}/${normalizedPath}`
}

export function buildPlatformApiPath(path: string) {
  return joinPath(PLATFORM_API_BASE_PATH, path)
}





export const PLATFORM_USER_ROUTE_CONTRACT = {
  bannersActive: buildPlatformApiPath('banners/active'),
  groupsMe: buildPlatformApiPath('groups/me'),
  followUpSuggestions: buildPlatformApiPath('follow-up/suggestions'),
  models: buildPlatformApiPath('models'),
  agents: buildPlatformApiPath('agents'),
} as const


