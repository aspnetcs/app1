import type { ComputedRef } from 'vue'
import { config } from '@/config'

interface AppLayoutNavigationOptions {
  isMobileLayout: ComputedRef<boolean>
  closeSidebar: () => void
}

interface FeatureMenuHandlers {
  openHistoryPage: () => void
  openTranslationPage: () => void
  openResearchPage: () => void
  openMarketPage: () => void
  openAccountPage: () => void
}

export interface FeatureMenuEntry {
  label: string
  action: () => void
}

const NAV_TEXT = {
  history: '\u5386\u53f2',
  translation: '文本翻译',
  researchAssistant: '\u79d1\u7814\u52a9\u7406',
  agentMarket: '\u667a\u80fd\u4f53\u5546\u5e97',
  settings: '\u8bbe\u7f6e\u548c\u5e2e\u52a9',
  openFailed: '\u6253\u5f00\u5931\u8d25',
}

export function buildFeatureMenuEntries(handlers: FeatureMenuHandlers): FeatureMenuEntry[] {
  const entries: FeatureMenuEntry[] = [
    { label: NAV_TEXT.history, action: handlers.openHistoryPage },
    { label: NAV_TEXT.translation, action: handlers.openTranslationPage },
    { label: NAV_TEXT.settings, action: handlers.openAccountPage },
  ]

  if (!config.features.translation) {
    const index = entries.findIndex((entry) => entry.label === NAV_TEXT.translation)
    if (index >= 0) entries.splice(index, 1)
  }

  if (config.features.researchAssistant) {
    entries.splice(entries.length - 1, 0, {
      label: NAV_TEXT.researchAssistant,
      action: handlers.openResearchPage,
    })
  }

  if (config.features.agentMarket) {
    entries.splice(entries.length - 1, 0, {
      label: NAV_TEXT.agentMarket,
      action: handlers.openMarketPage,
    })
  }
  return entries
}

export function createAppLayoutNavigation(options: AppLayoutNavigationOptions) {
  const closeSidebarIfNeeded = () => {
    if (options.isMobileLayout.value) {
      options.closeSidebar()
    }
  }

  const safeNavigate = (url: string) => {
    const target = url.replace(/^\//, "").trim();
    const pages = getCurrentPages();
    const current = pages.length > 0 ? pages[pages.length - 1] : null;
    const currentRoute = (current as { route?: string } | null)?.route?.trim();

    const showNavigationError = () => {
      uni.showToast({ title: NAV_TEXT.openFailed, icon: "none" });
    };

    if (currentRoute === target) {
      if (options.isMobileLayout.value) {
        options.closeSidebar();
      }
      return;
    }

    const navigateNow = () => {
      if (pages.length === 0) {
        uni.reLaunch({ url, fail: showNavigationError });
      } else {
        uni.redirectTo({
          url,
          fail: () => {
            uni.reLaunch({ url, fail: showNavigationError });
          },
        });
      }
    };

    if (options.isMobileLayout.value) {
      // Close the drawer first, then navigate after a short delay so the transition starts.
      options.closeSidebar();
      setTimeout(navigateNow, 50);
      return;
    }

    navigateNow();
  };

  const openChatPage = () => safeNavigate('/chat/pages/index/index')
  const openHistoryPage = () => safeNavigate('/chat/pages/history/history')
  const openTranslationPage = () => safeNavigate('/pages/translation/translation')
  const openResearchPage = () => safeNavigate('/research/pages/index/index')
  const openMarketPage = () => safeNavigate('/market/pages/index/index')
  const openAccountPage = () => safeNavigate('/pages/account/account')
  const goLogin = () => {
    // Handled by AppLayout.vue which shows LoginDialog popup directly.
    // This no-op exists to satisfy the return contract.
  }
  const featureMenuEntries = buildFeatureMenuEntries({
    openHistoryPage,
    openTranslationPage,
    openResearchPage,
    openMarketPage,
    openAccountPage,
  })

  const openFeatureMenu = () => {
    uni.showActionSheet({
      itemList: featureMenuEntries.map((entry) => entry.label),
      success: ({ tapIndex }) => {
        featureMenuEntries[tapIndex]?.action()
      },
    })
  }

  return {
    openChatPage,
    openHistoryPage,
    openTranslationPage,
    openResearchPage,
    openMarketPage,
    openAccountPage,
    openFeatureMenu,
    goLogin,
  }
}

