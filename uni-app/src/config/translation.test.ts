import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DEFAULT_TRANSLATION_TARGET_LANGUAGE,
  findTranslationLanguageOption,
  normalizeTranslationTargetLanguage,
  readTranslationPreferences,
  resolveTranslationLanguageLabel,
  writeTranslationPreferences,
} from './translation'

describe('translation config helpers', () => {
  const storage = new Map<string, string>()

  beforeEach(() => {
    storage.clear()
    ;(globalThis as Record<string, unknown>).uni = {
      getStorageSync: vi.fn((key: string) => storage.get(key) ?? ''),
      setStorageSync: vi.fn((key: string, value: string) => {
        storage.set(key, value)
      }),
      removeStorageSync: vi.fn((key: string) => {
        storage.delete(key)
      }),
    }
  })

  it('normalizes aliases into canonical language values', () => {
    expect(normalizeTranslationTargetLanguage('en')).toBe('English')
    expect(normalizeTranslationTargetLanguage('zh-cn')).toBe('Simplified Chinese')
    expect(findTranslationLanguageOption('ja-jp')?.value).toBe('Japanese')
  })

  it('resolves readable labels for supported languages', () => {
    expect(resolveTranslationLanguageLabel('French')).toBe('法文')
    expect(resolveTranslationLanguageLabel('ko')).toBe('韩文')
    expect(resolveTranslationLanguageLabel('Custom Language')).toBe('Custom Language')
  })

  it('reads and writes persisted translation preferences', () => {
    expect(readTranslationPreferences()).toEqual({
      targetLanguage: DEFAULT_TRANSLATION_TARGET_LANGUAGE,
    })

    const written = writeTranslationPreferences({ targetLanguage: 'ja' })

    expect(written).toEqual({ targetLanguage: 'Japanese' })
    expect(readTranslationPreferences()).toEqual({ targetLanguage: 'Japanese' })
  })
})