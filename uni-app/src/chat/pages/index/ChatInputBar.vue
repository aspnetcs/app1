<template>
  <view class="input-area" :style="inputAreaStyle">
    <view class="input-container" :class="{ 'input-container-desktop': isDesktopLayout }">
      <view class="input-box-shell" :class="{ 'input-box-shell-desktop': isDesktopLayout }">
        <view v-if="$slots.default" class="input-slot-stack">
          <slot />
        </view>

        <view
          class="input-box"
          :class="{
            'input-box-desktop': isDesktopLayout,
            'input-box-drop-active': isDropTargetActive,
          }"
        >
          <textarea
            ref="textareaRef"
            :value="modelValue"
            class="input-textarea"
            :class="{ 'input-textarea-desktop': isDesktopLayout }"
            :placeholder="inputPlaceholder"
            :auto-height="false"
            :cursor-spacing="20"
            :adjust-position="false"
            :fixed="true"
            :focus="isFocused"
            @input="onInput"
            @confirm="emit('send')"
            @focus="handleFocus"
            @blur="handleBlur"
            @keydown="onInputKeydown"
          />

          <!-- #ifndef MP-WEIXIN -->
          <input
            ref="attachmentInputRef"
            class="attachment-native-input"
            type="file"
            multiple
            @change="handleAttachmentInputChange"
          />
          <!-- #endif -->

          <view class="input-toolbar" :class="{ 'input-toolbar-desktop': isDesktopLayout }">
            <view class="input-toolbar-left" :class="{ 'input-toolbar-left-desktop': isDesktopLayout }">
              <view
                v-if="config.features.multimodalUpload && !isGenerating"
                class="attachment-trigger"
                @click.stop="handleOpenAttachmentMenu($event)"
                @tap.stop="handleOpenAttachmentMenu($event)"
              >
                <!-- #ifdef MP-WEIXIN -->
                <MpShapeIcon name="plus" :size="18" color="var(--app-text-secondary)" />
                <!-- #endif -->
                <!-- #ifndef MP-WEIXIN -->
                <IconPlus :size="18" color="var(--app-text-secondary)" />
                <!-- #endif -->
              </view>

              <!-- #ifndef MP-WEIXIN -->
              <AudioRecorder @transcription="emit('transcription', $event)" @error="emit('audio-error', $event)" />
              <!-- #endif -->

              <!-- #ifndef MP-WEIXIN -->
              <view
                v-if="config.features.webRead && detectedUrl && webReadStatus !== 'done'"
                class="toolbar-text-btn"
                :class="{ 'toolbar-text-btn-disabled': webReadStatus === 'loading' }"
                @click.stop="handleWebReadRequest($event)"
                @tap.stop="handleWebReadRequest($event)"
              >
                <text class="toolbar-text-btn-label">{{ webReadButtonLabel }}</text>
              </view>

              <view
                v-if="config.features.webRead && webReadStatus === 'done'"
                class="toolbar-text-btn toolbar-text-btn-done"
                @click.stop="handleWebReadClear($event)"
                @tap.stop="handleWebReadClear($event)"
              >
                <text class="toolbar-text-btn-label">已读取</text>
              </view>
              <!-- #endif -->

              <!-- #ifndef MP-WEIXIN -->
              <view
                v-if="config.features.promptOptimize && trimmedValue && !isGenerating"
                class="toolbar-text-btn"
                :class="{ 'toolbar-text-btn-disabled': optimizingPrompt }"
                @click.stop="handleOptimizePrompt($event)"
                @tap.stop="handleOptimizePrompt($event)"
              >
                <text class="toolbar-text-btn-label">{{ optimizingPrompt ? '优化中...' : '优化' }}</text>
              </view>
              <!-- #endif -->
            </view>

            <view class="input-toolbar-right" :class="{ 'input-toolbar-right-desktop': isDesktopLayout }">
              <view v-if="showAgentSelector" class="agent-selector-wrap">
                <view
                  class="agent-trigger"
                  :class="{ 'agent-trigger-active': agentPopupOpen, 'agent-trigger-locked': agentLocked }"
                  @click.stop="toggleAgentPopup($event)"
                  @tap.stop="toggleAgentPopup($event)"
                >
                  <text class="agent-trigger-label">{{ agentButtonText }}</text>
                </view>

                <view
                  v-if="agentPopupOpen"
                  class="agent-popup"
                  @click.stop
                  @tap.stop
                >
                  <view class="agent-popup-header">
                    <text class="agent-popup-title">已下载智能体</text>
                    <view class="agent-popup-actions">
                      <text class="agent-popup-link" @click.stop="handleOpenAgentMarket($event)" @tap.stop="handleOpenAgentMarket($event)">市场</text>
                      <text
                        v-if="canClearAgent"
                        class="agent-popup-link"
                        @click.stop="handleClearAgent($event)"
                        @tap.stop="handleClearAgent($event)"
                      >
                        仅模型
                      </text>
                    </view>
                  </view>

                  <view v-if="agentLoading" class="agent-popup-empty">
                    <text class="agent-popup-empty-text">正在加载智能体...</text>
                  </view>

                  <scroll-view
                    v-else-if="agentOptions.length > 0"
                    scroll-y
                    class="agent-popup-list"
                  >
                    <view
                      v-for="agent in agentOptions"
                      :key="agent.id"
                      class="agent-option"
                      :class="{ 'agent-option-active': agent.id === selectedAgentId }"
                      @click.stop="handleSelectAgent(agent.id, $event)"
                      @tap.stop="handleSelectAgent(agent.id, $event)"
                    >
                      <view class="agent-option-copy">
                        <text class="agent-option-title">{{ agent.title }}</text>
                        <text v-if="agent.subtitle" class="agent-option-subtitle">{{ agent.subtitle }}</text>
                      </view>
                      <text v-if="agent.badge" class="agent-option-badge">{{ agent.badge }}</text>
                    </view>
                  </scroll-view>

                  <view v-else class="agent-popup-empty">
                    <text class="agent-popup-empty-text">你还没有从市场下载智能体</text>
                    <view
                      class="agent-popup-empty-btn"
                      @click.stop="handleOpenAgentMarket($event)"
                      @tap.stop="handleOpenAgentMarket($event)"
                    >
                      <text class="agent-popup-empty-btn-text">前往市场</text>
                    </view>
                  </view>
                </view>
              </view>

              <view
                v-if="isGenerating"
                class="send-btn send-btn-stop"
                :class="{ 'send-btn-desktop': isDesktopLayout }"
                @click.stop="handleStopActivate"
                @tap.stop="handleStopActivate"
              >
                <!-- #ifdef MP-WEIXIN -->
                <MpShapeIcon name="stop" :size="16" color="white" />
                <!-- #endif -->
                <!-- #ifndef MP-WEIXIN -->
                <IconStop :size="16" color="white" />
                <!-- #endif -->
              </view>

              <view
                v-else
                class="send-btn"
                :class="[sendEnabled ? 'send-btn-active' : 'send-btn-disabled', isDesktopLayout ? 'send-btn-desktop' : '']"
                @click.stop="handleSendActivate"
                @tap.stop="handleSendActivate"
              >
                <!-- #ifdef MP-WEIXIN -->
                <MpShapeIcon name="send" :size="16" :color="sendEnabled ? 'white' : 'var(--app-text-secondary)'" />
                <!-- #endif -->
                <!-- #ifndef MP-WEIXIN -->
                <IconSend :size="16" :color="sendEnabled ? 'white' : 'var(--app-text-secondary)'" />
                <!-- #endif -->
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue'
// #ifndef MP-WEIXIN
import AudioRecorder from '@/components/audio/AudioRecorder.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconSend from '@/components/icons/IconSend.vue'
import IconStop from '@/components/icons/IconStop.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
import { useChatInputBlurSignal } from '@/composables/useChatMode'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import { config } from '@/config'
import { optimizePromptApi } from '@/api/prompt-optimize'

