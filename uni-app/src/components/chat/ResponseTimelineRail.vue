<template>
  <view v-if="versions.length > 1" class="response-timeline-rail">
    <view class="rail-track">
      <view
        v-for="(ver, idx) in versions"
        :key="ver.id"
        class="rail-node"
        :class="{ active: idx === currentIndex }"
        @click="selectVersion(idx)"
        @tap="selectVersion(idx)"
        @mouseenter="hoverIdx = idx"
        @mouseleave="hoverIdx = null"
        @touchstart.passive="onTouchStart(idx)"
        @touchmove.passive="onTouchMove($event)"
        @touchend.passive="onTouchEnd"
        @touchcancel.passive="onTouchEnd"
      >
        <view class="rail-dot"></view>
      </view>
    </view>
    <text class="rail-label">{{ currentIndex + 1 }}/{{ versions.length }}</text>

    <!-- Desktop hover preview -->
    <!-- #ifndef MP-WEIXIN -->
    <view v-if="hoverIdx !== null && hoverIdx !== currentIndex" class="rail-hover-preview">
      <text class="rail-preview-title">第 {{ hoverIdx + 1 }} 版</text>
      <text class="rail-preview-model">{{ versions[hoverIdx]?.model || 'AI' }}</text>
      <text class="rail-preview-snippet">{{ getSnippet(hoverIdx) }}</text>
    </view>
    <!-- #endif -->

    <!-- Mobile long-press preview -->
    <view v-if="longPressIdx !== null" class="rail-longpress-backdrop" @click="cancelLongPress" @tap="cancelLongPress">
      <view class="rail-longpress-card" @click.stop @tap.stop>
        <text class="rail-longpress-title">第 {{ (previewIdx ?? longPressIdx) + 1 }} 版 / 共 {{ versions.length }} 版</text>
        <text class="rail-longpress-model">{{ versions[previewIdx ?? longPressIdx]?.model || 'AI' }}</text>
        <text class="rail-longpress-snippet">{{ getSnippet(previewIdx ?? longPressIdx) }}</text>
        <view class="rail-longpress-hint">上下滑动切换 / 松手确认</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { MessageVersionItem } from '@/stores/chatState'

const props = defineProps<{
  versions: MessageVersionItem[]
  currentIndex: number
}>()

const emit = defineEmits<{
  (e: 'switch', index: number): void
}>()

const hoverIdx = ref<number | null>(null)
const longPressIdx = ref<number | null>(null)
const previewIdx = ref<number | null>(null)
let longPressTimer: ReturnType<typeof setTimeout> | null = null
let touchStartY = 0

function selectVersion(idx: number) {
  if (idx !== props.currentIndex) {
    emit('switch', idx)
  }
}

function getSnippet(idx: number) {
  const ver = props.versions[idx]
  if (!ver) return ''
  const text = ver.content || ''
  return text.length > 80 ? text.slice(0, 80) + '...' : text
}

function onTouchStart(idx: number) {
  longPressTimer = setTimeout(() => {
    longPressIdx.value = idx
    previewIdx.value = idx
    uni.vibrateShort({})
  }, 500)
  touchStartY = 0
}

function onTouchMove(event: TouchEvent | Event) {
  if (longPressTimer && longPressIdx.value === null) {
    clearTimeout(longPressTimer)
    longPressTimer = null
    return
  }
  if (longPressIdx.value === null) return

  const touch = (event as TouchEvent).touches?.[0]
  if (!touch) return

  if (touchStartY === 0) {
    touchStartY = touch.clientY
    return
  }

  const dy = touch.clientY - touchStartY
  const step = Math.round(dy / 40)
  if (step === 0) return

  const base = longPressIdx.value
  const next = Math.max(0, Math.min(props.versions.length - 1, base + step))
  previewIdx.value = next
}

function onTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
  if (longPressIdx.value !== null && previewIdx.value !== null) {
    if (previewIdx.value !== props.currentIndex) {
      emit('switch', previewIdx.value)
    }
    longPressIdx.value = null
    previewIdx.value = null
  }
}

function cancelLongPress() {
  longPressIdx.value = null
  previewIdx.value = null
}
</script>

<style scoped>
.response-timeline-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
  position: relative;
}

.rail-track {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  position: relative;
  padding: 4px 0;
}

.rail-track::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e5e7eb;
  transform: translateX(-50%);
  border-radius: 1px;
}

.rail-node {
  position: relative;
  z-index: 1;
  cursor: pointer;
  padding: 3px;
}

.rail-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #d1d5db;
  transition: all 150ms;
}

.rail-node.active .rail-dot {
  background: #6366f1;
  box-shadow: 0 0 0 2px #e0e7ff;
  width: 10px;
  height: 10px;
}

.rail-node:hover .rail-dot {
  background: #818cf8;
}

.rail-label {
  font-size: 10px;
  color: #9ca3af;
  white-space: nowrap;
}

.rail-hover-preview {
  position: absolute;
  left: calc(100% + 8px);
  top: 50%;
  transform: translateY(-50%);
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  width: 200px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  pointer-events: none;
  z-index: 50;
}

.rail-preview-title {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}

.rail-preview-model {
  display: block;
  font-size: 11px;
  color: #9ca3af;
  margin-bottom: 4px;
}

.rail-preview-snippet {
  display: block;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.rail-longpress-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rail-longpress-card {
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  width: 300px;
  max-width: 85vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.rail-longpress-title {
  display: block;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 6px;
}

.rail-longpress-model {
  display: block;
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 10px;
}

.rail-longpress-snippet {
  display: block;
  font-size: 13px;
  color: #4b5563;
  line-height: 1.5;
  max-height: 120px;
  overflow: hidden;
}

.rail-longpress-hint {
  margin-top: 12px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}
</style>
