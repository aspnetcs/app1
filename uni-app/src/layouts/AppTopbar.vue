<script setup lang="ts">
/**
 * AppTopbar - Arena.ai style top bar
 *
 * Layout:
 * - single:  [Mode v] [Model v]
 * - compare: [Mode v] [Model A v] [Model B v] ... [+] [-]
 * - team:    [Mode v] [Model A v] [Model B v] ... [+] [-]
 *
 * compare and team share the same multiModelIds list.
 */
import { computed, ref } from 'vue'
import ModelSelector from '@/components/ModelSelector.vue'
// #ifndef MP-WEIXIN
import IconPlus from '@/components/icons/IconPlus.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
import type { ChatMode } from '@/composables/useChatMode'
import { replaceMultiModelId } from '@/utils/multiModelId'
import { createCompatActionRunner } from '@/utils/h5EventCompat'
import { useStatusBar } from '@/utils/useStatusBar'
import { useChatConfigSheet, useChatInputBlurSignal } from '@/composables/useChatMode'
import type { LayoutModelOption } from './appLayoutModelOptions'

const {
  navigationBarHeight,
  navigationContentTop,
  navigationContentHeight,
  navigationRightInset,
  hasCapsule,
} = useStatusBar()

const topbarStyle = computed(() => {
  const barHeight = navigationBarHeight.value
  if (barHeight > 0) {
    const styleParts = [
      `padding-top:${navigationContentTop.value}px`,
      `height:${barHeight}px`,
      `--model-selector-dropdown-top:${barHeight}px`,
      'box-sizing:border-box',
    ]
    if (hasCapsule.value) {
      styleParts.push(`padding-right:${navigationRightInset.value}px`)
    }
    return `${styleParts.join(';')};`
  }
  return ''
})

const topbarInnerStyle = computed(() => {
  const contentHeight = navigationContentHeight.value
  if (contentHeight > 0) {
    return `height:${contentHeight}px;`
  }
  return ''
})

const props = defineProps<{
  modelValue: string
  realModels: LayoutModelOption[]
  isMobileLayout: boolean
  showSidebarToggle: boolean
  showLoginButton: boolean
  avatarText: string
  chatMode: ChatMode
  multiModelIds: string[]
  captainMode: 'auto' | 'fixed_first'
  hideChatControls: boolean
  pageTitle?: string
  pageSubtitle?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'toggle-sidebar'): void
  (e: 'go-login'): void
  (e: 'open-account'): void
  (e: 'update:chatMode', mode: ChatMode): void
  (e: 'update:multiModelIds', ids: string[]): void
  (e: 'update:captainMode', mode: 'auto' | 'fixed_first'): void
  (e: 'open-chat-config'): void
}>()

const showModeMenu = ref(false)
const runCompatAction = createCompatActionRunner()
const sheetVisible = useChatConfigSheet()
const chatInputBlurSignal = useChatInputBlurSignal()

const modeLabel = computed(() => {
  switch (props.chatMode) {
    case 'single':
      return '单模型模式'
    case 'compare':
      return '多模型模式'
    case 'team':
      return '团队模式'
    default:
      return '单模型模式'
  }
})

const isMultiMode = computed(() => props.chatMode === 'compare' || props.chatMode === 'team')

const currentSummaryTitle = computed(() => {
  if (props.chatMode === 'single') {
    const model = props.realModels.find(m => m.id === props.modelValue)
    return model ? model.name : '选择模型'
  } else if (props.chatMode === 'compare') {
    return `多模型对比 (${props.multiModelIds.length})`
  } else if (props.chatMode === 'team') {
    return `团队协作 (${props.multiModelIds.length})`
  }
  return '选择配置'
})

function handleOpenMobileConfig(event?: Event) {
  runCompatAction('open-chat-config', event, () => {
    // #ifdef MP-WEIXIN
    try {
      uni.hideKeyboard()
    } catch {
      // ignore
    }
    // #endif
    chatInputBlurSignal.value += 1
    sheetVisible.value = true
  })
}

