<script setup lang="ts">
import { computed, ref } from 'vue'
import { config } from '@/config'
// #ifndef MP-WEIXIN
import IconEdit from '@/components/icons/IconEdit.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
import { useAppStore } from '@/stores/app'
import { useChatStore, type Conversation } from '@/stores/chat'
import { createCompatActionRunner } from '@/utils/h5EventCompat'

const RAIL_WIDTH_EXPANDED = 280
const RAIL_WIDTH_COLLAPSED = 68
const MOBILE_BREAKPOINT = 600

const SIDEBAR_TEXT = {
  newChat: '\u53d1\u8d77\u65b0\u5bf9\u8bdd',
  translation: '文本翻译',
  researchAssistant: '\u79d1\u7814\u52a9\u7406',
  agentMarket: '\u5e02\u573a',
  conversations: '\u5bf9\u8bdd',
  pinned: '\u7f6e\u9876',
  pin: '\u7f6e\u9876',
  unpin: '\u53d6\u6d88\u7f6e\u9876',
  rename: '\u91cd\u547d\u540d',
  archive: '\u5f52\u6863',
  archiveTitle: '\u5f52\u6863\u5bf9\u8bdd',
  archiveContent: '\u5f52\u6863\u540e\u53ef\u5728\u5386\u53f2\u9875\u7684\u201c\u5df2\u5f52\u6863\u201d\u5217\u8868\u4e2d\u6062\u590d\u3002',
  archived: '\u5df2\u5f52\u6863',
  archiveFailed: '\u5f52\u6863\u5931\u8d25',
  settings: '\u8bbe\u7f6e\u548c\u5e2e\u52a9',
  enterConversationName: '\u8bf7\u8f93\u5165\u5bf9\u8bdd\u540d\u79f0',
  renamed: '\u5df2\u91cd\u547d\u540d',
  renameFailed: '\u91cd\u547d\u540d\u5931\u8d25',
  pinFailed: '\u7f6e\u9876\u64cd\u4f5c\u5931\u8d25',
  renameDialogTitle: '\u91cd\u547d\u540d\u5bf9\u8bdd',
  renameDialogPlaceholder: '\u8f93\u5165\u65b0\u7684\u5bf9\u8bdd\u540d\u79f0',
  cancel: '\u53d6\u6d88',
  save: '\u4fdd\u5b58',
  saving: '\u4fdd\u5b58\u4e2d...',
}

const appStore = useAppStore()
const chatStore = useChatStore()

const hoveredConversationId = ref<string | null>(null)
const menuConversationId = ref<string | null>(null)
const menuPlacement = ref<'left' | 'right'>('right')
const menuStyle = ref<Record<string, string>>({})
const renameConversationId = ref<string | null>(null)
const renameValue = ref('')
const renameSaving = ref(false)
const runCompatAction = createCompatActionRunner()

const isMobileLayout = computed(() => appStore.screenWidth < MOBILE_BREAKPOINT)
const isRailExpanded = computed(() => !isMobileLayout.value && appStore.showSidebar)
const isRailCollapsed = computed(() => !isMobileLayout.value && !appStore.showSidebar)
const showResearchEntry = config.features.researchAssistant
const currentConversationId = computed(() => chatStore.currentConversationId)

const sidebarStyle = computed(() => {
  if (isMobileLayout.value) return undefined
  const width = isRailExpanded.value ? RAIL_WIDTH_EXPANDED : RAIL_WIDTH_COLLAPSED
  return { width: `${width}px` }
})

const sidebarHistory = computed(() => {
  return chatStore.history
})

const activeMenuConversation = computed(() =>
  sidebarHistory.value.find((conversation) => conversation.id === menuConversationId.value) ?? null,
)

function setHoveredConversation(id: string | null) {
  if (isMobileLayout.value) return
  hoveredConversationId.value = id
}

function isActionButtonVisible(conversationId: string) {
  return isMobileLayout.value
    || hoveredConversationId.value === conversationId
    || menuConversationId.value === conversationId
    || currentConversationId.value === conversationId
}

function closeActionMenu() {
  menuConversationId.value = null
  menuPlacement.value = 'right'
  menuStyle.value = {}
}

let resolveConversationActionAnchor: (conversationId: string, event?: Event) => HTMLElement | null = () => null
let openDesktopConversationActionMenu: (conversation: Conversation, event?: Event) => boolean = () => false

// #ifdef H5
function buildConversationActionSelector(conversationId: string) {
  if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
    return `[data-history-action="${CSS.escape(conversationId)}"]`
  }
  const escapedId = conversationId.replace(/\\/g, '\\\\').replace(/"/g, '\\"')
  return `[data-history-action="${escapedId}"]`
}