type AgentSelectorOption = {
  id: string
  title: string
  subtitle?: string
  badge?: string
}

const props = defineProps<{
  modelValue: string
  isGenerating: boolean
  keyboardHeightPx?: number
  safeBottomPx?: number
  webReadStatus?: 'idle' | 'loading' | 'done'
  canSend?: boolean
  forceMobileLayout?: boolean
  showAgentSelector?: boolean
  agentSummary?: string
  agentOptions?: AgentSelectorOption[]
  selectedAgentId?: string | null
  agentLoading?: boolean
  canClearAgent?: boolean
  agentLocked?: boolean
  agentLockedMessage?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'send'): void
  (e: 'stop'): void
  (e: 'transcription', value: string): void
  (e: 'audio-error', value: string): void
  (e: 'height-change', value: number): void
  (e: 'web-read-request', url: string): void
  (e: 'web-read-clear'): void
  (e: 'upload-files', files: File[]): void
  (e: 'pick-attachment'): void
  (e: 'pick-image'): void
  (e: 'pick-document'): void
  (e: 'select-agent', id: string): void
  (e: 'clear-agent'): void
  (e: 'open-agent-market'): void
}>()

const runCompatAction = createCompatActionRunner()
const blurSignal = useChatInputBlurSignal()
const optimizingPrompt = ref(false)
const isFocused = ref(false)
const isDesktopLayout = ref(false)
const agentPopupOpen = ref(false)
const isDropTargetActive = ref(false)
const textareaRef = ref<any>(null)
const attachmentInputRef = ref<any>(null)
const instance = getCurrentInstance()
let dropTargetDepth = 0
let nativeTextareaEl: HTMLTextAreaElement | null = null
let nativeDropHostEl: HTMLElement | null = null

