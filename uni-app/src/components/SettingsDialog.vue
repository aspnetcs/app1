<template>
  <view
    v-if="visible && !pageMode"
    class="settings-backdrop"
    aria-hidden="true"
    @click="handleCloseActivate"
    @tap="handleCloseActivate"
  ></view>
  <view
    v-if="visible"
    class="settings-dialog-wrap"
    :class="{ 'settings-dialog-wrap-page': pageMode }"
  >
    <view
      ref="dialogRef"
      class="settings-dialog"
      :class="{ 'settings-dialog-page': pageMode }"
      role="dialog"
      aria-modal="true"
      aria-label="设置"
      tabindex="0"
      @click.stop
      @tap.stop
    >
      <view class="settings-sidebar">
        <view class="settings-sidebar-header">
          <view
            class="settings-close-btn"
            role="button"
            aria-label="关闭设置"
            tabindex="0"
            @click="handleCloseActivate"
            @tap="handleCloseActivate"
            @keydown.enter.prevent="handleCloseActivate"
            @keydown.space.prevent="handleCloseActivate"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="close" :size="18" color="currentColor" :stroke-width="2.5" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 6L6 18"/>
              <path d="M6 6l12 12"/>
            </svg>
            <!-- #endif -->
          </view>
        </view>
        <view class="settings-nav">
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'modelQuota' }"
            role="button"
            :aria-current="activeTab === 'modelQuota' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('modelQuota', $event)"
            @tap="handleTabActivate('modelQuota', $event)"
            @keydown.enter.prevent="handleTabActivate('modelQuota', $event)"
            @keydown.space.prevent="handleTabActivate('modelQuota', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="menu" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="5" y1="20" x2="5" y2="10"/>
              <line x1="12" y1="20" x2="12" y2="4"/>
              <line x1="19" y1="20" x2="19" y2="14"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">模型额度</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'agent' }"
            role="button"
            :aria-current="activeTab === 'agent' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('agent', $event)"
            @tap="handleTabActivate('agent', $event)"
            @keydown.enter.prevent="handleTabActivate('agent', $event)"
            @keydown.space.prevent="handleTabActivate('agent', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="user" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
              <circle cx="12" cy="7" r="4"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">智能体</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'memory' }"
            role="button"
            :aria-current="activeTab === 'memory' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('memory', $event)"
            @tap="handleTabActivate('memory', $event)"
            @keydown.enter.prevent="handleTabActivate('memory', $event)"
            @keydown.space.prevent="handleTabActivate('memory', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="clock" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9"/>
              <path d="M12 7v5l3 2"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">记忆</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'knowledge' }"
            role="button"
            :aria-current="activeTab === 'knowledge' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('knowledge', $event)"
            @tap="handleTabActivate('knowledge', $event)"
            @keydown.enter.prevent="handleTabActivate('knowledge', $event)"
            @keydown.space.prevent="handleTabActivate('knowledge', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="book" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">知识库</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'skills' }"
            role="button"
            :aria-current="activeTab === 'skills' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('skills', $event)"
            @tap="handleTabActivate('skills', $event)"
            @keydown.enter.prevent="handleTabActivate('skills', $event)"
            @keydown.space.prevent="handleTabActivate('skills', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="box" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              <path d="M3.29 7 12 12l8.71-5"/>
              <path d="M12 22V12"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">技能库</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'mcp' }"
            role="button"
            :aria-current="activeTab === 'mcp' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('mcp', $event)"
            @tap="handleTabActivate('mcp', $event)"
            @keydown.enter.prevent="handleTabActivate('mcp', $event)"
            @keydown.space.prevent="handleTabActivate('mcp', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="chat" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">MCP</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'appearance' }"
            role="button"
            :aria-current="activeTab === 'appearance' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('appearance', $event)"
            @tap="handleTabActivate('appearance', $event)"
            @keydown.enter.prevent="handleTabActivate('appearance', $event)"
            @keydown.space.prevent="handleTabActivate('appearance', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="image" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="13.5" cy="6.5" r="2.5"/>
              <path d="M6 21h12a2 2 0 0 0 1.94-1.5l.8-3.2A2 2 0 0 0 19.8 14H14l-1.4-4.2A2 2 0 0 0 10.7 8H9"/>
              <path d="M5 12h4"/>
              <path d="M4 16h5"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">外观</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'archived' }"
            role="button"
            :aria-current="activeTab === 'archived' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('archived', $event)"
            @tap="handleTabActivate('archived', $event)"
            @keydown.enter.prevent="handleTabActivate('archived', $event)"
            @keydown.space.prevent="handleTabActivate('archived', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="archive" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <polyline points="21 8 21 21 3 21 3 8"/>
              <rect x="1" y="3" width="22" height="5"/>
              <line x1="10" y1="12" x2="14" y2="12"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">已归档</text>
          </view>
          <view
            class="settings-nav-item"
            :class="{ 'settings-nav-item-active': activeTab === 'account' }"
            role="button"
            :aria-current="activeTab === 'account' ? 'page' : undefined"
            tabindex="0"
            @click="handleTabActivate('account', $event)"
            @tap="handleTabActivate('account', $event)"
            @keydown.enter.prevent="handleTabActivate('account', $event)"
            @keydown.space.prevent="handleTabActivate('account', $event)"
          >
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="settings" :size="16" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 5a3 3 0 1 0-5.997.176A4 4 0 0 0 6 13v1a3 3 0 0 0 6 0v-4"/>
              <path d="M12 5a3 3 0 1 1 5.997.176A4 4 0 0 1 18 13v1a3 3 0 0 1-6 0"/>
            </svg>
            <!-- #endif -->
            <text class="settings-nav-label">账户设置</text>
          </view>
        </view>
      </view>
      <view class="settings-content">
        <SettingsModelQuotaPanel v-if="activeTab === 'modelQuota'" />
        <SettingsAgentPanel v-else-if="activeTab === 'agent'" />
        <SettingsMemoryPanel v-else-if="activeTab === 'memory'" />
        <SettingsKnowledgePanel v-else-if="activeTab === 'knowledge'" />
        <SettingsSkillPanel v-else-if="activeTab === 'skills'" />
        <SettingsMcpPanel v-else-if="activeTab === 'mcp'" />
        <SettingsAppearancePanel v-else-if="activeTab === 'appearance'" />
        <SettingsArchivedPanel v-else-if="activeTab === 'archived'" />
        <SettingsAccountPanel v-else-if="activeTab === 'account'" @close="handleClose" @change-password="openChangePassword" />
      </view>
    </view>
    <ChangePasswordDialog :visible="changePasswordVisible" @close="changePasswordVisible = false" />
  </view>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import SettingsAccountPanel from './settings/SettingsAccountPanel.vue'
