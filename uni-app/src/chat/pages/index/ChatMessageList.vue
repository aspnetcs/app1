<template>
  <view class="chat-message-shell" :style="chatMessageShellStyle">
    <scroll-view
      scroll-y
      enable-flex
      class="chat-messages"
      :style="chatMessagesStyle"
      :scroll-into-view="scrollIntoView"
      :scroll-top="scrollTop"
      :lower-threshold="90"
      scroll-with-animation
      @scroll="onScroll"
      @scrolltolower="onScrollToLower"
      @touchmove="onUserScrollIntent"
    >
      <view class="messages-inner" :style="messagesInnerStyle">
        <view
          v-for="(msg, index) in messages"
          :id="`msg-${msg.id}`"
          :key="msg.id"
          class="message-row"
          :class="[msg.role === 'user' ? 'message-row-user' : 'message-row-assistant']"
        >
          <view v-if="shouldShowMessageTime(index)" class="message-time-divider">
            <text class="message-time-text">{{ formatMessageTime(msg.createdAt) }}</text>
          </view>

          <view v-if="msg.role === 'user'" class="msg-user">
            <view class="msg-user-bubble">
              <text class="msg-user-text">{{ msg.content }}</text>
              <AttachmentList
                v-if="msg.attachments?.length"
                :items="toChipItems(msg.attachments)"
                class="msg-user-attachments"
              />
            </view>
          </view>

          <view v-else class="msg-assistant">
            <view class="msg-assistant-avatar-rail">
              <image v-if="modelAvatarPath" :src="modelAvatarPath" class="msg-assistant-avatar" mode="aspectFit" />
              <view v-else class="msg-assistant-avatar msg-assistant-avatar-fallback"></view>
            </view>
            <view class="msg-assistant-main">
            <view v-if="hasReasoning(msg)" class="thinking-block" :class="{ 'thinking-active': msg.reasoningStatus === 'thinking' }">
              <view class="thinking-header" @click.stop="handleThinkingToggle(msg.id, $event)" @tap.stop="handleThinkingToggle(msg.id, $event)">
                <view class="thinking-icon-wrap">
                  <view v-if="msg.reasoningStatus === 'thinking'" class="thinking-spinner"></view>
                  <view v-else class="thinking-chevron" :class="{ 'thinking-chevron-open': expandedThinking[msg.id] }">
                    <text class="chevron-text">></text>
                  </view>
                </view>
                <text class="thinking-label">
                  {{ msg.reasoningStatus === 'thinking' ? thinkingElapsedText(msg) : thinkingDoneText(msg) }}
                </text>
              </view>
              <view v-if="expandedThinking[msg.id] || msg.reasoningStatus === 'thinking'" class="thinking-body">
                <text class="thinking-content-text">{{ msg.reasoningContent }}</text>
              </view>
            </view>

            <view v-if="isStreamingMessage(msg, index) && !hasReasoning(msg)" class="thinking-block thinking-active">
              <view class="thinking-header">
                <view class="thinking-icon-wrap">
                  <view class="thinking-spinner"></view>
                </view>
                <text class="thinking-label">思考中...</text>
              </view>
            </view>

            <view v-if="msg.content || msg.blocks?.length || (!isStreamingMessage(msg, index) && !hasReasoning(msg))" class="msg-assistant-content">
              <MessageBlocksRenderer :blocks="buildRenderableMessageBlocks(msg)" @open-citations="openCitations" />
            </view>

            <view v-if="hasMessageCitations(msg) && shouldShowActions(index)" class="msg-citation-actions">
              <view
                class="msg-action-btn msg-action-text-btn"
                title="来源"
                @click.stop="openMessageCitations(msg, $event)"
                @tap.stop="openMessageCitations(msg, $event)"
              >
                <text class="msg-action-text">来源</text>
              </view>
            </view>

            <view v-if="shouldShowActions(index)" class="msg-actions">
              <view
                v-if="msg.status === 'error'"
                class="msg-action-btn msg-action-text-btn"
                title="详情"
                @click.stop="handleMessageActionActivate(msg, 'error-details', $event)"
                @tap.stop="handleMessageActionActivate(msg, 'error-details', $event)"
              >
                <text class="msg-action-text">详情</text>
              </view>
              <view
                v-if="canEditMessage(msg)"
                class="msg-action-btn"
                title="编辑"
                @click.stop="handleMessageActionActivate(msg, 'edit', $event)"
                @tap.stop="handleMessageActionActivate(msg, 'edit', $event)"
              >
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="edit" :size="14" color="var(--app-text-secondary)" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
                <IconEdit :size="14" color="var(--app-text-secondary)" />
              <!-- #endif -->
              </view>
              <view
                class="msg-action-btn"
                title="复制"
                @click.stop="handleMessageActionActivate(msg, 'copy', $event)"
                @tap.stop="handleMessageActionActivate(msg, 'copy', $event)"
              >
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="copy" :size="14" color="var(--app-text-secondary)" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
                <IconCopy :size="14" color="var(--app-text-secondary)" />
              <!-- #endif -->
              </view>
              <!-- #ifndef MP-WEIXIN -->
              <view class="msg-action-btn" title="朗读">
                <AudioPlayer :text="msg.content" />
              </view>
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <!-- #endif -->
            </view>
            </view>
            <ResponseTimelineRail
              v-if="msg.versionList && msg.versionList.length > 1"
              :versions="msg.versionList"
              :current-index="msg.currentVersionIndex ?? 0"
              @switch="(idx: number) => emit('switch-version', msg, idx)"
            />
          </view>
        </view>
      </view>
    </scroll-view>

    <CitationDrawer :visible="citationsVisible" :citations="activeCitations" @close="closeCitations" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, onUnmounted } from 'vue'