resolveConversationActionAnchor = (conversationId: string, event?: Event) => {
  const canUseDomElement = typeof Element !== 'undefined' && typeof HTMLElement !== 'undefined'
  if (canUseDomElement) {
    const candidates = [event?.currentTarget, event?.target]
    for (const candidate of candidates) {
      if (candidate instanceof Element) {
        const matched = candidate.closest('.history-item-more')
        if (matched instanceof HTMLElement) {
          return matched
        }
      }
    }
  }

  if (typeof document === 'undefined') {
    return null
  }

  const fallback = document.querySelector(buildConversationActionSelector(conversationId))
  return fallback instanceof HTMLElement ? fallback : null
}

openDesktopConversationActionMenu = (conversation: Conversation, event?: Event) => {
  const rect = resolveConversationActionAnchor(conversation.id, event)?.getBoundingClientRect()
  if (!rect) {
    return false
  }

  const menuWidth = 176
  const menuHeight = 126
  const gap = 10
  const viewportWidth = typeof window === 'undefined' ? rect.right + menuWidth + gap : window.innerWidth
  const viewportHeight = typeof window === 'undefined' ? rect.bottom + menuHeight + gap : window.innerHeight
  const canPlaceRight = rect.right + gap + menuWidth <= viewportWidth - 12
  const desiredLeft = canPlaceRight ? rect.right + gap : rect.left - menuWidth - gap
  const desiredTop = rect.top + rect.height / 2 - menuHeight / 2
  const left = Math.max(12, Math.min(desiredLeft, viewportWidth - menuWidth - 12))
  const top = Math.max(12, Math.min(desiredTop, viewportHeight - menuHeight - 12))
  const arrowTop = Math.max(20, Math.min(rect.top + rect.height / 2 - top, menuHeight - 20))

  menuConversationId.value = conversation.id
  menuPlacement.value = canPlaceRight ? 'right' : 'left'
  menuStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    '--history-action-arrow-top': `${arrowTop}px`,
  }
  return true
}
// #endif

function openRenameDialog(conversation: Conversation) {
  closeActionMenu()
  renameConversationId.value = conversation.id
  renameValue.value = conversation.title
}

function closeRenameDialog() {
  renameConversationId.value = null
  renameValue.value = ''
  renameSaving.value = false
}

async function confirmRename() {
  const conversationId = renameConversationId.value
  const title = renameValue.value.trim()
  if (!conversationId) return
  if (!title) {
    uni.showToast({ title: SIDEBAR_TEXT.enterConversationName, icon: 'none' })
    return
  }

  renameSaving.value = true
  try {
    await chatStore.renameConversation(conversationId, title)
    uni.showToast({ title: SIDEBAR_TEXT.renamed, icon: 'none' })
    closeRenameDialog()
  } catch (error) {
    renameSaving.value = false
    uni.showToast({
      title: error instanceof Error ? error.message : SIDEBAR_TEXT.renameFailed,
      icon: 'none',
    })
  }
}

async function handleTogglePinned(conversation: Conversation) {
  closeActionMenu()
  try {
    await chatStore.toggleConversationPinned(conversation.id, !conversation.pinned)
    uni.showToast({
      title: conversation.pinned ? SIDEBAR_TEXT.unpin : SIDEBAR_TEXT.pin,
      icon: 'none',
    })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : SIDEBAR_TEXT.pinFailed,
      icon: 'none',
    })
  }
}

function handleArchiveConversation(conversation: Conversation) {
  closeActionMenu()
  chatStore.openArchiveConfirm(conversation.id, conversation.title)
}

function handleRenameSaveClick() {
  if (renameSaving.value) return
  void confirmRename()
}

function openConversationActionMenu(conversation: Conversation, event?: Event) {
  if (!isMobileLayout.value && openDesktopConversationActionMenu(conversation, event)) {
    return
  }

  const itemList = [
    conversation.pinned ? SIDEBAR_TEXT.unpin : SIDEBAR_TEXT.pin,
    SIDEBAR_TEXT.rename,
    SIDEBAR_TEXT.archive,
  ]

  uni.showActionSheet({
    itemList,
    success: ({ tapIndex }) => {
      if (tapIndex === 0) {
        void handleTogglePinned(conversation)
        return
      }
      if (tapIndex === 1) {
        openRenameDialog(conversation)
        return
      }
      if (tapIndex === 2) {
        handleArchiveConversation(conversation)
      }
    },
  })
}