import SettingsKnowledgePanel from './settings/SettingsKnowledgePanel.vue'
import SettingsMemoryPanel from './settings/SettingsMemoryPanel.vue'
import SettingsModelQuotaPanel from './settings/SettingsModelQuotaPanel.vue'
import SettingsAgentPanel from './settings/SettingsAgentPanel.vue'
import SettingsSkillPanel from './settings/SettingsSkillPanel.vue'
import SettingsMcpPanel from './settings/SettingsMcpPanel.vue'
import SettingsAppearancePanel from './settings/SettingsAppearancePanel.vue'
import SettingsArchivedPanel from './settings/SettingsArchivedPanel.vue'
import ChangePasswordDialog from './ChangePasswordDialog.vue'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const props = defineProps<{
  visible: boolean
  pageMode?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const activeTab = ref<'modelQuota' | 'agent' | 'memory' | 'knowledge' | 'skills' | 'mcp' | 'appearance' | 'archived' | 'account'>('modelQuota')
const changePasswordVisible = ref(false)
const runCompatAction = createCompatActionRunner()
const dialogRef = ref<HTMLElement | null>(null)

function handleWindowKeydown(event: KeyboardEvent) {
  if (!props.visible) {
    return
  }
  if (event.key === 'Escape') {
    event.preventDefault()
    handleClose()
  }
}

watch(() => props.visible, (v) => {
  if (v) {
    activeTab.value = 'modelQuota'
    // #ifndef MP-WEIXIN
    window.addEventListener('keydown', handleWindowKeydown)
    void nextTick(() => {
      dialogRef.value?.focus?.()
    })
    // #endif
    return
  }
  // #ifndef MP-WEIXIN
  window.removeEventListener('keydown', handleWindowKeydown)
  // #endif
})

onBeforeUnmount(() => {
  // #ifndef MP-WEIXIN
  window.removeEventListener('keydown', handleWindowKeydown)
  // #endif
})

function handleClose() {
  emit('close')
}

function openChangePassword() {
  changePasswordVisible.value = true
}

function handleCloseActivate(event?: CompatEventLike) {
  runCompatAction('settings-dialog-close', event, () => {
    handleClose()
  })
}

function handleTabActivate(
  tab: 'modelQuota' | 'agent' | 'memory' | 'knowledge' | 'skills' | 'mcp' | 'appearance' | 'archived' | 'account',
  event?: CompatEventLike,
) {
  runCompatAction(`settings-dialog-tab:${tab}`, event, () => {
    activeTab.value = tab
  })
}
</script>

<style scoped>
.settings-backdrop {
  position: fixed;
  inset: 0;
  background-color: var(--app-overlay, rgba(15, 23, 42, 0.56));
  z-index: var(--z-modal-backdrop);
  animation: settingsFadeIn 150ms ease;
}

.settings-dialog-wrap {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.settings-dialog {
  pointer-events: auto;
  display: flex;
  width: 720px;
  max-width: 90vw;
  height: 560px;
  max-height: 82vh;
  background-color: var(--app-surface, #ffffff);
  border-radius: 16px;
  box-shadow: var(--app-shadow-elevated);
  overflow: hidden;
  animation: settingsSlideUp 200ms cubic-bezier(0.16, 1, 0.3, 1);
  color: var(--app-text-primary);
}

.settings-sidebar {
  width: 220px;
  min-width: 220px;
  background-color: var(--app-surface-muted, #f8fafc);
  border-right: 1px solid var(--app-border-color-soft);
  display: flex;
  flex-direction: column;
  padding: 16px 12px;
}

.settings-sidebar-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 4px;
}

.settings-close-btn {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-text-secondary);
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
}

.settings-close-btn:hover {
  background: var(--app-border-color);
  color: var(--app-text-primary);
}

.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  overflow-y: auto;
}

.settings-nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--app-text-primary);
  transition: background 120ms ease, color 120ms ease;
}