const trimmedValue = computed(() => props.modelValue.trim())
const sendEnabled = computed(() => props.canSend ?? Boolean(trimmedValue.value))
const detectedUrl = computed(() => {
  const match = props.modelValue.match(/https?:\/\/[^\s]+/)
  return match ? match[0] : null
})
const webReadButtonLabel = computed(() => {
  if (props.webReadStatus === 'loading') return '读取中...'
  if (props.webReadStatus === 'done') return '已读取'
  return '读取网页'
})
const inputPlaceholder = computed(() =>
  isDesktopLayout.value ? '输入消息，Enter 发送，Shift + Enter 换行' : '输入消息',
)
const inputAreaStyle = computed(() => {
  const keyboardOffset = Math.max(0, Math.round(Number(props.keyboardHeightPx ?? 0)))
  const safeBottom = Math.max(0, Math.round(Number(props.safeBottomPx ?? 0)))
  const style: Record<string, string> = {}
  if (!isDesktopLayout.value) {
    const compactSafeBottom = safeBottom > 0 ? Math.max(8, safeBottom - 14) : 0
    style.bottom = `${keyboardOffset}px`
    style.paddingBottom = `${compactSafeBottom}px`
  }
  return style
})
const agentOptions = computed(() => props.agentOptions ?? [])
const agentLocked = computed(() => Boolean(props.agentLocked))
const agentButtonText = computed(() => {
  if (props.agentLoading) return '加载中'
  const text = props.agentSummary?.trim()
  return text || '选择智能体'
})

function showAgentLockedNotice() {
  const title = props.agentLockedMessage?.trim() || '当前会话的智能体已锁定，如需切换请新建会话'
  uni.showToast({ title, icon: 'none', duration: 2200 })
}

