import type { LoginResponse, UserInfo } from '@/api/types'
import { getStorage, removeStorage, setStorage } from '@/utils/storage'
import { GUEST_RECOVERY_TOKEN_STORAGE_KEY, GUEST_STORAGE_KEY } from './authGuestSession'

const TOKEN_STORAGE_KEY = 'token'
const REFRESH_TOKEN_STORAGE_KEY = 'refreshToken'
const USER_INFO_STORAGE_KEY = 'userInfo'

export interface StoredAuthSessionSnapshot {
  token: string | null
  refreshToken: string | null
  userInfo: UserInfo | null
  guestSession: boolean
  guestRecoveryToken: string | null
}

interface PersistedAuthSessionSnapshot {
  accessToken: string
  refreshToken: string | null
  userInfo: UserInfo | null
  guestRecoveryToken: string | null
}

export function readStoredAuthSession(): StoredAuthSessionSnapshot {
  const token = getStorage<string>(TOKEN_STORAGE_KEY) || null
  const refreshToken = getStorage<string>(REFRESH_TOKEN_STORAGE_KEY) || null
  const userInfo = getStorage<UserInfo>(USER_INFO_STORAGE_KEY) || null
  const guestRecoveryToken = getStorage<string>(GUEST_RECOVERY_TOKEN_STORAGE_KEY) || null

  if (!token) {
    removeStorage(GUEST_STORAGE_KEY)
    return {
      token: null,
      refreshToken,
      userInfo,
      guestSession: false,
      guestRecoveryToken,
    }
  }

  return {
    token,
    refreshToken,
    userInfo,
    guestSession: !!getStorage<boolean>(GUEST_STORAGE_KEY),
    guestRecoveryToken,
  }
}

export function writeStoredAuthSession(
  data: LoginResponse,
  isGuestSession: boolean,
  currentGuestRecoveryToken: string | null,
): PersistedAuthSessionSnapshot {
  const accessToken = data.accessToken || data.token
  const refreshToken = data.refreshToken || null
  const userInfo = data.userInfo || null
  const guestRecoveryToken = isGuestSession
    ? data.guestRecoveryToken || currentGuestRecoveryToken || null
    : null

  setStorage(TOKEN_STORAGE_KEY, accessToken)

  if (refreshToken) {
    setStorage(REFRESH_TOKEN_STORAGE_KEY, refreshToken)
  } else {
    removeStorage(REFRESH_TOKEN_STORAGE_KEY)
  }

  if (userInfo) {
    setStorage(USER_INFO_STORAGE_KEY, userInfo)
  } else {
    removeStorage(USER_INFO_STORAGE_KEY)
  }

  if (isGuestSession) {
    setStorage(GUEST_STORAGE_KEY, true)
    if (guestRecoveryToken) {
      setStorage(GUEST_RECOVERY_TOKEN_STORAGE_KEY, guestRecoveryToken)
    } else {
      removeStorage(GUEST_RECOVERY_TOKEN_STORAGE_KEY)
    }
  } else {
    removeStorage(GUEST_STORAGE_KEY)
    removeStorage(GUEST_RECOVERY_TOKEN_STORAGE_KEY)
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    guestRecoveryToken,
  }
}

export function writeStoredRefreshSession(
  data: LoginResponse,
  isGuestSession: boolean,
): PersistedAuthSessionSnapshot {
  const accessToken = data.accessToken || data.token
  const refreshToken = data.refreshToken || null
  const userInfo = data.userInfo || null

  setStorage(TOKEN_STORAGE_KEY, accessToken)

  if (refreshToken) {
    setStorage(REFRESH_TOKEN_STORAGE_KEY, refreshToken)
  }

  if (userInfo) {
    setStorage(USER_INFO_STORAGE_KEY, userInfo)
  }

  if (isGuestSession) {
    setStorage(GUEST_STORAGE_KEY, true)
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    guestRecoveryToken: getStorage<string>(GUEST_RECOVERY_TOKEN_STORAGE_KEY) || null,
  }
}

export function writeStoredUserInfo(userInfo: UserInfo) {
  setStorage(USER_INFO_STORAGE_KEY, userInfo)
}

export function clearStoredUserInfo() {
  removeStorage(USER_INFO_STORAGE_KEY)
}

export function clearStoredGuestRecoveryToken() {
  removeStorage(GUEST_RECOVERY_TOKEN_STORAGE_KEY)
}

export function clearStoredAuthSession(options: { clearGuestRecoveryToken?: boolean } = {}) {
  removeStorage(TOKEN_STORAGE_KEY)
  removeStorage(REFRESH_TOKEN_STORAGE_KEY)
  removeStorage(USER_INFO_STORAGE_KEY)
  removeStorage(GUEST_STORAGE_KEY)
  if (options.clearGuestRecoveryToken) {
    removeStorage(GUEST_RECOVERY_TOKEN_STORAGE_KEY)
  }
}
