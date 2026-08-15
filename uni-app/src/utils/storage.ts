import { logger } from '@/utils/logger'

export function setStorage(key: string, value: unknown): void {
  try {
    uni.setStorageSync(key, JSON.stringify(value))
  } catch (error) {
    logger.warn('[storage] setStorage failed:', key, error)
  }
}

export function getStorage<T = unknown>(key: string, defaultValue?: T): T | undefined {
  try {
    const raw = uni.getStorageSync(key)
    if (!raw) return defaultValue
    return JSON.parse(raw) as T
  } catch {
    return defaultValue
  }
}

export function removeStorage(key: string): void {
  try {
    uni.removeStorageSync(key)
  } catch (error) {
    logger.warn('[storage] removeStorage failed:', key, error)
  }
}
