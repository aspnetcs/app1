import { getStorage, setStorage } from '@/utils/storage'

export interface TranslationLanguageOption {
  value: string
  label: string
  aliases?: string[]
}

export interface TranslationPreferences {
  targetLanguage: string
}

const TRANSLATION_STORAGE_KEY = 'feature.translation.preferences'

export const TRANSLATION_MAX_INPUT_CHARS = 8000
export const DEFAULT_TRANSLATION_TARGET_LANGUAGE = 'English'

export const TRANSLATION_LANGUAGE_OPTIONS: TranslationLanguageOption[] = [
  { value: 'English', label: '英文', aliases: ['en', 'en-us'] },
  { value: 'Simplified Chinese', label: '简体中文', aliases: ['zh', 'zh-cn', 'chinese'] },
  { value: 'Traditional Chinese', label: '繁体中文', aliases: ['zh-tw'] },
  { value: 'Japanese', label: '日文', aliases: ['ja', 'ja-jp'] },
  { value: 'Korean', label: '韩文', aliases: ['ko', 'ko-kr'] },
  { value: 'French', label: '法文', aliases: ['fr', 'fr-fr'] },
  { value: 'German', label: '德文', aliases: ['de', 'de-de'] },
  { value: 'Spanish', label: '西班牙文', aliases: ['es', 'es-es'] },
  { value: 'Russian', label: '俄文', aliases: ['ru', 'ru-ru'] },
  { value: 'Portuguese', label: '葡萄牙文', aliases: ['pt', 'pt-pt'] },
]

export function findTranslationLanguageOption(value: unknown): TranslationLanguageOption | null {
  const normalized = typeof value === 'string' ? value.trim().toLowerCase() : ''
  if (!normalized) return null
  return (
    TRANSLATION_LANGUAGE_OPTIONS.find((option) => {
      if (option.value.toLowerCase() === normalized) return true
      if (option.label.toLowerCase() === normalized) return true
      return option.aliases?.some((alias) => alias.toLowerCase() === normalized)
    }) ?? null
  )
}

export function normalizeTranslationTargetLanguage(value: unknown): string {
  const matched = findTranslationLanguageOption(value)
  if (matched) return matched.value
  const raw = typeof value === 'string' ? value.trim() : ''
  return raw || DEFAULT_TRANSLATION_TARGET_LANGUAGE
}

export function resolveTranslationLanguageLabel(value: unknown): string {
  const matched = findTranslationLanguageOption(value)
  if (matched) return matched.label
  const raw = typeof value === 'string' ? value.trim() : ''
  return raw || '未指定语言'
}

export function readTranslationPreferences(): TranslationPreferences {
  const stored = getStorage<Partial<TranslationPreferences>>(TRANSLATION_STORAGE_KEY, {}) ?? {}
  return {
    targetLanguage: normalizeTranslationTargetLanguage(stored.targetLanguage),
  }
}

export function writeTranslationPreferences(
  next: Partial<TranslationPreferences>,
): TranslationPreferences {
  const current = readTranslationPreferences()
  const resolved: TranslationPreferences = {
    targetLanguage: normalizeTranslationTargetLanguage(next.targetLanguage ?? current.targetLanguage),
  }
  setStorage(TRANSLATION_STORAGE_KEY, resolved)
  return resolved
}