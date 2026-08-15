/**
 * 全局应用状态
 * - 系统信息 (statusBarHeight, windowHeight 等)
 * - 响应式断点 (screenType: wide / mid / narrow)
 * - 侧边栏控制
 */
import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import { logger } from '@/utils/logger'

export type ScreenType = 'wide' | 'mid' | 'narrow'

type SafeAreaLike = {
  top?: number
  bottom?: number
}

type SystemInfoSnapshot = {
  statusBarHeight?: number
  screenHeight?: number
  safeArea?: SafeAreaLike | null
  safeAreaInsets?: SafeAreaLike | null
}

// Safe-area insets should be small (home indicator / gesture bar height).
// Clamp aggressively to avoid layout blow-ups when a platform reports
// inconsistent units (e.g. rpx-like values) or bogus safeArea snapshots.
const MAX_SAFE_INSET_PX = 80

function normalizeSafeInset(value: unknown): number | null {
  const inset = Number(value)
  if (!Number.isFinite(inset)) return null
  if (inset < 0) return null
  if (inset > MAX_SAFE_INSET_PX) return null
  return Math.round(inset)
}

export function resolveSafeAreaInsets(systemInfo?: SystemInfoSnapshot) {
  const safeAreaInsets = systemInfo?.safeAreaInsets
  const safeArea = systemInfo?.safeArea
  const screenHeight = Math.max(0, Number(systemInfo?.screenHeight ?? 0))
  const statusBarHeight = Math.max(0, Number(systemInfo?.statusBarHeight ?? 0))

  if (safeAreaInsets) {
    const topInset = normalizeSafeInset(safeAreaInsets.top)
    const bottomInset = normalizeSafeInset(safeAreaInsets.bottom)
    return {
      top: Math.max(statusBarHeight, topInset ?? statusBarHeight),
      bottom: bottomInset ?? 0,
    }
  }

  if (safeArea && screenHeight > 0) {
    const topInset = normalizeSafeInset(safeArea.top)
    const safeBottom = Number(safeArea.bottom)
    const derivedBottomInset =
      Number.isFinite(safeBottom) && safeBottom > 0 && safeBottom <= screenHeight
        ? normalizeSafeInset(screenHeight - safeBottom) ?? 0
        : 0
    return {
      top: Math.max(statusBarHeight, topInset ?? statusBarHeight),
      bottom: derivedBottomInset,
    }
  }

  return {
    top: statusBarHeight,
    bottom: 0,
  }
}

export const useAppStore = defineStore('app', () => {
  const showSidebar = ref(false)
  const desktopSidebarExpanded = ref(true)
  const statusBarHeight = ref(0)
  const safeAreaTop = ref(0)
  const safeAreaBottom = ref(0)
  const systemInfo = shallowRef<UniApp.GetSystemInfoResult | null>(null)
  const wsConnected = ref(false)

  // ── 响应式断点 ──
  const screenWidth = ref(375) // 默认手机宽度
  const BREAKPOINT = 600;

  const isMobile = computed(() => screenWidth.value < BREAKPOINT)

  const screenType = computed<ScreenType>(() => {
    const w = screenWidth.value
    if (w >= 1024) return 'wide'
    if (w >= 600) return 'mid'
    return 'narrow'
  })

  const isWide = computed(() => screenType.value === 'wide')
  const isMid = computed(() => screenType.value === 'mid')
  const isNarrow = computed(() => screenType.value === 'narrow')

  function initSystemInfo() {
    try {
      const info = uni.getSystemInfoSync()
      systemInfo.value = info
      const safeAreaInsets = resolveSafeAreaInsets(info)
      statusBarHeight.value = info.statusBarHeight || 0
      safeAreaTop.value = safeAreaInsets.top
      safeAreaBottom.value = safeAreaInsets.bottom
      screenWidth.value = info.windowWidth || info.screenWidth || 375
    } catch (e) {
      logger.warn('[app] getSystemInfoSync failed:', e)
    }

    // H5 端：监听 window resize 实时更新断点
    // #ifdef H5
    if (typeof window !== 'undefined') {
      screenWidth.value = window.innerWidth
      const onResize = () => { screenWidth.value = window.innerWidth }
      window.addEventListener('resize', onResize)
      // 利用 Vue 生命周期清理（store 是全局单例，不需要清理）
    }
    // #endif
  }

  function toggleSidebar() {
    showSidebar.value = !showSidebar.value
    desktopSidebarExpanded.value = showSidebar.value
  }

  function closeSidebar() {
    showSidebar.value = false
  }

  return {
    showSidebar,
    desktopSidebarExpanded,
    statusBarHeight,
    safeAreaTop,
    safeAreaBottom,
    systemInfo,
    wsConnected,
    screenWidth,
    screenType,
    isMobile,
    isWide,
    isMid,
    isNarrow,
    initSystemInfo,
    toggleSidebar,
    closeSidebar,
  }
})