.settings-nav-item:hover {
  background: var(--app-border-color-soft);
}

.settings-nav-item-active {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-weight: 500;
}

.settings-nav-item-active:hover {
  background: var(--app-accent-soft);
}

.settings-nav-label {
  font-size: 14px;
}

.settings-content {
  flex: 1;
  padding: calc(var(--app-space-vertical, 16px) * 1.5) calc(var(--app-space-horizontal, 16px) * 2);
  overflow-y: auto;
  min-width: 0;
  background-color: var(--app-surface, #ffffff);
}

/* #ifdef MP-WEIXIN */
.settings-dialog-wrap {
  align-items: flex-end;
  padding: 12px 12px calc(12px + var(--app-safe-bottom, 0px));
  box-sizing: border-box;
}

.settings-dialog {
  width: 100%;
  max-width: none;
  height: auto;
  max-height: calc(100vh - var(--app-safe-top, 0px) - var(--app-safe-bottom, 0px) - 12px);
  flex-direction: column;
  border-radius: 20px 20px 0 0;
}

.settings-sidebar {
  width: 100%;
  min-width: 0;
  border-right: none;
  border-bottom: 1px solid var(--app-border-color-soft);
  padding: 12px;
}

.settings-sidebar-header {
  justify-content: flex-end;
  margin-bottom: 12px;
}

.settings-nav {
  flex-direction: row;
  gap: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  white-space: nowrap;
  padding-bottom: 4px;
}

.settings-nav-item {
  flex: 0 0 auto;
  min-width: 92px;
  justify-content: center;
  padding: 12px 10px;
}

.settings-nav-label {
  white-space: nowrap;
}

.settings-content {
  padding: calc(var(--app-space-vertical, 16px) * 1.25) var(--app-space-horizontal, 16px) calc(var(--app-space-vertical, 16px) + var(--app-safe-bottom, 0px));
}
/* #endif */

.settings-dialog-wrap-page {
  position: relative;
  inset: auto;
  width: 100%;
  min-height: 100vh;
  padding: calc(16px + var(--app-safe-top, 0px)) 12px calc(16px + var(--app-safe-bottom, 0px));
  box-sizing: border-box;
  pointer-events: auto;
  align-items: flex-start;
  justify-content: center;
  background: var(--app-page-bg);
  overflow: hidden;
}

.settings-dialog.settings-dialog-page {
  width: 100%;
  max-width: 720px;
  height: calc(100vh - var(--app-safe-top, 0px) - var(--app-safe-bottom, 0px) - 32px);
  max-height: none;
  border-radius: 20px;
}

/* #ifdef MP-WEIXIN */
.settings-dialog-wrap-page {
  position: relative;
  align-items: flex-start;
  padding: calc(12px + var(--app-safe-top, 0px)) 10px calc(12px + var(--app-safe-bottom, 0px));
}

.settings-dialog.settings-dialog-page {
  height: calc(100vh - var(--app-safe-top, 0px) - var(--app-safe-bottom, 0px) - 24px);
  border-radius: 20px;
}
/* #endif */

@keyframes settingsFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes settingsSlideUp {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