function selectMode(mode: ChatMode) {
  emit('update:chatMode', mode)
  showModeMenu.value = false
}

function handleModeTrigger(event?: Event) {
  runCompatAction('topbar-mode-trigger', event, () => {
    showModeMenu.value = !showModeMenu.value
  })
}

function handleModeOverlay(event?: Event) {
  runCompatAction('topbar-mode-overlay', event, () => {
    showModeMenu.value = false
  })
}

function handleModeSelect(mode: ChatMode, event?: Event) {
  runCompatAction(`topbar-mode-select:${mode}`, event, () => {
    selectMode(mode)
  })
}

function updateMultiModel(index: number, id: string) {
  const nextList = replaceMultiModelId(props.multiModelIds, index, id)
  const changed = nextList.some((modelId, currentIndex) => modelId !== props.multiModelIds[currentIndex])
  if (!changed) {
    return
  }
  emit('update:multiModelIds', nextList)
}

function addModel() {
  const list = [...props.multiModelIds]
  const used = new Set(list)
  const next = props.realModels.find((model) => !used.has(model.id))
  if (!next?.id) {
    return
  }
  list.push(next.id)
  emit('update:multiModelIds', list)
}

function handleAddModel(event?: Event) {
  runCompatAction('topbar-add-model', event, () => {
    addModel()
  })
}

function removeModel() {
  const list = [...props.multiModelIds]
  if (list.length <= 2) return
  list.pop()
  emit('update:multiModelIds', list)
}

function handleRemoveModel(event?: Event) {
  runCompatAction('topbar-remove-model', event, () => {
    removeModel()
  })
}

function toggleCaptainMode() {
  emit('update:captainMode', props.captainMode === 'auto' ? 'fixed_first' : 'auto')
}

function handleCaptainToggle(event?: Event) {
  runCompatAction('topbar-captain-toggle', event, () => {
    toggleCaptainMode()
  })
}

function handleToggleSidebar(event?: Event) {
  runCompatAction('topbar-toggle-sidebar', event, () => {
    emit('toggle-sidebar')
  })
}

function handleGoLogin(event?: Event) {
  runCompatAction('topbar-go-login', event, () => {
    emit('go-login')
  })
}

function handleOpenAccount(event?: Event) {
  runCompatAction('topbar-open-account', event, () => {
    emit('open-account')
  })
}
</script>

