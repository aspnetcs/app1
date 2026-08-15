<script setup lang="ts">
import { computed, ref } from 'vue'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'
import SettingsDialog from '@/components/SettingsDialog.vue'
import LoginDialog from '@/components/LoginDialog.vue'
import SetPasswordDialog from '@/components/SetPasswordDialog.vue'
import ArchiveConfirmDialog from '@/components/chat/ArchiveConfirmDialog.vue'
import { useAppLayoutShell } from './useAppLayoutShell'
import { useChatMode } from '@/composables/useChatMode'
import type { ChatMode } from '@/composables/useChatMode'
import { createCompatActionRunner } from '@/utils/h5EventCompat'

const CHAT_HOME_ROUTES = ['chat/pages/index/index', 'pages/index/index']

function isChatHomeRoute(route: string) {
  const normalized = String(route || '').replace(/^\/+/, '')
  return CHAT_HOME_ROUTES.some(candidate => normalized === candidate)
}

const isChatPage = computed(() => {
  try {
    const pages = getCurrentPages()
    if (pages.length > 0) {
      const current = pages[pages.length - 1]
      const route = (current as any).route || (current as any).__route__ || ''
      return isChatHomeRoute(route)
    }
  } catch {
    // fallback
  }
  // #ifdef H5
  if (typeof window !== 'undefined') {
    const hash = window.location?.hash || ''
    return hash === '#/' || hash === '#' || hash.includes('/chat/pages/index/index')
  }
  // #endif
  return false
})

const pageHeaderMeta = computed(() => {
  try {
    const pages = getCurrentPages()
    if (pages.length > 0) {
      const current = pages[pages.length - 1]
      const route = (current as any).route || (current as any).__route__ || ''
      if (route.includes('pages/translation/translation')) {
        return {
          title: '文本翻译',
          subtitle: '超长文本翻译',
        }
      }
    }
  } catch {
    // fallback
  }
  // #ifdef H5
  if (typeof window !== 'undefined' && window.location?.hash?.includes('/pages/translation/translation')) {
    return {
      title: '文本翻译',
      subtitle: '超长文本翻译',
    }
  }
  // #endif
  return {
    title: '',
    subtitle: '',
  }
})

const topbarPageHeaderMeta = computed(() => {
  try {
    const pages = getCurrentPages()
    if (pages.length > 0) {
      const current = pages[pages.length - 1]
      const route = (current as any).route || (current as any).__route__ || ''
      if (route.includes('pages/translation/translation')) {
        return {
          title: '\u6587\u672c\u7ffb\u8bd1',
          subtitle: '',
        }
      }
      if (route.includes('research/pages/index/index')) {
        return {
          title: '\u79d1\u7814\u52a9\u7406',
          subtitle: '',
        }
      }
      if (route.includes('market/pages/index/index')) {
        return {
          title: '\u5e02\u573a',
          subtitle: '',
        }
      }
    }
  } catch {
    // fallback
  }
  // #ifdef H5
  if (typeof window !== 'undefined') {
    const hash = window.location?.hash || ''
    if (hash.includes('/pages/translation/translation')) {
      return {
        title: '\u6587\u672c\u7ffb\u8bd1',
        subtitle: '',
      }
    }
    if (hash.includes('/research/pages/index/index')) {
      return {
        title: '\u79d1\u7814\u52a9\u7406',
        subtitle: '',
      }
    }
    if (hash.includes('/market/pages/index/index')) {
      return {
        title: '\u5e02\u573a',
        subtitle: '',
      }
    }
  }
  // #endif
  return pageHeaderMeta.value
})

const {
  chatStore,
  realModels,
  selectedModelId,
  isMobileLayout,
  isRailExpanded,
  isRailCollapsed,
  isOverlayOpen,
  sidebarTransitionEnabled,
  sidebarStyle,
  appShellStyle,
  showLoginButton,
  avatarText,
  newChat,
  startIncognitoChat,
  openConversationFromSidebar,
  openTranslationPage,
  openResearchPage,
  openMarketPage,
  openAccountPage: openAccountPageNav,
  openHistoryPage,
  toggleSidebar,
  closeSidebar,
} = useAppLayoutShell()
const runCompatAction = createCompatActionRunner()

const showSettingsDialog = ref(false)
const showLoginDialog = ref(false)
const showSetPasswordDialog = ref(false)

