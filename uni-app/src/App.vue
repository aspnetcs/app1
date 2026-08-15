<script setup lang="ts">
import { watch } from 'vue'
import { onLaunch } from '@dcloudio/uni-app'
import { useAppStore } from './stores/app'
import { useAuthStore } from './stores/auth'
import { useThemeStore } from './stores/theme'
import { useUserPreferencesStore } from './stores/userPreferences'
import { config } from './config'

const appStore = useAppStore()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const userPreferencesStore = useUserPreferencesStore()

onLaunch(() => {
  appStore.initSystemInfo()
  themeStore.start()
  themeStore.syncFromPreferences(userPreferencesStore.preferences)
  void userPreferencesStore.loadPreferences().then((preferences) => {
    themeStore.syncFromPreferences(preferences)
  })
  // Proactively establish guest session on app launch if guest auth is enabled
  // and user is not logged in. This ensures token is available before any
  // authenticated API call (e.g., conversation creation) is made.
  if (config.features.guestAuth && !authStore.isLoggedIn) {
    authStore.ensureGuestSession().catch((error) => {
      console.error('[app] ensureGuestSession on launch failed:', error)
    })
  }
  // #ifdef H5
  if (typeof window !== 'undefined' && 'serviceWorker' in navigator && config.features.pwa) {
    window.addEventListener('beforeinstallprompt', (event: Event) => {
      event.preventDefault()
      const promptEvent = event as Event & { prompt?: () => Promise<void>; userChoice?: Promise<{ outcome: string }> }
      uni.showModal({
        title: '安装应用',
        content: '是否将 AI 助手安装到桌面？',
        success: async (res) => {
          if (res.confirm && promptEvent.prompt) {
            await promptEvent.prompt()
          }
        },
      })
    })
    navigator.serviceWorker.register('/sw.js').then((registration) => {
      if (registration.waiting) {
        uni.showModal({
          title: '发现新版本',
          content: '检测到新版本缓存，是否立即刷新？',
          success: (res) => {
            if (res.confirm) window.location.reload()
          },
        })
      }
      registration.addEventListener('updatefound', () => {
        uni.showToast({ title: 'PWA 缓存已更新，刷新后生效', icon: 'none' })
      })
    }).catch(() => {
      // ignore registration errors
    })
  }
  // #endif
})

watch(
  () => userPreferencesStore.preferences,
  (preferences) => {
    themeStore.syncFromPreferences(preferences)
  },
  { deep: true, immediate: true },
)
</script>

<style>
/*
 * Tailwind CSS 预编译产物 — 由 Tailwind CLI 生成
 * 小程序端: postcss.config.js + weapp-tailwindcss 处理原始 @tailwind 指令
 * H5 端:    Tailwind CLI --watch 预编译到 tailwind-compiled.css
 */
@import './styles/tailwind-compiled.css';

