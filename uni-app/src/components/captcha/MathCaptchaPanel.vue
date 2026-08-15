<template>
  <view class="captcha-math-mode">
    <text class="captcha-hint">请计算下方结果</text>
    <view class="captcha-math-question">
      <image v-if="mathImage" class="captcha-math-image" :src="mathImage" mode="widthFix" />
      <text v-else class="captcha-math-text">{{ mathQuestion }}</text>
    </view>
    <view class="captcha-math-input-row">
      <input
        :model-value="mathAnswer"
        class="captcha-math-input"
        type="number"
        maxlength="4"
        placeholder="输入答案"
        @input="onMathInput"
        @confirm="$emit('confirm')"
      />
    </view>
    <button
      class="captcha-btn"
      :disabled="!mathAnswer || isSubmitting"
      @click="handleConfirmActivate"
      @tap="handleConfirmActivate"
    >
      确认
    </button>
  </view>
</template>

<script setup lang="ts">
import { createCompatActionRunner } from '@/utils/h5EventCompat'

defineProps<{
  mathImage: string
  mathQuestion: string
  mathAnswer: string
  isSubmitting: boolean
}>()

const emit = defineEmits<{
  (e: 'update:math-answer', value: string): void
  (e: 'confirm'): void
}>()

const runCompatAction = createCompatActionRunner()

type MathInputDetail =
  | {
      value?: string | number
    }
  | string
  | number
  | undefined

function onMathInput(event: Event) {
  const detail = (event as Event & { detail?: MathInputDetail }).detail
  const targetValue = (event.target as HTMLInputElement | null)?.value
  const value =
    typeof detail === 'object' && detail !== null && 'value' in detail
      ? detail.value
      : detail ?? targetValue
  emit('update:math-answer', value == null ? '' : String(value))
}

function handleConfirmActivate(event?: Event) {
  runCompatAction('captcha-math-confirm', event, () => {
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

.captcha-math-image {
  width: 100%;
  display: block;
}

.captcha-math-question {
  text-align: center;
  margin-bottom: 12px;
}

.captcha-math-text {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: #1a1a1a;
  padding: 17px 0;
  letter-spacing: 1px;
}

.captcha-math-input-row {
  margin-bottom: 10px;
}

.captcha-math-input {
  width: 100%;
  height: 42px;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 17px;
  text-align: center;
  box-sizing: border-box;
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