function handleConversationTap(conversationId: string) {
  closeActionMenu()
  emit('open-conversation', conversationId)
}

defineProps<{
  isOverlayOpen: boolean
  sidebarTransitionEnabled: boolean
}>()

const emit = defineEmits<{
  (e: 'open-history'): void
  (e: 'new-chat'): void
  (e: 'start-incognito'): void
  (e: 'open-translation'): void
  (e: 'open-research'): void
  (e: 'open-market'): void
  (e: 'open-account'): void
  (e: 'open-conversation', conversationId: string): void
}>()

function handleOpenHistory(event?: Event) {
  runCompatAction('sidebar-open-history', event, () => {
    emit('open-history')
  })
}

function handleNewChat(event?: Event) {
  runCompatAction('sidebar-new-chat', event, () => {
    emit('new-chat')
  })
}

function handleStartIncognito(event?: Event) {
  runCompatAction('sidebar-start-incognito', event, () => {
    emit('start-incognito')
  })
}

function handleOpenTranslation(event?: Event) {
  runCompatAction('sidebar-open-translation', event, () => {
    emit('open-translation')
  })
}

function handleOpenResearch(event?: Event) {
  runCompatAction('sidebar-open-research', event, () => {
    emit('open-research')
  })
}

function handleOpenMarket(event?: Event) {
  runCompatAction('sidebar-open-market', event, () => {
    emit('open-market')
  })
}

function handleOpenAccount(event?: Event) {
  runCompatAction('sidebar-open-account', event, () => {
    emit('open-account')
  })
}

function handleActionMenuClose(event?: Event) {
  runCompatAction('sidebar-action-menu-close', event, () => {
    closeActionMenu()
  })
}

let lastConvSwitchAt = 0
function handleConversationActivate(conversationId: string, _event?: Event) {
  const now = Date.now()
  if (now - lastConvSwitchAt < 100) return
  lastConvSwitchAt = now
  handleConversationTap(conversationId)
}

function handleConversationActionMenuOpen(conversation: Conversation, event?: Event) {
  runCompatAction(`sidebar-action-menu-open:${conversation.id}`, event, () => {
    openConversationActionMenu(conversation, event)
  })
}

function handlePinnedMenuAction(event?: Event) {
  const conversation = activeMenuConversation.value
  if (!conversation) return
  runCompatAction(`sidebar-action-menu-pin:${conversation.id}`, event, () => {
    void handleTogglePinned(conversation)
  })
}

function handleRenameMenuAction(event?: Event) {
  const conversation = activeMenuConversation.value
  if (!conversation) return
  runCompatAction(`sidebar-action-menu-rename:${conversation.id}`, event, () => {
    openRenameDialog(conversation)
  })
}

function handleArchiveMenuAction(event?: Event) {
  const conversation = activeMenuConversation.value
  if (!conversation) return
  runCompatAction(`sidebar-action-menu-archive:${conversation.id}`, event, () => {
    handleArchiveConversation(conversation)
  })
}

function handleRenameDialogClose(event?: Event) {
  runCompatAction('sidebar-rename-close', event, () => {
    closeRenameDialog()
  })
}

function handleRenameSaveAction(event?: Event) {
  runCompatAction(`sidebar-rename-save:${renameConversationId.value ?? 'unknown'}`, event, () => {
    handleRenameSaveClick()
  })
}

</script>