async function handleOptimizePrompt(event?: CompatEventLike) {
  runCompatAction('chat-input-optimize', event, async () => {
    if (!props.modelValue.trim() || optimizingPrompt.value) return
    optimizingPrompt.value = true
    try {
      const resp = await optimizePromptApi({ content: props.modelValue, direction: 'detailed' })
      if (resp.data?.optimizedPrompt) {
        emit('update:modelValue', resp.data.optimizedPrompt)
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : '优化失败，请稍后重试'
      uni.showToast({ title: message, icon: 'none', duration: 2000 })
    } finally {
      optimizingPrompt.value = false
    }
  })
}

function handleWebReadRequest(event?: CompatEventLike) {
  runCompatAction('chat-input-web-read', event, () => {
    const url = detectedUrl.value
    if (!url || props.webReadStatus === 'loading') return
    emit('web-read-request', url)
  })
}

function handleWebReadClear(event?: CompatEventLike) {
  runCompatAction('chat-input-web-read-clear', event, () => {
    emit('web-read-clear')
  })
}

function handleOpenAttachmentMenu(event?: CompatEventLike) {
  runCompatAction('chat-input-open-attachment-menu', event, () => {
    closeAgentPopup()
    if (!config.features.multimodalUpload || props.isGenerating) return
    // #ifndef MP-WEIXIN
    if (openNativeAttachmentPicker()) {
      return
    }
    // #endif
    emit('pick-attachment')
  })
}

function handlePickImage(event?: CompatEventLike) {
  runCompatAction('chat-input-pick-image', event, () => {
    emit('pick-image')
  })
}

function handlePickDocument(event?: CompatEventLike) {
  runCompatAction('chat-input-pick-document', event, () => {
    emit('pick-document')
  })
}

function closeAgentPopup() {
  agentPopupOpen.value = false
}

defineExpose({
  closeAgentPopup,
})

function toggleAgentPopup(event?: CompatEventLike) {
  runCompatAction('chat-input-toggle-agent-popup', event, () => {
    if (!props.showAgentSelector) return
    if (agentLocked.value) {
      closeAgentPopup()
      showAgentLockedNotice()
      return
    }
    agentPopupOpen.value = !agentPopupOpen.value
  })
}

function handleSelectAgent(id: string, event?: CompatEventLike) {
  runCompatAction('chat-input-select-agent', event, () => {
    if (agentLocked.value) {
      closeAgentPopup()
      showAgentLockedNotice()
      return
    }
    closeAgentPopup()
    emit('select-agent', id)
  })
}

function handleClearAgent(event?: CompatEventLike) {
  runCompatAction('chat-input-clear-agent', event, () => {
    if (agentLocked.value) {
      closeAgentPopup()
      showAgentLockedNotice()
      return
    }
    closeAgentPopup()
    emit('clear-agent')
  })
}

function handleOpenAgentMarket(event?: CompatEventLike) {
  runCompatAction('chat-input-open-agent-market', event, () => {
    closeAgentPopup()
    emit('open-agent-market')
  })
}

let lastMeasuredHeight = 0
let measurePending = false

function measureInputAreaHeight() {
  if (!instance) return
  uni
    .createSelectorQuery()
    .in(instance)
    .select('.input-area')
    .boundingClientRect((rect) => {
      const node = Array.isArray(rect) ? rect[0] : rect
      const heightValue =
        node && typeof node === 'object' && 'height' in node
          ? (node as { height?: unknown }).height
          : 0
      const height = Math.max(0, Math.round(Number(heightValue ?? 0)))
      if (height <= 0) return
      if (Math.abs(height - lastMeasuredHeight) < 1) return
      lastMeasuredHeight = height
      emit('height-change', height)
    })
    .exec()
}

function scheduleMeasureInputAreaHeight() {
  if (measurePending) return
  measurePending = true
  nextTick(() => {
    measurePending = false
    measureInputAreaHeight()
  })
}

onMounted(() => {
  syncDesktopLayout()
  // #ifndef MP-WEIXIN
  window.addEventListener('resize', syncDesktopLayout, { passive: true })
  bindDesktopDomListeners()
  // #endif
  scheduleMeasureInputAreaHeight()
})

onUpdated(() => {
  bindDesktopDomListeners()
  scheduleMeasureInputAreaHeight()
})

onBeforeUnmount(() => {
  // #ifndef MP-WEIXIN
  window.removeEventListener('resize', syncDesktopLayout)
  unbindDesktopDomListeners()
  // #endif
})

watch(
  () => [props.keyboardHeightPx, props.safeBottomPx],
  () => {
    scheduleMeasureInputAreaHeight()
  },
)

watch(
  () => blurSignal.value,
  (current, previous) => {
    if (current === previous) return
    isFocused.value = false
    closeAgentPopup()
    // #ifdef MP-WEIXIN
    try {
      uni.hideKeyboard()
    } catch {
      // ignore
    }
    // #endif
  },
)

watch(
  () => props.showAgentSelector,
  (visible) => {
    if (!visible) closeAgentPopup()
  },
)

watch(
  () => props.isGenerating,
  (generating) => {
    if (generating) closeAgentPopup()
  },
)

type TextareaInputDetail =
  | {
      value?: string | number
    }
  | string
  | number
  | undefined

function onInput(event: Event) {
  const detail = (event as Event & { detail?: TextareaInputDetail }).detail
  const targetValue = (event.target as HTMLTextAreaElement | null)?.value
  const value =
    typeof detail === 'object' && detail !== null && 'value' in detail
      ? detail.value
      : detail ?? targetValue
  emit('update:modelValue', value == null ? '' : String(value))
}

function onInputKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.isComposing) {
    return
  }

  if (event.shiftKey) {
    event.preventDefault()
    const target = event.target as HTMLTextAreaElement | null
    const currentValue = target?.value ?? props.modelValue
    const selectionStart = target?.selectionStart ?? currentValue.length
    const selectionEnd = target?.selectionEnd ?? selectionStart
    const nextValue =
      currentValue.slice(0, selectionStart) +
      '\n' +
      currentValue.slice(selectionEnd)

    emit('update:modelValue', nextValue)

    if (target) {
      nextTick(() => {
        try {
          const caret = selectionStart + 1
          target.focus()
          target.setSelectionRange(caret, caret)
        } catch {
          // ignore
        }
      })
    }
    return
  }

  event.preventDefault()
  emit('send')
}