import type { Message } from '@/stores/chat'
import type { CitationSource } from '@/utils/messageBlocks'
import {
  buildRenderableMessageBlocks,
  collectMessageCitations,
} from '@/utils/messageBlocks'
import CitationDrawer from '@/components/chat/CitationDrawer.vue'
import MessageBlocksRenderer from '@/components/chat/MessageBlocksRenderer.vue'
import ResponseTimelineRail from '@/components/chat/ResponseTimelineRail.vue'
import AttachmentList from '@/components/attachments/AttachmentList.vue'
import type { AttachmentChipItem } from '@/components/attachments/AttachmentChip.vue'
import type { ChatAttachment } from '@/api/types/chat'
// #ifndef MP-WEIXIN
import AudioPlayer from '@/components/audio/AudioPlayer.vue'
import IconCopy from '@/components/icons/IconCopy.vue'
import IconEdit from '@/components/icons/IconEdit.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

const TIMESTAMP_GAP_MS = 60 * 1000
type ScrollEventPayload = { detail?: { deltaY?: number; scrollTop?: number } }
type MessageActionKind = 'edit' | 'copy' | 'error-details'

const props = defineProps<{
  messages: Message[]
  isGenerating: boolean
  contentBottomPaddingPx?: number
  scrollIntoView?: string
  scrollTop?: number
  modelDisplayName?: string
  modelAvatarPath?: string
  viewportHeightPx?: number
}>()

const viewportHeightStyle = computed(() => {
  const raw = Number(props.viewportHeightPx)
  const height = Number.isFinite(raw) ? Math.max(0, Math.round(raw)) : 0
  return height > 0 ? `${height}px` : ''
})

const chatMessageShellStyle = computed(() => {
  if (!viewportHeightStyle.value) return {}
  return {
    height: viewportHeightStyle.value,
    minHeight: viewportHeightStyle.value,
  }
})

const chatMessagesStyle = computed(() => {
  if (!viewportHeightStyle.value) return {}
  return {
    height: viewportHeightStyle.value,
  }
})

const messagesInnerStyle = computed(() => {
  const raw = Number(props.contentBottomPaddingPx)
  const bottom = Number.isFinite(raw) ? Math.max(0, Math.round(raw)) : 180
  return {
    paddingBottom: `${bottom}px`,
  }
})

const emit = defineEmits<{
  (e: 'scroll', event: ScrollEventPayload): void
  (e: 'scrolltolower'): void
  (e: 'edit', content: string): void
  (e: 'copy', content: string): void
  (e: 'switch-version', message: Message, index: number): void
  (e: 'error-details', message: Message): void
  (e: 'user-scroll-intent'): void
}>()

