import { http } from './http'
import { buildPlatformApiPath } from './platformUserRouteContract'

export interface MemoryRuntimeConfig {
  enabled: boolean
  consentRequired?: boolean
  consentGranted?: boolean
  summary?: string
}

export interface MemoryEntry {
  id: string
  content?: string
  summary?: string
  sourceType?: string
  status?: string
  updatedAt?: string | null
  createdAt?: string | null
}

const MEMORY_CONFIG_PATH = buildPlatformApiPath('memory/config')
const MEMORY_CONSENT_PATH = buildPlatformApiPath('memory/consent')
const MEMORY_ENTRIES_PATH = buildPlatformApiPath('memory/entries')

export function getMemoryRuntimeConfig() {
  return http.get<MemoryRuntimeConfig>(MEMORY_CONFIG_PATH, undefined, { auth: true, silent: true })
}

export function updateMemoryConsent(enabled: boolean) {
  return http.put<MemoryRuntimeConfig>(MEMORY_CONSENT_PATH, { enabled }, { auth: true, silent: true })
}

export function listMemoryEntries(limit = 10) {
  return http.get<MemoryEntry[]>(`${MEMORY_ENTRIES_PATH}?limit=${Math.max(1, limit)}`, undefined, {
    auth: true,
    silent: true,
  })
}

export function deleteMemoryEntry(entryId: string) {
  return http.delete<void>(`${MEMORY_ENTRIES_PATH}/${encodeURIComponent(entryId)}`, undefined, {
    auth: true,
    silent: true,
  })
}
