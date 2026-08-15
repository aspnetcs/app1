/**
 * Navigation metrics for custom navigation bars.
 * On mini-programs, align content to the native capsule rect and reserve
 * the right-side capsule area. On other platforms, fall back to status bar.
 */
import { ref } from 'vue'

type SystemInfoSnapshot = {
  statusBarHeight?: number
  windowWidth?: number
  screenWidth?: number
}

type MenuButtonRectLike = {
  top?: number
  bottom?: number
  height?: number
  left?: number
}

export type NavigationMetrics = {
  statusBarHeight: number
  navigationBarHeight: number
  navigationContentTop: number
  navigationContentHeight: number
  navigationRightInset: number
  hasCapsule: boolean
}

const statusBarHeight = ref(0)
const navigationBarHeight = ref(0)
const navigationContentTop = ref(0)
const navigationContentHeight = ref(56)
const navigationRightInset = ref(16)
const hasCapsule = ref(false)
let resolved = false

export function resolveNavigationMetrics(
  sysInfo?: SystemInfoSnapshot,
  menuButton?: MenuButtonRectLike | null,
): NavigationMetrics {
  const topInset = Math.max(0, Number(sysInfo?.statusBarHeight ?? 0))
  const windowWidth = Math.max(
    0,
    Number(sysInfo?.windowWidth ?? sysInfo?.screenWidth ?? 0),
  )

  const fallback: NavigationMetrics = {
    statusBarHeight: topInset,
    navigationBarHeight: topInset > 0 ? topInset + 56 : 0,
    navigationContentTop: topInset,
    navigationContentHeight: 56,
    navigationRightInset: 16,
    hasCapsule: false,
  }

  if (
    !menuButton
    || typeof menuButton.top !== 'number'
    || typeof menuButton.bottom !== 'number'
    || menuButton.bottom <= 0
    || menuButton.top < 0
  ) {
    return fallback
  }

  const gap = Math.max(menuButton.top - topInset, 4)
  const rightInset =
    windowWidth > 0 && typeof menuButton.left === 'number'
      ? Math.max(16, windowWidth - menuButton.left + 12)
      : 16

  return {
    statusBarHeight: topInset,
    navigationBarHeight: menuButton.bottom + gap,
    navigationContentTop: menuButton.top,
    navigationContentHeight: Math.max(32, Number(menuButton.height ?? menuButton.bottom - menuButton.top)),
    navigationRightInset: rightInset,
    hasCapsule: true,
  }
}

export function useStatusBar() {
  if (!resolved) {
    resolved = true
    try {
      const sysInfo = uni.getSystemInfoSync()
      let metrics = resolveNavigationMetrics(sysInfo, null)
      try {
        const menuButton = (uni as any).getMenuButtonBoundingClientRect?.()
        metrics = resolveNavigationMetrics(sysInfo, menuButton)
      } catch {
        // H5/App do not expose the capsule API.
      }

      statusBarHeight.value = metrics.statusBarHeight
      navigationBarHeight.value = metrics.navigationBarHeight
      navigationContentTop.value = metrics.navigationContentTop
      navigationContentHeight.value = metrics.navigationContentHeight
      navigationRightInset.value = metrics.navigationRightInset
      hasCapsule.value = metrics.hasCapsule
    } catch {
      const metrics = resolveNavigationMetrics()
      statusBarHeight.value = metrics.statusBarHeight
      navigationBarHeight.value = metrics.navigationBarHeight
      navigationContentTop.value = metrics.navigationContentTop
      navigationContentHeight.value = metrics.navigationContentHeight
      navigationRightInset.value = metrics.navigationRightInset
      hasCapsule.value = metrics.hasCapsule
    }
  }

  return {
    statusBarHeight,
    navigationBarHeight,
    navigationContentTop,
    navigationContentHeight,
    navigationRightInset,
    hasCapsule,
  }
}
