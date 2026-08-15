<template>
  <view v-if="visible" class="spd-backdrop" @click="handleSkipActivate" @tap="handleSkipActivate"></view>
  <view v-if="visible" class="spd-wrap">
    <view class="spd-dialog" @click.stop @tap.stop>
      <view class="spd-header">
        <text class="spd-title">{{ titleText }}</text>
        <text class="spd-subtitle">{{ subtitleText }}</text>
      </view>

      <view class="spd-form">
        <input
          v-model="pwd"
          class="spd-input"
          :placeholder="pwdPlaceholder"
          :password="!showPwd"
          @confirm="handleSubmit"
        />
        <input
          v-model="confirmPwd"
          class="spd-input"
          :placeholder="confirmPlaceholder"
          :password="!showPwd"
          @confirm="handleSubmit"
        />

        <button
          class="spd-submit-btn"
          :disabled="!canSubmit || loading"
          :loading="loading"
          @click="handleSubmitActivate"
          @tap="handleSubmitActivate"
        >
          {{ submitText }}
        </button>

        <view class="spd-skip" @click="handleSkipActivate" @tap="handleSkipActivate">
          <text class="spd-skip-text">{{ skipText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import * as authApi from '@/api/auth'
import { extractErrorMessage } from '@/utils/errorMessage'
import { logger } from '@/utils/logger'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const pwd = ref('')
const confirmPwd = ref('')
const showPwd = ref(false)
const loading = ref(false)
const runCompatAction = createCompatActionRunner()

const titleText = computed(() => '\u8bbe\u7f6e\u5bc6\u7801')
const subtitleText = computed(() => '\u8bbe\u7f6e\u5bc6\u7801\u540e\u53ef\u4f7f\u7528\u5bc6\u7801\u767b\u5f55')
const pwdPlaceholder = computed(() => '\u8bf7\u8f93\u5165\u5bc6\u7801\uff08\u81f3\u5c116\u4f4d\uff09')
const confirmPlaceholder = computed(() => '\u786e\u8ba4\u5bc6\u7801')
const submitText = computed(() => '\u8bbe\u7f6e\u5bc6\u7801')
const skipText = computed(() => '\u7a0d\u540e\u8bbe\u7f6e')

const canSubmit = computed(() => {
  return pwd.value.length >= 6 && confirmPwd.value.length >= 6
})

watch(() => props.visible, (v) => {
  if (v) {
    pwd.value = ''
    confirmPwd.value = ''
    loading.value = false
  }
})

async function handleSubmit() {
  if (!canSubmit.value || loading.value) return
  if (pwd.value !== confirmPwd.value) {
    uni.showToast({ title: '\u4e24\u6b21\u5bc6\u7801\u4e0d\u4e00\u81f4', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await authApi.setPassword({ password: pwd.value })
    uni.showToast({ title: '\u5bc6\u7801\u8bbe\u7f6e\u6210\u529f', icon: 'success' })
    emit('close')
  } catch (error: unknown) {
    logger.warn('[SetPasswordDialog] setPassword failed:', error)
    uni.showToast({
      title: extractErrorMessage(error, '\u8bbe\u7f6e\u5931\u8d25'),
      icon: 'none',
    })
  } finally {
    loading.value = false
  }
}

function handleSubmitActivate(event?: CompatEventLike) {
  runCompatAction('set-password-submit', event, () => {
    void handleSubmit()
  })
}

function handleSkip() {
  emit('close')
}

function handleSkipActivate(event?: CompatEventLike) {
  runCompatAction('set-password-skip', event, () => {
    handleSkip()
  })
}
</script>

<style scoped>
.spd-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 910;
  animation: spdFadeIn 150ms ease;
}

.spd-wrap {
  position: fixed;
  inset: 0;
  z-index: 911;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.spd-dialog {
  pointer-events: auto;
  width: 380px;
  max-width: 90vw;
  background: #ffffff;
  border-radius: 16px;
  padding: 40px 36px 32px;
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.18);
  animation: spdSlideUp 200ms cubic-bezier(0.16, 1, 0.3, 1);
  font-family: -apple-system, "Segoe UI", "Helvetica Neue", sans-serif;
}

.spd-header {
  text-align: center;
  margin-bottom: 24px;
}

.spd-title {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: #0d0d0d;
  margin-bottom: 8px;
}

.spd-subtitle {
  display: block;
  font-size: 14px;
  color: #6e6e80;
  line-height: 1.5;
}

.spd-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.spd-input {
  height: 52px;
  padding: 0 16px;
  border: 1px solid #c2c8d0;
  border-radius: 26px;
  font-size: 15px;
  color: #0d0d0d;
  outline: none;
  transition: border-color 150ms ease;
}

.spd-input:focus {
  border-color: #0d0d0d;
}

.spd-submit-btn {
  height: 52px;
  border-radius: 26px;
  background: #0d0d0d;
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: background 150ms ease, opacity 150ms ease;
  margin-top: 4px;
}

.spd-submit-btn::after {
  border: none;
}

.spd-submit-btn:active:not([disabled]) {
  background: #333333;
}

.spd-submit-btn[disabled] {
  opacity: 0.4;
  cursor: not-allowed;
}

.spd-skip {
  text-align: center;
  cursor: pointer;
  padding: 4px 0;
}

.spd-skip-text {
  font-size: 14px;
  color: #6e6e80;
}

.spd-skip-text:hover {
  text-decoration: underline;
}

@keyframes spdFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes spdSlideUp {
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