const runCompatAction = createCompatActionRunner()
const expandedThinking = reactive<Record<string, boolean>>({})

function toChipItems(items: ChatAttachment[]): AttachmentChipItem[] {
  return items.map(a => ({
    fileId: a.fileId,
    originalName: a.originalName || 'file',
    kind: a.kind as AttachmentChipItem['kind'],
    mimeType: a.mimeType,
  }))
}

const citationsVisible = ref(false)
const activeCitations = ref<CitationSource[]>([])
const now = ref(Date.now())
let tickTimer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  tickTimer = setInterval(() => { now.value = Date.now() }, 500)
})

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})

function hasReasoning(msg: Message): boolean {
  return Boolean(msg.reasoningContent)
}

function hasMessageCitations(message: Message) {
  return collectMessageCitations(message).length > 0
}

function openCitations(citations: CitationSource[]) {
  if (!citations.length) return
  activeCitations.value = citations
  citationsVisible.value = true
}

function closeCitations() {
  citationsVisible.value = false
  activeCitations.value = []
}

function openMessageCitations(message: Message, event?: CompatEventLike) {
  runCompatAction(`message-citations:${message.id}`, event, () => {
    openCitations(collectMessageCitations(message))
  })
}

function toggleThinking(msgId: string) {
  expandedThinking[msgId] = !expandedThinking[msgId]
}

function handleThinkingToggle(msgId: string, event?: CompatEventLike) {
  runCompatAction(`thinking-toggle:${msgId}`, event, () => {
    toggleThinking(msgId)
  })
}

function thinkingElapsedText(msg: Message): string {
  if (!msg.reasoningStartTime) return '思考中...'
  const elapsed = Math.max(0, Math.floor((now.value - msg.reasoningStartTime) / 1000))
  return `思考中 ${elapsed}s...`
}

function thinkingDoneText(msg: Message): string {
  if (!msg.reasoningStartTime) return '推理完成'
  const elapsed = Math.max(1, Math.ceil((Date.now() - msg.reasoningStartTime) / 1000))
  return `推理完成 (${elapsed}s)`
}

function onScroll(event: ScrollEventPayload) {
  emit('scroll', event)
}

function onUserScrollIntent() {
  emit('user-scroll-intent')
}

function onScrollToLower() {
  emit('scrolltolower')
}

function canEditMessage(message: Message) {
  return message.role === 'user' && Boolean(message.content.trim())
}

function handleMessageActionActivate(message: Message, action: MessageActionKind, event?: CompatEventLike) {
  runCompatAction(`message-action:${message.id}:${action}`, event, () => {
    if (action === 'edit') {
      if (!canEditMessage(message)) return
      emit('edit', message.content)
      return
    }
    if (action === 'copy') {
      emit('copy', message.content)
      return
    }
    if (action === 'error-details') {
      emit('error-details', message)
    }
  })
}

function isStreamingMessage(message: Message, index: number) {
  return props.isGenerating && index === props.messages.length - 1 && !message.content && !message.blocks?.length
}

function shouldShowActions(index: number) {
  return !props.isGenerating || index !== props.messages.length - 1
}

function shouldShowMessageTime(index: number) {
  if (index === 0) return true
  const current = props.messages[index]
  const previous = props.messages[index - 1]
  if (!current || !previous) return false
  return current.createdAt - previous.createdAt >= TIMESTAMP_GAP_MS
}

function formatMessageTime(timestamp: number) {
  const date = new Date(timestamp)
  const nowDate = new Date()
  const isToday =
    date.getFullYear() === nowDate.getFullYear()
    && date.getMonth() === nowDate.getMonth()
    && date.getDate() === nowDate.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')

  if (isToday) {
    return `${hour}:${minute}`
  }

  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}
</script>
<style scoped>
.chat-message-shell {
  position: absolute;
  display: flex;
  flex-direction: column;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  width: 100%;
  height: 100%;
  padding: var(--app-space-vertical, 16px) 0;
  min-height: 0;
  box-sizing: border-box;
}

