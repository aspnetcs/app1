import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { FontScale, UserPreferences } from '@/api/preferences'
import {
  applyNativeThemeSync,
  resolveCodeThemeMode,
  resolveSystemTheme,
  resolveThemeMode,
  type ResolvedTheme,
} from '@/utils/native-theme-sync'

type ThemeSyncState = Pick<UserPreferences, 'themeMode' | 'codeTheme' | 'fontScale' | 'spacingVertical' | 'spacingHorizontal'>

function normalizeSnapshot(snapshot?: Partial<ThemeSyncState> | null): ThemeSyncState {
  return {
    themeMode: snapshot?.themeMode ?? 'system',
    codeTheme: snapshot?.codeTheme ?? 'system',
    fontScale: snapshot?.fontScale ?? 'md',
    spacingVertical: snapshot?.spacingVertical ?? '16px',
    spacingHorizontal: snapshot?.spacingHorizontal ?? '16px',
  }
}

export const useThemeStore = defineStore('theme', () => {
  const systemTheme = ref<ResolvedTheme>(resolveSystemTheme())
  const preferenceThemeMode = ref<ThemeSyncState['themeMode']>('system')
  const preferenceCodeTheme = ref<ThemeSyncState['codeTheme']>('system')
  const fontScale = ref<FontScale>('md')
  const spacingVertical = ref<string>('16px')
  const spacingHorizontal = ref<string>('16px')
  const initialized = ref(false)

  const resolvedTheme = computed<ResolvedTheme>(() =>
    resolveThemeMode(preferenceThemeMode.value, systemTheme.value),
  )

  const resolvedCodeTheme = computed<ResolvedTheme>(() =>
    resolveCodeThemeMode(preferenceCodeTheme.value, resolvedTheme.value),
  )

  function applyCurrentTheme() {
    applyNativeThemeSync({
      theme: resolvedTheme.value,
      codeTheme: resolvedCodeTheme.value,
      fontScale: fontScale.value,
      spacingVertical: spacingVertical.value,
      spacingHorizontal: spacingHorizontal.value,
    })
  }

  function setSystemTheme(nextTheme: ResolvedTheme) {
    systemTheme.value = nextTheme
    applyCurrentTheme()
  }

  function syncFromPreferences(snapshot?: Partial<ThemeSyncState> | null) {
    const next = normalizeSnapshot(snapshot)
    preferenceThemeMode.value = next.themeMode
    preferenceCodeTheme.value = next.codeTheme
    fontScale.value = next.fontScale
    spacingVertical.value = next.spacingVertical || '16px'
    spacingHorizontal.value = next.spacingHorizontal || '16px'
    applyCurrentTheme()
  }

  function start() {
    if (initialized.value) {
      applyCurrentTheme()
      return
    }

    initialized.value = true
    setSystemTheme(resolveSystemTheme())

    // #ifdef H5
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      const media = window.matchMedia('(prefers-color-scheme: dark)')
      const handleChange = (event: MediaQueryListEvent | MediaQueryList) => {
        setSystemTheme(event.matches ? 'dark' : 'light')
      }

      if (typeof media.addEventListener === 'function') {
        media.addEventListener('change', handleChange)
      } else if (typeof media.addListener === 'function') {
        media.addListener(handleChange)
      }
    }
    // #endif

    const uniAny = uni as typeof uni & {
      onThemeChange?: (callback: (payload: { theme?: string }) => void) => void
    }

    if (typeof uniAny.onThemeChange === 'function') {
      uniAny.onThemeChange((payload) => {
        setSystemTheme(payload?.theme === 'dark' ? 'dark' : 'light')
      })
    }
  }

  return {
    initialized,
    systemTheme,
    resolvedTheme,
    resolvedCodeTheme,
    fontScale,
    start,
    syncFromPreferences,
    setSystemTheme,
  }
})