function onLoginSuccess(data: { needSetPassword: boolean }) {
  showLoginDialog.value = false
  if (data.needSetPassword) {
    setTimeout(() => {
      showSetPasswordDialog.value = true
    }, 1200)
  }
}

function openAccountPage() {
  if (!isMobileLayout.value) {
    showSettingsDialog.value = true
    return
  }
  openAccountPageNav()
}

function goLogin() {
  showLoginDialog.value = true
}

// Inject state provided by index.vue (the parent page)
const { chatMode, multiModelIds, captainMode } = useChatMode()

function onChatModeChange(mode: ChatMode) {
  chatMode.value = mode
  // Auto-init model list if switching to multi-mode with insufficient models
  if ((mode === 'compare' || mode === 'team') && multiModelIds.value.length < 2) {
    const ids = realModels.value.map(m => m.id)
    multiModelIds.value = mode === 'team' && ids.length >= 3
      ? ids.slice(0, 3)
      : ids.slice(0, 2)
  }
}

function handleSidebarToggle(event?: Event) {
  runCompatAction('app-layout-sidebar-toggle', event, () => {
    toggleSidebar()
  })
}

function handleSidebarBackdrop(event?: Event) {
  runCompatAction('app-layout-sidebar-backdrop', event, () => {
    closeSidebar()
  })
}
</script>

<template>
  <view class="app-shell" :class="{ 'is-mobile-layout': isMobileLayout }" :style="appShellStyle">
    <!-- #ifndef MP-WEIXIN -->
    <button class="sidebar-toggle-fixed" @click.stop="handleSidebarToggle" @tap.stop="handleSidebarToggle">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="#4b5563"
        stroke-width="2.2"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <line x1="4" y1="7" x2="20" y2="7"></line>
        <line x1="4" y1="12" x2="20" y2="12"></line>
        <line x1="4" y1="17" x2="20" y2="17"></line>
      </svg>
    </button>
    <!-- #endif -->

    <AppSidebar
      :is-overlay-open="isOverlayOpen"
      :sidebar-transition-enabled="sidebarTransitionEnabled"
      @open-history="openHistoryPage"
      @new-chat="newChat"
      @start-incognito="startIncognitoChat"
      @open-translation="openTranslationPage"
      @open-research="openResearchPage"
      @open-market="openMarketPage"
      @open-account="openAccountPage"
      @open-conversation="openConversationFromSidebar"
    />

    <view
      v-if="isOverlayOpen"
      class="sidebar-backdrop"
      @click="handleSidebarBackdrop"
      @tap="handleSidebarBackdrop"
    ></view>

    <view class="main-area">
      <AppTopbar
        v-model="selectedModelId"
        :real-models="realModels"
        :is-mobile-layout="isMobileLayout"
        :show-sidebar-toggle="isMobileLayout"
        :show-login-button="showLoginButton"
        :avatar-text="avatarText"
        :chat-mode="chatMode"
        :multi-model-ids="multiModelIds"
        :captain-mode="captainMode"
        :hide-chat-controls="!isChatPage"
        :page-title="topbarPageHeaderMeta.title"
        :page-subtitle="topbarPageHeaderMeta.subtitle"
        @toggle-sidebar="toggleSidebar"
        @go-login="goLogin"
        @open-account="openAccountPage"
        @update:chat-mode="onChatModeChange"
        @update:multi-model-ids="multiModelIds = $event"
        @update:captain-mode="captainMode = $event"
      >
        <template #page-header>
          <slot name="topbar-header"></slot>
        </template>
      </AppTopbar>

      <view class="main-content">
        <slot></slot>
      </view>
    </view>

    <SettingsDialog
      :visible="showSettingsDialog"
      @close="showSettingsDialog = false"
    />

    <LoginDialog
      :visible="showLoginDialog"
      @close="showLoginDialog = false"
      @login-success="onLoginSuccess"
    />

    <SetPasswordDialog
      :visible="showSetPasswordDialog"
      @close="showSetPasswordDialog = false"
    />

    <ArchiveConfirmDialog
      :visible="chatStore.archiveConfirmVisible"
      :name="chatStore.archiveConfirmConversationTitle"
      message="归档后可在设置页面中的“已归档”列表中恢复。"
      tip="归档不会删除内容，只会从当前列表移动到“已归档”列表。"
      :busy="chatStore.archiveConfirmBusy"
      @close="chatStore.closeArchiveConfirm"
      @confirm="chatStore.confirmArchiveFromDialog"
    />
  </view>
</template>

<style scoped src="./AppLayout.css"></style>
