<template>
  <view v-if="visible" class="cpd-backdrop" @click="handleClose" @tap="handleClose"></view>
  <view v-if="visible" class="cpd-wrap">
    <view class="cpd-dialog" @click.stop @tap.stop>
      <view class="cpd-header">
        <text class="cpd-title">{{ pageTitle }}</text>
        <text class="cpd-subtitle">{{ pageSubtitle }}</text>
      </view>

      <view class="cpd-form">
        <input
          v-if="verifyChannel === 'sms'"
          v-model="phone"
          class="cpd-input"
          type="number"
          :maxlength="11"
          placeholder="手机号"
        />
        <input
          v-else-if="verifyChannel === 'email'"
          v-model="email"
          class="cpd-input"
          type="text"
          placeholder="邮箱"
        />

        <view v-if="!isSetPasswordMode" class="cpd-code-row">
          <input
            v-model="code"
            class="cpd-input cpd-code-input"
            type="number"
            :maxlength="6"
            placeholder="验证码"
          />
          <button
            class="cpd-code-btn"
            :disabled="countdown > 0 || !canSendCode"
            @click="handleSendCode"
            @tap="handleSendCode"
          >
            {{ countdown > 0 ? countdown + 's' : '获取验证码' }}
          </button>
        </view>

        <input
          v-model="newPassword"
          class="cpd-input"
          :password="true"
          placeholder="新密码（至少 6 位）"
          @confirm="handleSubmit"
        />

        <view class="cpd-actions">
          <button class="cpd-cancel-btn" @click="handleClose" @tap="handleClose">取消</button>
          <button
            class="cpd-submit-btn"
            :disabled="!canSubmit || loading"
            :loading="loading"
            @click="handleSubmit"
            @tap="handleSubmit"
          >
            {{ submitBtnText }}
          </button>
        </view>
      </view>

      <CaptchaModal ref="captchaRef" @success="onCaptchaSuccess" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, onUnmounted } from 'vue'
import { APP_CONFIG } from '@/config'
import { useAuthStore } from '@/stores/auth'
import { sendSmsCode, sendEmailCode, setPassword, changePassword } from '@/api/auth'
import { isValidPhone, isValidEmail } from '@/utils/format'
import CaptchaModal from '@/components/captcha/CaptchaModal.vue'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const authStore = useAuthStore()
const userPhone = computed(() => authStore.userInfo?.phone || '')
const userEmail = computed(() => authStore.userInfo?.email || '')
const verifyChannel = computed<'sms' | 'email' | 'none'>(() => {
  if (userPhone.value) return 'sms'
  if (userEmail.value) return 'email'
  return 'none'
})
const isSetPasswordMode = computed(() => verifyChannel.value === 'none')

const phone = ref('')
const email = ref('')
const code = ref('')
const newPassword = ref('')
const loading = ref(false)
const countdown = ref(0)
const captchaRef = ref<InstanceType<typeof CaptchaModal>>()
let countdownTimer: ReturnType<typeof setInterval> | null = null

const canSendCode = computed(() => {
  if (verifyChannel.value === 'sms') return isValidPhone(phone.value)
  if (verifyChannel.value === 'email') return isValidEmail(email.value)
  return false
})

const canSubmit = computed(() => {
  if (newPassword.value.length < 6) return false
  if (isSetPasswordMode.value) return true
  return code.value.length >= 4
})

const pageTitle = computed(() => isSetPasswordMode.value ? '设置密码' : '修改密码')
const pageSubtitle = computed(() => isSetPasswordMode.value ? '为你的账号设置登录密码' : '请验证后设置新密码')
const submitBtnText = computed(() => {
  if (loading.value) return '提交中...'
  return isSetPasswordMode.value ? '设置密码' : '确认修改'
})

watch(() => props.visible, (v) => {
  if (v) {
    void authStore.fetchUserInfo()
    phone.value = userPhone.value
    email.value = userEmail.value
    code.value = ''
    newPassword.value = ''
  }
})

watch(userPhone, (v) => { if (v) phone.value = v }, { immediate: true })
watch(userEmail, (v) => { if (v && !userPhone.value) email.value = v }, { immediate: true })

function handleClose() {
  if (loading.value) return
  emit('close')
}

function handleSendCode() {
  if (!canSendCode.value) {
    uni.showToast({ title: verifyChannel.value === 'email' ? '邮箱信息无效' : '手机号信息无效', icon: 'none' })
    return
  }
  captchaRef.value?.open()
}

async function onCaptchaSuccess(token: string) {
  try {
    if (verifyChannel.value === 'email') {
      await sendEmailCode({ email: email.value, purpose: 'change_password', challengeToken: token })
    } else {
      await sendSmsCode({ phone: phone.value, purpose: 'change_password', challengeToken: token })
    }
    uni.showToast({ title: '验证码已发送', icon: 'success' })
    countdown.value = APP_CONFIG.codeCountdown
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0 && countdownTimer) clearInterval(countdownTimer)
    }, 1000)
  } catch {
    // handled by toast
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

async function handleSubmit() {
  if (!canSubmit.value || loading.value) return
  loading.value = true
  try {
    if (isSetPasswordMode.value) {
      await setPassword({ password: newPassword.value })
      uni.showToast({ title: '密码设置成功', icon: 'success' })
    } else {
      await changePassword({
        code: code.value,
        newPassword: newPassword.value,
        purpose: 'change_password',
      })
      uni.showToast({ title: '密码修改成功', icon: 'success' })
    }
    emit('close')
  } catch {
    // handled by toast
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cpd-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 1000;
}

.cpd-wrap {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1001;
  pointer-events: none;
}

.cpd-dialog {
  pointer-events: auto;
  background: #fff;
  border-radius: 16px;
  padding: 28px 24px;
  width: 360px;
  max-width: 90vw;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.18);
}

.cpd-header {
  margin-bottom: 20px;
}

.cpd-title {
  display: block;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 6px;
}

.cpd-subtitle {
  display: block;
  font-size: 13px;
  color: #6b7280;
}

.cpd-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cpd-input {
  height: 44px;
  padding: 0 14px;
  border-radius: 10px;
  background: #f3f4f6;
  font-size: 14px;
  color: #111827;
}

.cpd-code-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.cpd-code-input {
  flex: 1;
}

.cpd-code-btn {
  flex-shrink: 0;
  height: 44px;
  line-height: 44px;
  padding: 0 14px;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  font-size: 13px;
  border: none;
  white-space: nowrap;
}

.cpd-code-btn::after {
  border: none;
}

.cpd-code-btn[disabled] {
  opacity: 0.5;
}

.cpd-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}

.cpd-cancel-btn {
  flex: 1;
  height: 44px;
  line-height: 44px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #374151;
  font-size: 15px;
  border: none;
}

.cpd-cancel-btn::after {
  border: none;
}

.cpd-submit-btn {
  flex: 1;
  height: 44px;
  line-height: 44px;
  border-radius: 10px;
  background: #111827;
  color: #fff;
  font-size: 15px;
  border: none;
}

.cpd-submit-btn::after {
  border: none;
}

.cpd-submit-btn[disabled] {
  opacity: 0.5;
}
</style>
