import { getStorage } from '@/utils/storage'

interface HttpAuthStore {
  token: string | null
  refreshToken: string | null
  guestRecoveryToken: string | null
  isGuest: boolean
  refreshAccessToken?: () => Promise<string>
  ensureGuestSession?: () => Promise<string>
  recoverGuestSession?: () => Promise<string>
  clearAuth?: (options?: { clearGuestRecoveryToken?: boolean }) => void
}

interface RefreshQueueEntry {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

let authStoreGetter: (() => HttpAuthStore | null | undefined) | null = null
let isRefreshing = false
let refreshQueue: RefreshQueueEntry[] = []

export function setAuthStoreGetter(getter: () => HttpAuthStore | null | undefined) {
  authStoreGetter = getter
}

function getAuthStore(): HttpAuthStore | null {
  return authStoreGetter?.() ?? null
}

const STORAGE_MISS = Symbol('storage_miss')

function readStoredString(key: string): string | null {
  // The project storage helper always JSON-encodes values; use it as the primary contract.
  // Keep a defensive fallback for legacy deployments that wrote raw string values.
  const parsed = getStorage<unknown | typeof STORAGE_MISS>(key, STORAGE_MISS)
  if (parsed !== STORAGE_MISS) {
    if (typeof parsed === 'string') {
      const trimmed = parsed.trim()
      return trimmed ? trimmed : null
    }
    return null
  }

  try {
    const raw = uni.getStorageSync(key)
    if (typeof raw !== 'string') return null
    const trimmed = raw.trim()
    return trimmed ? trimmed : null
  } catch {
    return null
  }
}

function readStoredFlag(key: string): boolean {
  try {
    return !!uni.getStorageSync(key)
  } catch {
    return false
  }
}

function getToken(): string | null {
  return getAuthStore()?.token || readStoredString('token')
}

function getGuestRecoveryToken(): string | null {
  return getAuthStore()?.guestRecoveryToken || readStoredString('guestRecoveryToken')
}

function isGuestSession(): boolean {
  const authStore = getAuthStore()
  if (authStore) {
    return authStore.isGuest
  }
  return readStoredFlag('guestSession')
}

function processRefreshQueue(token: string | null, error?: unknown) {
  refreshQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
      return
    }
    if (token) {
      resolve(token)
    } else {
      reject(new Error('Missing refreshed token'))
    }
  })
  refreshQueue = []
}

async function refreshAccessToken(): Promise<string> {
  const authStore = getAuthStore()
  if (!authStore?.refreshToken || !authStore.refreshAccessToken) {
    throw new Error('No refresh token')
  }

  if (isRefreshing) {
    return new Promise((resolve, reject) => {
      refreshQueue.push({ resolve, reject })
    })
  }

  isRefreshing = true
  try {
    const nextToken = await authStore.refreshAccessToken()
    processRefreshQueue(nextToken)
    return nextToken
  } catch (error) {
    processRefreshQueue(null, error)
    throw error
  } finally {
    isRefreshing = false
  }
}

export function buildRequestHeaders(
  auth: boolean,
  headers: Record<string, string>,
): Record<string, string> {
  const header: Record<string, string> = {
    'Content-Type': 'application/json',
    ...headers,
  }

  const token = getToken()
  if (auth) {
    if (token) {
      header.Authorization = 'Bearer ' + token
    }
  } else if (!header.Authorization && isGuestSession() && token) {
    header.Authorization = 'Bearer ' + token
  }

  if (!header['X-Guest-Recovery-Token']) {
    const recoveryToken = getGuestRecoveryToken()
    if (recoveryToken) {
      header['X-Guest-Recovery-Token'] = recoveryToken
    }
  }

  return header
}

export function recoverUnauthorizedRequest(guestAuthEnabled: boolean): Promise<string> | null {
  const authStore = getAuthStore()
  if (!authStore) {
    return null
  }

  if (authStore.isGuest && guestAuthEnabled && authStore.recoverGuestSession) {
    return authStore.recoverGuestSession()
  }

  if (!authStore.token && guestAuthEnabled && authStore.ensureGuestSession) {
    return authStore.ensureGuestSession()
  }

  if (authStore.refreshToken && authStore.refreshAccessToken) {
    return refreshAccessToken()
  }

  return null
}

export function clearCurrentAuth() {
  getAuthStore()?.clearAuth?.()
}