:root {
  --app-page-bg: #f7f7f8;
  --app-surface: #ffffff;
  --app-surface-muted: #f8fafc;
  --app-surface-raised: #ffffff;
  --app-text-primary: #111827;
  --app-text-secondary: #6b7280;
  --app-border-color: #e5e7eb;
  --app-border-color-soft: #f3f4f6;
  --app-fill-soft: #e5e7eb;
  --app-fill-hover: rgba(15, 23, 42, 0.06);
  --app-fill-hover-strong: rgba(148, 163, 184, 0.18);
  --app-overlay: rgba(15, 23, 42, 0.56);
  --app-accent: #2563eb;
  --app-accent-soft: #dbeafe;
  --app-accent-contrast: #1d4ed8;
  --app-accent-gradient: linear-gradient(90deg, #2563eb 0%, #38bdf8 100%);
  --app-neutral-strong: #111827;
  --app-neutral-strong-contrast: #ffffff;
  --app-neutral-muted: #e5e7eb;
  --app-neutral-muted-contrast: #374151;
  --app-tooltip-bg: rgba(17, 24, 39, 0.92);
  --app-tooltip-text: #ffffff;
  --app-danger: #dc2626;
  --app-danger-soft: #fef2f2;
  --app-danger-soft-hover: #fee2e2;
  --app-danger-contrast: #b91c1c;
  --app-danger-border: #fecaca;
  --app-warning-soft: #fff7ed;
  --app-warning-contrast: #c2410c;
  --app-warning-border: #fdba74;
  --app-success-soft: #dcfce7;
  --app-success-contrast: #15803d;
  --app-info-soft: #e0f2fe;
  --app-info-contrast: #075985;
  --app-shadow-elevated: 0 24px 48px -12px rgba(15, 23, 42, 0.25);
  --app-page-font-size: 14px;
  --app-space-vertical: 16px;
  --app-space-horizontal: 16px;
}

html[data-theme='dark'] {
  --app-page-bg: #050505;
  --app-surface: #0f0f10;
  --app-surface-muted: #151517;
  --app-surface-raised: #1b1b1e;
  --app-text-primary: #f5f5f5;
  --app-text-secondary: #a3a3a3;
  --app-border-color: #2a2a2e;
  --app-border-color-soft: #202024;
  --app-fill-soft: #2c2c31;
  --app-fill-hover: rgba(255, 255, 255, 0.06);
  --app-fill-hover-strong: rgba(255, 255, 255, 0.1);
  --app-overlay: rgba(0, 0, 0, 0.72);
  --app-accent: #60a5fa;
  --app-accent-soft: rgba(255, 255, 255, 0.08);
  --app-accent-contrast: #f3f4f6;
  --app-accent-gradient: linear-gradient(90deg, #60a5fa 0%, #38bdf8 100%);
  --app-neutral-strong: #e5e5e5;
  --app-neutral-strong-contrast: #0a0a0a;
  --app-neutral-muted: #252529;
  --app-neutral-muted-contrast: #d4d4d8;
  --app-tooltip-bg: rgba(0, 0, 0, 0.95);
  --app-tooltip-text: #fafafa;
  --app-danger: #f87171;
  --app-danger-soft: rgba(127, 29, 29, 0.36);
  --app-danger-soft-hover: rgba(153, 27, 27, 0.48);
  --app-danger-contrast: #fecaca;
  --app-danger-border: rgba(248, 113, 113, 0.32);
  --app-warning-soft: rgba(120, 53, 15, 0.4);
  --app-warning-contrast: #fdba74;
  --app-warning-border: rgba(251, 146, 60, 0.3);
  --app-success-soft: rgba(20, 83, 45, 0.42);
  --app-success-contrast: #86efac;
  --app-info-soft: rgba(255, 255, 255, 0.08);
  --app-info-contrast: #e2e8f0;
  --app-shadow-elevated: 0 24px 48px -18px rgba(0, 0, 0, 0.72);
}

html[data-font-scale='sm'] {
  --app-page-font-size: 13px;
}

html[data-font-scale='lg'] {
  --app-page-font-size: 15px;
}

/* Google Fonts 在小程序中不可用，仅通过 font-family fallback 使用系统字体 */

/* Lock root font-size to prevent uni-app H5 runtime dynamic scaling.
   Without this, all Tailwind rem-based classes scale proportionally. */
/* #ifdef H5 */
html {
  font-size: 16px !important;
  height: 100%;
}

body {
  height: 100%;
  font-size: var(--app-page-font-size);
  background-color: var(--app-page-bg);
  color: var(--app-text-primary);
  transition: background-color 160ms ease, color 160ms ease;
}

#app {
  min-height: 100%;
  font-size: inherit;
  background-color: var(--app-page-bg);
  color: var(--app-text-primary);
}
/* #endif */

page {
  height: 100%;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', 'Microsoft YaHei', sans-serif;
  font-size: 14px;
  color: var(--app-text-primary);
  background-color: var(--app-page-bg);
  -webkit-font-smoothing: antialiased;
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
  transition: background-color 160ms ease, color 160ms ease;
}

/* --- 多端响应式基础框架 (`multi-platform` 规范) --- */
.page-container {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  min-height: 100vh;
  background-color: var(--app-surface);
  position: relative;
  color: var(--app-text-primary);
}

/* 当屏幕比手机大时，给出阴影边界感 */
@media screen and (min-width: 600px) {
  .page-container {
    box-shadow: 0 4px 24px rgba(15, 23, 42, 0.08);
    min-height: calc(100vh - 40px);
    margin-top: 20px;
    margin-bottom: 20px;
    border-radius: 12px;
    overflow: hidden;
  }
}

/* 按钮基础动画与悬停 */
button {
  transition: transform 120ms ease, opacity 150ms ease, filter 150ms ease, background-color 150ms ease;
}
button:active {
  transform: scale(0.985);
}
button[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
}
button::after {
  border: none;
}
@media screen and (min-width: 600px) {
  button:hover {
    filter: brightness(0.95);
    cursor: pointer;
  }
}

/* #ifdef H5 */
/* 全局 scrollbar 美化 (H5) */
::-webkit-scrollbar {
  width: 4px;
}

::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.45);
  border-radius: 4px;
}
/* #endif */



/* Motion primitives */
@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translate3d(0, 18px, 0); /* 固定为 px，避免大屏幕偏差 */
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

/* prefers-reduced-motion: 小程序不支持 @media + * 选择器，已移除 */
/* -- 认证页面公共样式 (change-password 等页面使用) -- */
.auth-page {
  padding: 20px 24px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--app-surface);
  box-sizing: border-box;
}
.auth-page-title { margin-top: 10px; margin-bottom: 30px; }
.auth-title-line { display: block; font-size: 24px; font-weight: 600; color: var(--app-text-primary); line-height: 1.4; }
.auth-subtitle { display: block; font-size: 14px; color: var(--app-text-secondary); margin-bottom: 20px; }
.auth-input-error { font-size: 12px; color: #ff4d4f; margin-top: 2px; padding-left: 4px; display: block; }
.auth-get-code-btn { font-size: 14px; color: #4c7cf6; white-space: nowrap; margin-left: 8px; font-weight: 500; height: 22px; line-height: 22px; }
.auth-get-code-btn-disabled { color: #ccc; }
.auth-action-section { margin-top: 10px; margin-bottom: 16px; }
.auth-btn-login { width: 100%; height: 48px; line-height: 48px; text-align: center; background-color: #a8c0f9; color: #ffffff; font-size: 17px; font-weight: 500; border-radius: 24px; border: none; padding: 0; }
.auth-btn-login::after { border: none; }
.auth-btn-active { background-color: #4c7cf6; }
.auth-switch-row { display: flex; align-items: center; margin-bottom: 20px; }
.auth-switch-link { font-size: 14px; color: #4c7cf6; }
.auth-footer { margin-top: auto; text-align: center; padding-bottom: 20px; }
.auth-footer-text { font-size: 12px; color: #bbb; }
</style>