function resetDropTargetState() {
  dropTargetDepth = 0
  isDropTargetActive.value = false
}

function hasTransferFiles(dataTransfer: DataTransfer | null | undefined) {
  if (!dataTransfer) return false
  if (dataTransfer.files && dataTransfer.files.length > 0) return true
  return Array.from(dataTransfer.items || []).some((item) => item.kind === 'file')
}

function extractFilesFromTransfer(dataTransfer: DataTransfer | null | undefined) {
  if (!dataTransfer) return [] as File[]
  if (dataTransfer.files && dataTransfer.files.length > 0) {
    return Array.from(dataTransfer.files)
  }
  return Array.from(dataTransfer.items || [])
    .filter((item) => item.kind === 'file')
    .map((item) => item.getAsFile())
    .filter((file): file is File => Boolean(file))
}

function emitTransferredFiles(files: File[]) {
  if (!config.features.multimodalUpload || props.isGenerating) return false
  const normalized = files.filter((file) => file instanceof File)
  if (normalized.length === 0) return false
  closeAgentPopup()
  emit('upload-files', normalized)
  return true
}

function handleAttachmentInputChange(event: Event) {
  const input = (event.currentTarget || event.target) as HTMLInputElement | null
  const files = input?.files ? Array.from(input.files) : []
  if (input) {
    input.value = ''
  }
  if (files.length === 0) return
  emitTransferredFiles(files)
}

function handleTextareaPaste(event: Event) {
  const clipboardEvent = event as ClipboardEvent
  const files = extractFilesFromTransfer(clipboardEvent.clipboardData)
  if (files.length === 0) return
  event.preventDefault()
  emitTransferredFiles(files)
}

function handleInputDragEnter(event: Event) {
  const dragEvent = event as DragEvent
  if (!hasTransferFiles(dragEvent.dataTransfer)) return
  dropTargetDepth += 1
  isDropTargetActive.value = true
}

function handleInputDragOver(event: Event) {
  const dragEvent = event as DragEvent
  if (!hasTransferFiles(dragEvent.dataTransfer)) return
  if (!isDropTargetActive.value) {
    isDropTargetActive.value = true
  }
}

function handleInputDragLeave(event: Event) {
  const dragEvent = event as DragEvent
  if (!hasTransferFiles(dragEvent.dataTransfer) && !isDropTargetActive.value) return
  dropTargetDepth = Math.max(0, dropTargetDepth - 1)
  if (dropTargetDepth === 0) {
    isDropTargetActive.value = false
  }
}

function handleInputDrop(event: Event) {
  const dragEvent = event as DragEvent
  const files = extractFilesFromTransfer(dragEvent.dataTransfer)
  resetDropTargetState()
  if (files.length === 0) return
  emitTransferredFiles(files)
}

function resolveRootElement() {
  const root = instance?.proxy?.$el
  return root instanceof HTMLElement ? root : null
}

function resolveTextareaElement() {
  const raw = textareaRef.value
  if (raw instanceof HTMLTextAreaElement) {
    return raw
  }
  if (raw && typeof raw === 'object' && '$el' in raw) {
    const el = (raw as { $el?: unknown }).$el
    if (el instanceof HTMLTextAreaElement) {
      return el
    }
    if (el instanceof HTMLElement) {
      const nested = el.querySelector('textarea')
      if (nested instanceof HTMLTextAreaElement) {
        return nested
      }
    }
  }
  const root = resolveRootElement()
  if (root) {
    const nested = root.querySelector('.input-textarea')
    if (nested instanceof HTMLTextAreaElement) {
      return nested
    }
    const textarea = root.querySelector('textarea')
    if (textarea instanceof HTMLTextAreaElement) {
      return textarea
    }
  }
  return null
}

