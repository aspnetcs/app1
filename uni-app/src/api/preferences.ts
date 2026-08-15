import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

export type ThemeMode = 'light' | 'dark' | 'system'
export type CodeTheme = 'light' | 'dark' | 'system'
export type FontScale = 'sm' | 'md' | 'lg'
export type McpMode = 'disabled' | 'auto' | 'manual'

export interface UserPreferences {
  defaultAgentId: string | null
  themeMode: ThemeMode
  codeTheme: CodeTheme
  fontScale: FontScale
  mcpMode: McpMode
  preferredMcpServerId: string | null
  spacingVertical?: string
  spacingHorizontal?: string
}

const USER_PREFERENCES_PATH = buildPlatformApiPath('preferences')

export function getUserPreferences() {
  return http.get<UserPreferences>(USER_PREFERENCES_PATH, undefined, { auth: true, silent: true })
}

export function updateUserPreferences(payload: Partial<UserPreferences>) {
  return http.put<UserPreferences>(USER_PREFERENCES_PATH, payload, { auth: true, silent: true })
}
