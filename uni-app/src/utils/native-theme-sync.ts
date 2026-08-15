import type { CodeTheme, FontScale, ThemeMode } from '@/api/preferences'

export type ResolvedTheme = 'light' | 'dark'

export type ThemeSnapshot = {
  themeMode?: ThemeMode
  codeTheme?: CodeTheme
  fontScale?: FontScale
}

const FONT_SIZE_BY_SCALE: Record<FontScale, string> = {
  sm: '13px',
  md: '14px',
  lg: '15px',
}

export function resolveSystemTheme(): ResolvedTheme {
  try {
    const systemInfo = typeof uni !== 'undefined' && typeof uni.getSystemInfoSync === 'function'
      ? uni.getSystemInfoSync()
      : null
    if (systemInfo?.theme === 'dark' || systemInfo?.hostTheme === 'dark') {
      return 'dark'
    }
  } catch {
    // ignore environment lookup failures
  }

  if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }

  return 'light'
}

export function resolveThemeMode(mode: ThemeMode | null | undefined, systemTheme: ResolvedTheme): ResolvedTheme {
  if (mode === 'dark') {
    return 'dark'
  }
  if (mode === 'light') {
    return 'light'
  }
  return systemTheme
}

export function resolveCodeThemeMode(
  codeTheme: CodeTheme | null | undefined,
  resolvedTheme: ResolvedTheme,
): ResolvedTheme {
  if (codeTheme === 'dark') {
    return 'dark'
  }
  if (codeTheme === 'light') {
    return 'light'
  }
  return resolvedTheme
}

export function applyNativeThemeSync(options: {
  theme: ResolvedTheme
  codeTheme: ResolvedTheme
  fontScale: FontScale
  spacingVertical: string
  spacingHorizontal: string
}): void {
  const { theme, codeTheme, fontScale, spacingVertical, spacingHorizontal } = options

  // #ifdef H5
  if (typeof document !== 'undefined') {
    const root = document.documentElement
    const body = document.body

    root.dataset.theme = theme
    root.dataset.codeTheme = codeTheme
    root.dataset.fontScale = fontScale
    root.classList.toggle('dark', theme === 'dark')
    root.style.setProperty('--app-page-font-size', FONT_SIZE_BY_SCALE[fontScale])
    root.style.setProperty('--app-space-vertical', spacingVertical || '16px')
    root.style.setProperty('--app-space-horizontal', spacingHorizontal || '16px')

    if (body) {
      body.dataset.theme = theme
      body.classList.toggle('dark', theme === 'dark')
    }
  }
  // #endif

  const frontColor = theme === 'dark' ? '#ffffff' : '#000000'
  const backgroundColor = theme === 'dark' ? '#050505' : '#f7f7f8'

  try {
    if (typeof uni.setNavigationBarColor === 'function') {
      uni.setNavigationBarColor({
        frontColor,
        backgroundColor,
        animation: {
          duration: 0,
          timingFunc: 'linear',
        },
      })
    }
  } catch {
    // ignore platforms that do not support navigation-bar sync
  }

  try {
    if (typeof uni.setBackgroundColor === 'function') {
      uni.setBackgroundColor({
        backgroundColor,
        backgroundColorTop: backgroundColor,
        backgroundColorBottom: backgroundColor,
      })
    }
  } catch {
    // ignore platforms that do not support background sync
  }
}