<template>
  <view
    class="sidebar"
    :class="{
      'sidebar-rail': !isMobileLayout,
      'sidebar-rail-expanded': isRailExpanded,
      'sidebar-rail-collapsed': isRailCollapsed,
      'sidebar-overlay': isMobileLayout,
      'sidebar-overlay-open': isOverlayOpen,
      'sidebar-no-transition': !sidebarTransitionEnabled,
    }"
    :style="sidebarStyle"
  >
    <view
      v-if="menuConversationId"
      class="history-action-overlay"
      @click="handleActionMenuClose"
      @tap="handleActionMenuClose"
    ></view>

    <view
      v-if="!isMobileLayout"
      class="sidebar-search-fixed"
      :class="{ 'sidebar-search-visible': isRailExpanded, 'sidebar-tooltip-target': isRailCollapsed }"
      :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.conversations : undefined"
      @click.stop="handleOpenHistory"
      @tap.stop="handleOpenHistory"
    >
      <!-- #ifdef MP-WEIXIN -->
      <MpShapeIcon name="search" :size="20" color="currentColor" />
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <IconSearch :size="20" color="currentColor" />
      <!-- #endif -->
    </view>

    <view class="sidebar-inner" :class="{ 'sidebar-inner-collapsed': isRailCollapsed }">
      <view
        class="sidebar-quick-actions"
        :class="{ 'sidebar-quick-actions-collapsed': isRailCollapsed }"
      >
        <view
          class="quick-new-chat-btn"
          :class="{ 'sidebar-tooltip-target': isRailCollapsed }"
          :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.newChat : undefined"
          @click.stop="handleNewChat"
          @tap.stop="handleNewChat"
        >
          <view class="quick-new-chat-icon-anchor">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="edit" :size="20" color="currentColor" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <IconEdit :size="20" color="currentColor" />
            <!-- #endif -->
          </view>
          <text class="quick-new-chat-text" :class="{ 'quick-new-chat-text-hidden': isRailCollapsed }">
            {{ SIDEBAR_TEXT.newChat }}
          </text>
        </view>
        <view
          class="quick-incognito-btn"
          :class="{ 'quick-incognito-btn-hidden': isRailCollapsed }"
          @click.stop="handleStartIncognito"
          @tap.stop="handleStartIncognito"
        >
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="crop" :size="20" color="currentColor" :stroke-width="2.2" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2.2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 9V4h5"></path>
            <path d="M20 9V4h-5"></path>
            <path d="M4 15v5h5"></path>
            <path d="M20 15v5h-5"></path>
          </svg>
          <!-- #endif -->
        </view>
      </view>

      <view class="sidebar-entry-links">
        <view
          v-if="config.features.translation"
          class="sidebar-entry-link"
          :class="{ 'sidebar-tooltip-target': isRailCollapsed }"
          :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.translation : undefined"
          @click.stop="handleOpenTranslation"
          @tap.stop="handleOpenTranslation"
        >
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="book" :size="20" color="currentColor" class="sidebar-entry-icon" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg
            class="sidebar-entry-icon"
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20"></path>
            <path d="M4 18.5A2.5 2.5 0 0 0 6.5 21H20"></path>
            <path d="M4 5.5V18.5"></path>
            <path d="M8 7H16"></path>
            <path d="M8 12H14"></path>
            <path d="M8 17H12"></path>
          </svg>
          <!-- #endif -->
          <text class="sidebar-entry-link-label">{{ SIDEBAR_TEXT.translation }}</text>
        </view>


        <view
          v-if="showResearchEntry"
          class="sidebar-entry-link"
          :class="{ 'sidebar-tooltip-target': isRailCollapsed }"
          :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.researchAssistant : undefined"
          @click.stop="handleOpenResearch"
          @tap.stop="handleOpenResearch"
        >
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="search" :size="20" color="currentColor" class="sidebar-entry-icon" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg
            class="sidebar-entry-icon"
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M9.5 4.5h5"></path>
            <path d="M7 7.5h10"></path>
            <path d="M8 3h8a2 2 0 0 1 2 2v14l-6-3-6 3V5a2 2 0 0 1 2-2z"></path>
          </svg>
          <!-- #endif -->
          <text class="sidebar-entry-link-label">{{ SIDEBAR_TEXT.researchAssistant }}</text>
        </view>

        <view
          v-if="config.features.agentMarket"
          class="sidebar-entry-link"
          :class="{ 'sidebar-tooltip-target': isRailCollapsed }"
          :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.agentMarket : undefined"
          @click.stop="handleOpenMarket"
          @tap.stop="handleOpenMarket"
        >
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="user" :size="20" color="currentColor" class="sidebar-entry-icon" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg
            class="sidebar-entry-icon"
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path d="M12 2a4 4 0 0 1 4 4v2H8V6a4 4 0 0 1 4-4z"></path>
            <rect x="3" y="8" width="18" height="14" rx="2"></rect>
            <circle cx="12" cy="16" r="2"></circle>
          </svg>
          <!-- #endif -->
          <text class="sidebar-entry-link-label">{{ SIDEBAR_TEXT.agentMarket }}</text>
        </view>
      </view>

      <view class="sidebar-section-label" :class="{ 'sidebar-section-label-hidden': isRailCollapsed }">
        <text>{{ SIDEBAR_TEXT.conversations }}</text>
      </view>

      <scroll-view scroll-y class="sidebar-history" :class="{ 'sidebar-history-hidden': isRailCollapsed }">
        <view
          v-for="item in sidebarHistory"
          :key="item.id"
          class="history-item"
          :class="{ 'history-item-active': item.id === currentConversationId }"
          @mouseenter="setHoveredConversation(item.id)"
          @mouseleave="setHoveredConversation(null)"
        >
          <view
            class="history-item-main"
            @mousedown.prevent
            @tap.stop="handleConversationActivate(item.id, $event)"
            @click.stop="handleConversationActivate(item.id, $event)"
          >
            <view class="history-item-title-row">
              <text class="history-item-title">{{ item.title }}</text>
              <text v-if="item.pinned" class="history-item-tag">{{ SIDEBAR_TEXT.pinned }}</text>
            </view>
          </view>

          <view
            class="history-item-actions"
            :class="{ 'history-item-actions-visible': isActionButtonVisible(item.id) }"
          >
            <view
              class="history-item-more"
              :data-history-action="item.id"
              @tap.stop.prevent="handleConversationActionMenuOpen(item, $event)"
              @click.stop.prevent="handleConversationActionMenuOpen(item, $event)"
            >
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="more" :size="18" color="currentColor" :stroke-width="2.2" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <svg
                xmlns="http://www.w3.org/2000/svg"
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="5" r="1.2"></circle>
                <circle cx="12" cy="12" r="1.2"></circle>
                <circle cx="12" cy="19" r="1.2"></circle>
              </svg>
              <!-- #endif -->
            </view>
          </view>
        </view>
      </scroll-view>

      <view
        v-if="activeMenuConversation"
        class="history-action-menu"
        :class="menuPlacement === 'left' ? 'history-action-menu-left' : 'history-action-menu-right'"
        :style="menuStyle"
        @click.stop
        @tap.stop
      >
        <view class="history-action-menu-row" @click.stop="handlePinnedMenuAction" @tap.stop="handlePinnedMenuAction">
          <text>{{ activeMenuConversation.pinned ? SIDEBAR_TEXT.unpin : SIDEBAR_TEXT.pin }}</text>
        </view>
        <view class="history-action-menu-row" @click.stop="handleRenameMenuAction" @tap.stop="handleRenameMenuAction">
          <text>{{ SIDEBAR_TEXT.rename }}</text>
        </view>
        <view
          class="history-action-menu-row history-action-menu-row-danger"
          @click.stop="handleArchiveMenuAction"
          @tap.stop="handleArchiveMenuAction"
        >
          <text>{{ SIDEBAR_TEXT.archive }}</text>
        </view>
      </view>

      <view class="sidebar-footer">
        <view
          class="sidebar-user"
          :class="{ 'sidebar-tooltip-target': isRailCollapsed }"
          :data-tooltip="isRailCollapsed ? SIDEBAR_TEXT.settings : undefined"
          @click.stop="handleOpenAccount"
          @tap.stop="handleOpenAccount"
        >
          <!-- #ifdef MP-WEIXIN -->
          <MpShapeIcon name="settings" :size="20" color="currentColor" class="sidebar-entry-icon" />
          <!-- #endif -->
          <!-- #ifndef MP-WEIXIN -->
          <svg
            class="sidebar-entry-icon"
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <circle cx="12" cy="12" r="3"></circle>
            <path
              d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68 1.65 1.65 0 0 0 10 3.17V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"
            ></path>
          </svg>
          <!-- #endif -->
          <text class="sidebar-user-name">{{ SIDEBAR_TEXT.settings }}</text>
        </view>
      </view>
    </view>

    <view
      v-if="renameConversationId"
      class="sidebar-dialog-backdrop"
      @click="handleRenameDialogClose"
      @tap="handleRenameDialogClose"
    ></view>
    <view v-if="renameConversationId" class="sidebar-dialog-shell">
      <view class="sidebar-dialog" @click.stop @tap.stop>
        <view class="sidebar-dialog-title">{{ SIDEBAR_TEXT.renameDialogTitle }}</view>
        <input
          v-model="renameValue"
          class="sidebar-dialog-input"
          maxlength="80"
          :placeholder="SIDEBAR_TEXT.renameDialogPlaceholder"
          focus
        />
        <view class="sidebar-dialog-actions">
          <button
            class="sidebar-dialog-btn ghost"
            :disabled="renameSaving"
            @tap.stop="handleRenameDialogClose"
            @click.stop="handleRenameDialogClose"
          >
            {{ SIDEBAR_TEXT.cancel }}
          </button>
          <button
            class="sidebar-dialog-btn primary"
            :disabled="renameSaving"
            @tap.stop="handleRenameSaveAction"
            @click.stop="handleRenameSaveAction"
          >
            {{ renameSaving ? SIDEBAR_TEXT.saving : SIDEBAR_TEXT.save }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped src="./AppSidebar.css"></style>