function resolveAttachmentInputElement() {
  const raw = attachmentInputRef.value
  if (raw instanceof HTMLInputElement) {
    return raw
  }
  if (raw && typeof raw === 'object' && '$el' in raw) {
    const el = (raw as { $el?: unknown }).$el
    if (el instanceof HTMLInputElement) {
      return el
    }
    if (el instanceof HTMLElement) {
      const nested = el.querySelector('input[type="file"]')
      if (nested instanceof HTMLInputElement) {
        return nested
      }
    }
  }
  const root = resolveRootElement()
  if (!root) return null
  return root.querySelector('.attachment-native-input') as HTMLInputElement | null
}

function resolveDropHostElement() {
  const root = resolveRootElement()
  if (!root) return null
  return root.querySelector('.input-box') as HTMLElement | null
}

function openNativeAttachmentPicker() {
  const input = resolveAttachmentInputElement()
  if (!(input instanceof HTMLInputElement)) {
    return false
  }
  input.value = ''
  input.click()
  return true
}

function bindDesktopDomListeners() {
  // #ifdef MP-WEIXIN
  return
  // #endif
  const nextTextarea = resolveTextareaElement()
  if (nextTextarea && nextTextarea !== nativeTextareaEl) {
    const previousTextarea = nativeTextareaEl
    previousTextarea?.removeEventListener('paste', handleTextareaPaste as EventListener)
    nativeTextareaEl = nextTextarea
    nextTextarea?.addEventListener('paste', handleTextareaPaste as EventListener)
  }

  const nextDropHost = resolveDropHostElement()
  if (nextDropHost && nextDropHost !== nativeDropHostEl) {
    const previousDropHost = nativeDropHostEl
    previousDropHost?.removeEventListener('paste', handleTextareaPaste as EventListener)
    previousDropHost?.removeEventListener('dragenter', handleInputDragEnter as EventListener)
    previousDropHost?.removeEventListener('dragover', handleInputDragOver as EventListener)
    previousDropHost?.removeEventListener('dragleave', handleInputDragLeave as EventListener)
    previousDropHost?.removeEventListener('drop', handleInputDrop as EventListener)
    nativeDropHostEl = nextDropHost
    nextDropHost?.addEventListener('paste', handleTextareaPaste as EventListener)
    nextDropHost?.addEventListener('dragenter', handleInputDragEnter as EventListener)
    nextDropHost?.addEventListener('dragover', handleInputDragOver as EventListener)
    nextDropHost?.addEventListener('dragleave', handleInputDragLeave as EventListener)
    nextDropHost?.addEventListener('drop', handleInputDrop as EventListener)
  }
}

function unbindDesktopDomListeners() {
  // #ifdef MP-WEIXIN
  return
  // #endif
  const previousTextarea = nativeTextareaEl
  if (previousTextarea) {
    previousTextarea?.removeEventListener('paste', handleTextareaPaste as EventListener)
    nativeTextareaEl = null
  }
  const previousDropHost = nativeDropHostEl
  if (previousDropHost) {
    previousDropHost?.removeEventListener('paste', handleTextareaPaste as EventListener)
    previousDropHost?.removeEventListener('dragenter', handleInputDragEnter as EventListener)
    previousDropHost?.removeEventListener('dragover', handleInputDragOver as EventListener)
    previousDropHost?.removeEventListener('dragleave', handleInputDragLeave as EventListener)
    previousDropHost?.removeEventListener('drop', handleInputDrop as EventListener)
    nativeDropHostEl = null
  }
}

function handleFocus() {
  isFocused.value = true
}

function handleBlur() {
  isFocused.value = false
}

function handleSendActivate(event?: CompatEventLike) {
  runCompatAction('chat-input-send', event, () => {
    closeAgentPopup()
    emit('send')
  })
}

function handleStopActivate(event?: CompatEventLike) {
  runCompatAction('chat-input-stop', event, () => {
    closeAgentPopup()
    emit('stop')
  })
}