<template>
  <view class="topbar" :class="{ 'topbar-mobile': isMobileLayout }" :style="topbarStyle">
    <view class="topbar-inner" :style="topbarInnerStyle">
      <view class="topbar-left">
        <view
          v-if="showSidebarToggle"
          class="topbar-sidebar-toggle"
          @click.stop="handleToggleSidebar"
          @tap.stop="handleToggleSidebar"
        >
          <view class="topbar-sidebar-toggle-lines">
            <view class="topbar-sidebar-toggle-line"></view>
            <view class="topbar-sidebar-toggle-line"></view>
            <view class="topbar-sidebar-toggle-line"></view>
          </view>
        </view>

        <template v-if="!hideChatControls">
        <template v-if="!isMobileLayout">
        <view class="mode-selector" @click.stop @tap.stop>
          <view class="mode-trigger" @click.stop="handleModeTrigger" @tap.stop="handleModeTrigger">
            <text class="mode-trigger-text">{{ modeLabel }}</text>
            <view class="mode-chevron" :class="{ 'mode-chevron-open': showModeMenu }">
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="chevron-down" :size="10" color="currentColor" :stroke-width="1.5" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
                <path d="M2 3.5L5 6.5L8 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <!-- #endif -->
            </view>
          </view>
          <view v-if="showModeMenu" class="mode-dropdown">
            <view
              class="mode-option"
              :class="{ 'mode-option-active': chatMode === 'single' }"
              @click.stop="handleModeSelect('single', $event)"
              @tap.stop="handleModeSelect('single', $event)"
            >
              <text class="mode-option-text">单模型模式</text>
              <text class="mode-option-desc">单个模型对话</text>
            </view>
            <view
              class="mode-option"
              :class="{ 'mode-option-active': chatMode === 'compare' }"
              @click.stop="handleModeSelect('compare', $event)"
              @tap.stop="handleModeSelect('compare', $event)"
            >
              <text class="mode-option-text">多模型模式</text>
              <text class="mode-option-desc">多个模型并排对比</text>
            </view>
            <view
              class="mode-option mode-option-with-toggle"
              :class="{ 'mode-option-active': chatMode === 'team' }"
              @click.stop="handleModeSelect('team', $event)"
              @tap.stop="handleModeSelect('team', $event)"
            >
              <view class="mode-option-left">
                <text class="mode-option-text">团队模式</text>
                <text class="mode-option-desc">多个模型协商</text>
              </view>
              <view class="mode-toggle-area">
                <text class="mode-toggle-label">自动选举队长</text>
                <view
                  class="toggle-switch"
                  :class="{ 'toggle-switch-on': captainMode === 'auto' }"
                  @click.stop.prevent="handleCaptainToggle"
                  @tap.stop.prevent="handleCaptainToggle"
                >
                  <view class="toggle-knob"></view>
                </view>
              </view>
            </view>
          </view>
          <view v-if="showModeMenu" class="mode-overlay" @click="handleModeOverlay" @tap="handleModeOverlay"></view>
        </view>

        <template v-if="chatMode === 'single'">
          <ModelSelector
            :model-value="modelValue"
            :models="realModels"
            @update:model-value="emit('update:modelValue', $event)"
          />
        </template>

        <template v-if="isMultiMode">
          <ModelSelector
            v-for="(mid, idx) in multiModelIds"
            :key="idx"
            :model-value="mid"
            :models="realModels"
            @update:model-value="updateMultiModel(idx, $event)"
          />
          <view class="topbar-add-btn" @click="handleAddModel" @tap="handleAddModel">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="plus" :size="16" color="currentColor" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <IconPlus :size="16" color="currentColor" />
            <!-- #endif -->
          </view>
          <view
            v-if="multiModelIds.length > 2"
            class="topbar-add-btn"
            @click="handleRemoveModel"
            @tap="handleRemoveModel"
          >
            <!-- #ifdef MP-WEIXIN -->
            <text style="font-size:16px;color:currentColor;line-height:16px">-</text>
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
              <path d="M4 8h8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            </svg>
            <!-- #endif -->
          </view>
        </template>
        </template>
        <template v-else>
          <view class="mobile-aggregate-title" @click.stop="handleOpenMobileConfig" @tap.stop="handleOpenMobileConfig">
            <text class="mobile-title-text">{{ currentSummaryTitle }}</text>
            <view class="mobile-title-chevron">
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="chevron-down" :size="12" color="currentColor" :stroke-width="2.5" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path d="M3 4.5L6 7.5L9 4.5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              <!-- #endif -->
            </view>
          </view>
        </template>
        </template>

        <template v-if="(hideChatControls || $slots['page-header']) && (pageTitle || $slots['page-header'])">
          <view
            class="topbar-page-copy"
            :class="{
              'topbar-page-copy-mobile': isMobileLayout,
              'topbar-page-copy-slot': Boolean($slots['page-header']),
            }"
          >
            <slot name="page-header">
              <text class="topbar-page-title">{{ pageTitle }}</text>
              <text v-if="pageSubtitle" class="topbar-page-subtitle">{{ pageSubtitle }}</text>
            </slot>
          </view>
        </template>
      </view>

      <view class="topbar-right">
        <view v-if="showLoginButton" class="topbar-login-btn" @click="handleGoLogin" @tap="handleGoLogin">
          <text>登录</text>
        </view>
        <view v-else class="topbar-user-avatar" @click="handleOpenAccount" @tap="handleOpenAccount">
          <text class="topbar-user-avatar-text">{{ avatarText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped src="./AppTopbar.css"></style>