.messages-inner {
  max-width: var(--chat-content-max-width, 768px);
  width: 100%;
  margin: 0 auto;
  padding: 0 var(--chat-content-padding-x, 20px);
  padding-bottom: 140px;
  box-sizing: border-box;
}

.message-row {
  margin-bottom: var(--app-space-vertical, 20px);
  display: flex;
  flex-direction: column;
}

.message-row-user {
  align-items: flex-end;
}

.message-row-assistant {
  align-items: flex-start;
}

.message-time-divider {
  width: 100%;
  display: flex;
  justify-content: center;
  align-self: center;
  margin-bottom: 10px;
}

.message-time-text {
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--app-surface-muted);
  font-size: 11px;
  color: var(--app-text-secondary);
  line-height: 18px;
}

.msg-user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  max-width: 85%;
}

.msg-user-bubble {
  background: var(--app-surface-muted);
  border-radius: 20px;
  border-bottom-right-radius: 6px;
  padding: var(--app-space-vertical, 12px) var(--app-space-horizontal, 18px);
}

.msg-user-text {
  font-size: 14px;
  color: var(--app-text-primary);
  line-height: 1.6;
  word-break: break-word;
}

.msg-user-attachments {
  margin-top: var(--app-space-vertical, 8px);
}

.msg-assistant {
  width: 100%;
  max-width: 95%;
  display: flex;
  gap: var(--app-space-horizontal, 12px);
  align-items: flex-start;
}

.msg-assistant-avatar-rail {
  width: 34px;
  display: flex;
  justify-content: center;
  padding-top: 1px;
  flex-shrink: 0;
}

.msg-assistant-main {
  flex: 1;
  min-width: 0;
}

.msg-assistant-avatar {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.msg-assistant-avatar-fallback {
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
  box-shadow: none;
}

.thinking-block {
  margin-bottom: var(--app-space-vertical, 8px);
  border-left: 2px solid var(--app-border-color);
  padding-left: var(--app-space-horizontal, 12px);
}

.thinking-block.thinking-active {
  border-left-color: var(--app-fill-soft);
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: var(--app-space-horizontal, 8px);
  cursor: pointer;
  padding: 4px 0;
  user-select: none;
}

.thinking-icon-wrap {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.thinking-spinner {
  width: 16px;
  height: 16px;
  display: block;
  box-sizing: border-box;
  border: 2px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--app-text-secondary);
  border-right-color: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  transform-origin: center;
  will-change: transform;
  animation: thinkingSpin 1.65s linear infinite;
}

@keyframes thinkingSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.thinking-chevron {
  transition: transform 200ms ease;
  transform: rotate(0deg);
  display: flex;
  align-items: center;
  justify-content: center;
}

.thinking-chevron-open {
  transform: rotate(90deg);
}

.chevron-text {
  font-size: 12px;
  color: var(--app-text-secondary);
  font-family: monospace;
  line-height: 1;
}

.thinking-label {
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.4;
}

.thinking-active .thinking-label {
  background: linear-gradient(90deg, var(--app-text-secondary) 0%, var(--app-text-primary) 50%, var(--app-text-secondary) 100%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: thinkingShimmer 3.2s ease-in-out infinite;
}

@keyframes thinkingShimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.thinking-body {
  margin-top: 6px;
  padding: 10px 12px;
  background: var(--app-surface-muted);
  border-radius: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.thinking-content-text {
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-assistant-content {
  font-size: 14px;
  color: var(--app-text-primary);
  line-height: 1.7;
  word-break: break-word;
}

.msg-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
}

.msg-action-btn {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 120ms;
}

.msg-action-btn:hover {
  background: var(--app-fill-hover);
}

.msg-action-text-btn {
  width: auto;
  padding: 0 8px;
}

.msg-action-text {
  font-size: 12px;
  color: var(--app-text-secondary);
  line-height: 1;
}

@media (max-width: 1024px) {
  .messages-inner {
    padding: 0 var(--chat-content-padding-x, 16px);
    padding-bottom: 140px;
  }
}

@media (max-width: 640px) {
  .messages-inner {
    padding: 0 var(--chat-content-padding-x, 12px);
    padding-bottom: 132px;
  }
}

.msg-citation-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
</style>