function syncDesktopLayout() {
  if (props.forceMobileLayout) {
    isDesktopLayout.value = false
    return
  }
  // #ifdef MP-WEIXIN
  isDesktopLayout.value = false
  // #endif
  // #ifndef MP-WEIXIN
  isDesktopLayout.value = window.innerWidth >= 600
  // #endif
}
</script>

<style scoped>
.input-area {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  width: 100%;
  padding: 0;
  background: var(--app-surface);
  border-top-left-radius: 32px;
  border-top-right-radius: 32px;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.2);
  box-sizing: border-box;
  z-index: 50;
}

.agent-popup-mask {
  position: fixed;
  inset: 0;
  background: transparent;
  z-index: 1;
  pointer-events: none;
}

.input-container {
  position: relative;
  z-index: 2;
  max-width: var(--chat-content-max-width, 768px);
  width: 100%;
  margin: 0 auto;
  padding: 12px var(--chat-content-padding-x, 20px) 0;
  box-sizing: border-box;
}

.input-box-shell {
  width: 100%;
  box-sizing: border-box;
}

.input-slot-stack {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  margin-bottom: 8px;
}

.input-box {
  background: transparent;
  border: none;
  overflow: visible;
  min-height: 48px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  transition: box-shadow 0.18s ease, background-color 0.18s ease;
}

.input-box-drop-active {
  background: var(--app-fill-hover);
  box-shadow: inset 0 0 0 1px rgba(96, 165, 250, 0.24);
}

.input-textarea {
  width: 100%;
  height: 40px;
  min-height: 40px;
  max-height: 120px;
  background: transparent;
  padding: 4px 4px;
  box-sizing: border-box;
  font-size: 15px;
  color: var(--app-text-primary);
  line-height: 20px;
  border: none;
  outline: none;
  resize: none;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.input-textarea::placeholder {
  color: var(--app-text-secondary);
}

.input-textarea-desktop {
  height: 48px;
  min-height: 48px;
  max-height: 120px;
  padding: 2px 0 0;
  font-size: 15px;
  line-height: 22px;
}

.input-textarea::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.attachment-native-input {
  position: fixed;
  left: -9999px;
  top: -9999px;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 2px 0 2px;
  box-sizing: border-box;
}

.input-toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.attachment-trigger {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: transparent;
  border: none;
  color: var(--app-text-secondary);
}

.attachment-trigger:active {
  opacity: 0.72;
}

.toolbar-text-btn {
  height: 30px;
  padding: 0 10px;
  border-radius: 8px;
  background: var(--app-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 1px solid var(--app-border-color);
}

.toolbar-text-btn:active {
  background: var(--app-fill-hover);
}

.toolbar-text-btn-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toolbar-text-btn-done {
  background: var(--app-success-soft);
  border-color: rgba(134, 239, 172, 0.28);
}

.toolbar-text-btn-label {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1;
  white-space: nowrap;
}

.input-toolbar-right {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.agent-selector-wrap {
  position: relative;
}

.agent-trigger {
  max-width: 132px;
  height: 38px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-trigger-active {
  border-color: rgba(96, 165, 250, 0.32);
  background: var(--app-fill-hover);
}

.agent-trigger-locked {
  opacity: 0.78;
}

.agent-trigger-label {
  max-width: 108px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1;
  color: var(--app-text-primary);
  font-weight: 600;
}

.agent-popup {
  position: absolute;
  right: 0;
  bottom: calc(100% + 10px);
  width: calc(100vw - 32px);
  max-width: 320px;
  max-height: 320px;
  border-radius: 18px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-elevated);
  overflow: hidden;
}

.agent-popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 10px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.agent-popup-title {
  font-size: 13px;
  line-height: 1;
  color: var(--app-text-primary);
  font-weight: 700;
}

.agent-popup-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.agent-popup-link {
  font-size: 12px;
  line-height: 1;
  color: var(--app-accent);
  font-weight: 600;
}

.agent-popup-list {
  max-height: 260px;
}

.agent-option {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.agent-option-active {
  background: var(--app-fill-hover);
}

.agent-option-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-option-title {
  font-size: 13px;
  line-height: 1.4;
  color: var(--app-text-primary);
  font-weight: 600;
}

.agent-option-subtitle {
  font-size: 12px;
  line-height: 1.5;
  color: var(--app-text-secondary);
}

.agent-option-badge {
  flex-shrink: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  line-height: 1;
  font-weight: 600;
}

.agent-popup-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
}

.agent-popup-empty-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--app-text-secondary);
  text-align: center;
}

.agent-popup-empty-btn {
  min-width: 112px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
  display: flex;
  align-items: center;
  justify-content: center;
}

.agent-popup-empty-btn-text {
  font-size: 12px;
  line-height: 1;
  color: var(--app-text-primary);
  font-weight: 600;
}

.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 150ms, transform 120ms;
}

