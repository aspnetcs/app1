<template>
  <view v-if="visible" class="captcha-root">
    <view class="captcha-backdrop" @click="handleBackdropClickActivate" @tap="handleBackdropClickActivate"></view>
    <view class="captcha-modal" :class="{ 'captcha-modal-enter': showAnim }">
      <view class="captcha-header">
        <text class="captcha-title">安全验证</text>
        <view class="captcha-header-right">
          <view class="captcha-refresh" :class="{ 'captcha-refreshing': refreshing }" @click="handleRefreshActivate" @tap.prevent="handleRefreshActivate">
            <view class="captcha-refresh-icon">
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="refresh" :size="18" color="currentColor" :stroke-width="2" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 2v6h-6"/>
                <path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
                <path d="M3 22v-6h6"/>
                <path d="M21 12a9 9 0 0 1-15 6.7L3 16"/>
              </svg>
              <!-- #endif -->
            </view>
          </view>
          <view class="captcha-close" @click="handleCloseActivate" @tap.prevent="handleCloseActivate">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="close" :size="16" color="currentColor" :stroke-width="2.5" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <path d="M18 6L6 18"/>
              <path d="M6 6l12 12"/>
            </svg>
            <!-- #endif -->
          </view>
        </view>
      </view>

      <view class="captcha-body">
        <view v-if="loading" class="captcha-loading">
          <text class="captcha-loading-text">加载中...</text>
        </view>
        <slot v-else />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

defineProps<{
  visible: boolean
  showAnim: boolean
  refreshing: boolean
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refresh'): void
}>()

const runCompatAction = createCompatActionRunner()

function handleBackdropClick() {
  emit('close')
}

function handleBackdropClickActivate(event?: CompatEventLike) {
  runCompatAction('captcha-shell-backdrop', event, () => {
    handleBackdropClick()
  })
}

function onCloseClick() {
  emit('close')
}

function handleCloseActivate(event?: CompatEventLike) {
  runCompatAction('captcha-shell-close', event, () => {
    onCloseClick()
  })
}

function onRefreshClick() {
  emit('refresh')
}

function handleRefreshActivate(event?: CompatEventLike) {
  runCompatAction('captcha-shell-refresh', event, () => {
    onRefreshClick()
  })
}
</script>

<style scoped>
.captcha-root {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: fadeIn 150ms ease both;
}

.captcha-backdrop {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
}

.captcha-modal {
  position: relative;
  z-index: 1;
  width: 310px;
  max-width: calc(100vw - 24px);
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
  opacity: 0;
  transform: translate3d(0, 15px, 0) scale(0.97);
}

/* #ifdef MP-WEIXIN */
.captcha-root {
  padding: 12px;
  box-sizing: border-box;
}

.captcha-modal {
  width: 100%;
  max-width: 360px;
}
/* #endif */

.captcha-modal-enter {
  animation: modalSlideUp 250ms cubic-bezier(0.2, 0.9, 0.2, 1) forwards;
}

.captcha-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 8px;
}

.captcha-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.captcha-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.captcha-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #9ca3af;
  cursor: pointer;
  transition: background 150ms ease, color 150ms ease;
}

.captcha-close:hover {
  background: #f3f4f6;
  color: #374151;
}

.captcha-refresh {
  padding: 4px;
  transition: transform 300ms ease;
  cursor: pointer;
}

.captcha-refreshing {
  animation: spin 800ms linear infinite;
}

.captcha-refresh-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
}

.captcha-body {
  padding: 4px 16px 14px;
  min-height: 140px;
}

.captcha-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 140px;
}

.captcha-loading-text {
  font-size: 14px;
  color: #999;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes modalSlideUp {
  from {
    opacity: 0;
    transform: translate3d(0, 15px, 0) scale(0.97);
  }

  to {
    opacity: 1;
    transform: translate3d(0, 0, 0) scale(1);
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
