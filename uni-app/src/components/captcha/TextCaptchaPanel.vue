<template>
  <view class="captcha-text-mode">
    <text class="captcha-hint">
      请按顺序点击：
      <text class="captcha-target-chars">{{ targetCharsDisplay }}</text>
    </text>
    <view
      id="textImageWrap"
      class="captcha-text-image-wrap"
      @click="handleTouch"
      @tap="handleTouch"
    >
      <image class="captcha-text-bg" :src="bgImage" mode="widthFix" />
      <view
        v-for="(point, index) in clickedPoints"
        :key="index"
        class="captcha-click-marker"
        :style="`left:${point.displayX}px;top:${point.displayY}px;`"
        @click.stop="handleRemovePoint(index, $event)"
        @tap.stop="handleRemovePoint(index, $event)"
      >
        <text class="captcha-marker-num">{{ index + 1 }}</text>
      </view>
    </view>
    <button
      class="captcha-btn captcha-text-confirm"
      :disabled="clickedPoints.length < targetCharsArr.length || isSubmitting"
      @click="handleConfirm"
      @tap="handleConfirm"
    >
      确认 ({{ clickedPoints.length }}/{{ targetCharsArr.length }})
    </button>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CaptchaClickPoint } from './captchaTypes'
import { createCompatActionRunner } from '@/utils/h5EventCompat'

const props = defineProps<{
  bgImage: string
  targetCharsArr: string[]
  clickedPoints: CaptchaClickPoint[]
  isSubmitting: boolean
}>()

const emit = defineEmits<{
  (e: 'touch', event: Event): void
  (e: 'remove-point', index: number): void
  (e: 'confirm'): void
}>()

const runCompatAction = createCompatActionRunner()

const targetCharsDisplay = computed(() => props.targetCharsArr.join(' '))

function handleTouch(event?: Event) {
  if (!event) return
  runCompatAction('text-captcha-touch', event, () => {
    emit('touch', event)
  })
}

function handleRemovePoint(index: number, event?: Event) {
  runCompatAction(`text-captcha-remove:${index}`, event, () => {
    emit('remove-point', index)
  })
}

function handleConfirm(event?: Event) {
  runCompatAction('text-captcha-confirm', event, () => {
    emit('confirm')
  })
}
</script>

<style scoped>
.captcha-hint {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 10px;
  text-align: center;
}

.captcha-target-chars {
  color: #4c7cf6;
  font-weight: 600;
  letter-spacing: 2px;
}

.captcha-text-image-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 8px;
  margin-bottom: 10px;
}

.captcha-text-bg {
  width: 100%;
  display: block;
}

.captcha-click-marker {
  position: absolute;
  width: 24px;
  height: 24px;
  background: #4c7cf6;
  border: 2px solid #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: translate(-50%, -50%);
  box-shadow: 0 2px 6px rgba(76, 124, 246, 0.4);
  z-index: 3;
}

.captcha-marker-num {
  font-size: 12px;
  color: #fff;
  font-weight: 600;
}

.captcha-btn {
  width: 100%;
  height: 42px;
  line-height: 42px;
  text-align: center;
  font-size: 15px;
  font-weight: 500;
  border: none;
  border-radius: 8px;
  background: #1a1a1a;
  color: #fff;
  transition: opacity 120ms ease;
}

.captcha-btn::after {
  border: none;
}

.captcha-btn[disabled] {
  opacity: 0.45;
}
</style>