.send-btn:active {
  transform: scale(0.92);
}

.send-btn-active {
  background: #f97316;
}

.send-btn-active:hover {
  background: #ea580c;
}

.send-btn-disabled {
  background: var(--app-fill-soft);
  cursor: default;
}

.send-btn-stop {
  background: #ef4444;
}

.send-btn-stop:hover {
  background: #dc2626;
}

@media (min-width: 600px) {
  .input-area {
    left: calc(var(--sidebar-width, 0px) + 20px);
    right: calc(20px + var(--chat-input-desktop-right-inset, 0px));
    width: auto;
    bottom: 20px;
    background: transparent;
    box-shadow: none;
    border-top-left-radius: 0;
    border-top-right-radius: 0;
    padding-bottom: 0;
    transition:
      left var(--sidebar-motion-duration, 320ms) var(--sidebar-motion-ease, cubic-bezier(0.18, 0.84, 0.32, 1)),
      right var(--sidebar-motion-duration, 320ms) var(--sidebar-motion-ease, cubic-bezier(0.18, 0.84, 0.32, 1)),
      bottom 220ms ease,
      opacity 180ms ease;
    will-change: left, right;
  }

  .input-container.input-container-desktop {
    max-width: var(--chat-content-max-width, 768px);
    padding: 0 var(--chat-content-padding-x, 20px);
    transition:
      max-width var(--sidebar-motion-duration, 320ms) var(--sidebar-motion-ease, cubic-bezier(0.18, 0.84, 0.32, 1)),
      transform var(--sidebar-motion-duration, 320ms) var(--sidebar-motion-ease, cubic-bezier(0.18, 0.84, 0.32, 1));
  }

  .input-box-shell.input-box-shell-desktop {
    border: 1px solid var(--app-border-color);
    border-radius: 20px;
    background: var(--app-surface-raised);
    box-shadow:
      0 10px 28px rgba(0, 0, 0, 0.28),
      0 2px 8px rgba(0, 0, 0, 0.18);
    backdrop-filter: blur(14px);
    padding: 10px 14px 12px;
  }

  .input-slot-stack {
    margin-bottom: 10px;
  }

  .input-toolbar.input-toolbar-desktop {
    padding: 6px 0 0;
    margin-top: 4px;
  }

  .input-toolbar-left.input-toolbar-left-desktop {
    flex: 1;
    flex-wrap: wrap;
    gap: 10px;
  }

  .attachment-trigger {
    width: 32px;
    height: 32px;
  }

  .input-toolbar-right.input-toolbar-right-desktop {
    gap: 10px;
  }

  .toolbar-text-btn {
    height: 32px;
    border-radius: 999px;
    padding: 0 12px;
  }

  .toolbar-text-btn-label {
    font-size: 12px;
    font-weight: 500;
  }

  .agent-trigger {
    max-width: 160px;
    height: 42px;
    padding: 0 14px;
  }

  .agent-trigger-label {
    max-width: 132px;
  }

  .send-btn.send-btn-desktop {
    width: 42px;
    min-width: 42px;
    height: 42px;
    border-radius: 14px;
  }

  .send-btn-active {
    background: linear-gradient(135deg, #f97316, #ea580c);
    color: #ffffff;
  }

  .send-btn-active:hover {
    background: linear-gradient(135deg, #ea580c, #c2410c);
  }

  .send-btn-disabled {
    background: var(--app-fill-soft);
    color: var(--app-text-secondary);
  }

  .send-btn-stop {
    background: linear-gradient(135deg, #ef4444, #dc2626);
    color: #ffffff;
  }

  .send-btn-stop:hover {
    background: linear-gradient(135deg, #dc2626, #b91c1c);
  }
}

/* #ifdef MP-WEIXIN */
.input-area {
  box-shadow: 0 -6px 24px rgba(0, 0, 0, 0.24);
}
/* #endif */
</style>
