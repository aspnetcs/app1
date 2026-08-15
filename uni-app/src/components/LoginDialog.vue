<template>
  <view v-if="visible" class="login-backdrop" @click="handleCloseActivate" @tap="handleCloseActivate"></view>
  <view v-if="visible" class="login-dialog-wrap">
    <view class="login-dialog" @click.stop @tap.stop>
      <view class="login-close-btn" @click="handleCloseActivate" @tap="handleCloseActivate">
        <!-- #ifdef MP-WEIXIN -->
        <MpShapeIcon name="close" :size="18" color="currentColor" :stroke-width="2.5" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M18 6L6 18" />
          <path d="M6 6l12 12" />
        </svg>
        <!-- #endif -->
      </view>

      <view class="login-header">
        <text class="login-title">{{ titleText }}</text>
        <text class="login-subtitle">{{ subtitleText }}</text>
      </view>

      <!-- #ifdef H5 -->
      <!-- OAuth providers (H5) -->
      <view v-if="oauthProviders.length > 0" class="login-oauth-section">
        <view
          v-for="provider in oauthProviders"
          :key="provider.provider"
          class="login-oauth-btn"
          :class="{ 'login-oauth-btn-loading': oauthLoading === provider.provider }"
          @click="handleOAuthActivate(provider.provider, $event)"
          @tap="handleOAuthActivate(provider.provider, $event)"
        >
          <text class="login-oauth-text">{{ getOauthButtonText(provider.displayName) }}</text>
        </view>
      </view>

      <view v-if="oauthProviders.length > 0" class="login-divider">
        <view class="login-divider-line"></view>
        <text class="login-divider-text">{{ orText }}</text>
        <view class="login-divider-line"></view>
      </view>
      <!-- #endif -->

      <!-- identifier input -->
      <view class="login-form-section">
        <input
          v-model="identifier"
          class="login-input"
          :placeholder="identifierPlaceholder"
          type="text"
          :disabled="identifierLocked"
          @confirm="handleContinue"
          @blur="onIdentifierBlur"
        />

        <!-- code mode -->
        <view v-if="loginMode === 'code'" class="login-code-wrap">
          <input
            v-model="code"
            class="login-code-field"
            :placeholder="codePlaceholder"
            type="number"
            :maxlength="6"
            @confirm="handleContinue"
          />
          <text
            class="login-get-code-text"
            :class="{ 'login-get-code-disabled': !canSendCode || sendingCode }"
            @click="handleGetCodeActivate"
            @tap="handleGetCodeActivate"
          >{{ codeButtonText }}</text>
        </view>

        <!-- password mode -->
        <view v-if="loginMode === 'password'" class="login-pwd-row">
          <input
            v-model="password"
            class="login-input"
            :placeholder="pwdPlaceholder"
            :password="!showPassword"
            @confirm="handleContinue"
          />
        </view>

        <!-- mode toggle -->
        <view class="login-mode-toggle-btn" @click.stop="handleToggleLoginModeActivate" @tap.stop="handleToggleLoginModeActivate">
          <text class="login-mode-toggle-text">{{ modeToggleText }}</text>
        </view>

        <!-- continue button -->
        <button
          class="login-continue-btn"
          :disabled="!canContinue || loading"
          :loading="loading"
          @click="handleContinueActivate"
          @tap="handleContinueActivate"
        >
          {{ continueText }}
        </button>
      </view>

      <view class="login-terms">
        <navigator url="/pages/agreement/agreement" class="login-terms-link">{{ termsText }}</navigator>
        <text class="login-terms-dot">|</text>
        <navigator url="/pages/privacy/privacy" class="login-terms-link">{{ privacyText }}</navigator>
      </view>

      <!-- #ifdef H5 -->
      <view v-if="oauthHint" class="login-hint">
        <text>{{ oauthHint }}</text>
      </view>
      <!-- #endif -->
    </view>
  </view>

  <CaptchaModal ref="captchaRef" @success="onCaptchaSuccess" />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { APP_CONFIG, config } from '@/config'
import * as authApi from '@/api/auth'
import type { LoginResponse, OAuthProviderItem, PasswordLoginRequest } from '@/api/types'
import { extractErrorMessage } from '@/utils/errorMessage'
import { isValidEmail, isValidPhone } from '@/utils/format'
import { logger } from '@/utils/logger'
import CaptchaModal from '@/components/captcha/CaptchaModal.vue'
import {
  buildPasswordLoginRequest,
  inferLoginIdentifierType,
  isCodeLoginIdentifierValid,
  isPasswordLoginIdentifierValid,
  type LoginIdentifierType,
} from '@/components/loginDialogRules'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import { useAuthStore } from '@/stores/auth'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'login-success', data: { needSetPassword: boolean }): void
}>()

// --- state ---
const identifier = ref('')
const identifierLocked = ref(false)
const identifierType = ref<LoginIdentifierType>('phone')
const identifierChecked = ref(false)
const hasPassword = ref<boolean | null>(null)
const loginMode = ref<'code' | 'password'>('code')
const code = ref('')
const password = ref('')
const showPassword = ref(false)
const loading = ref(false)
const sendingCode = ref(false)
const countdown = ref(0)
const oauthProviders = ref<OAuthProviderItem[]>([])
const oauthLoading = ref('')
const oauthConsuming = ref(false)
const oauthHint = ref('')
const captchaRef = ref<InstanceType<typeof CaptchaModal>>()
const pendingCaptchaPurpose = ref<'sendCode' | 'passwordLogin'>('sendCode')
const runCompatAction = createCompatActionRunner()
let countdownTimer: ReturnType<typeof setInterval> | null = null

// --- computed text ---
const titleText = computed(() => '\u767b\u5f55\u6216\u6ce8\u518c')
const subtitleText = computed(() => '\u4f60\u5c06\u83b7\u5f97\u66f4\u52a0\u667a\u80fd\u7684\u56de\u590d\u548c\u66f4\u591a\u989d\u5ea6')
const orText = computed(() => '\u6216\u8005')
const identifierPlaceholder = computed(() => '\u624b\u673a\u53f7/\u7535\u5b50\u90ae\u4ef6\u5730\u5740')
const codePlaceholder = computed(() => '\u8bf7\u8f93\u5165\u9a8c\u8bc1\u7801')
const pwdPlaceholder = computed(() => '\u8bf7\u8f93\u5165\u5bc6\u7801')
const continueText = computed(() => '\u7ee7\u7eed')
const termsText = computed(() => '\u4f7f\u7528\u6761\u6b3e')
const privacyText = computed(() => '\u9690\u79c1\u534f\u8bae')
const invalidIdentifierText = computed(() => '\u8bf7\u8f93\u5165\u6b63\u786e\u7684\u624b\u673a\u53f7\u6216\u90ae\u7bb1')
const retryText = computed(() => '\u8bf7\u7a0d\u540e\u91cd\u8bd5')
const loginSuccessText = computed(() => '\u767b\u5f55\u6210\u529f')
const oauthLoadingText = computed(() => '\u6388\u6743\u767b\u5f55\u4e2d...')
const oauthRedirectingText = computed(() => '\u8df3\u8f6c\u6388\u6743\u4e2d...')
const oauthFailedText = computed(() => '\u7b2c\u4e09\u65b9\u767b\u5f55\u5931\u8d25')
const oauthUnavailableText = computed(() => '\u7b2c\u4e09\u65b9\u767b\u5f55\u6682\u4e0d\u53ef\u7528')

const getCodeText = computed(() => '\u83b7\u53d6\u9a8c\u8bc1\u7801')
const codeButtonText = computed(() => {
  if (countdown.value > 0) return `${countdown.value}s`
  return getCodeText.value
})

const modeToggleText = computed(() => {
  if (loginMode.value === 'code') {
    return '\u4f7f\u7528\u5bc6\u7801\u767b\u5f55'
  }
  return '\u4f7f\u7528\u9a8c\u8bc1\u7801\u767b\u5f55'
})

const oauthBusy = computed(() => !!oauthLoading.value || oauthConsuming.value)

const isValidIdentifier = computed(() => {
  const v = identifier.value.trim()
  if (loginMode.value === 'password') {
    return isPasswordLoginIdentifierValid(v)
  }
  return isCodeLoginIdentifierValid(v)
})

const canSendCode = computed(() => {
  return isCodeLoginIdentifierValid(identifier.value) && countdown.value <= 0
})

const canContinue = computed(() => {
  if (!isValidIdentifier.value) return false
  if (loginMode.value === 'code') {
    return code.value.length >= 4
  }
  return password.value.length > 0
})

// --- lifecycle ---
watch(() => props.visible, (visible) => {
  if (!visible) return
  resetState()
  // #ifdef H5
  void loadOAuthProviders()
  // #endif
})

onMounted(() => {
  // #ifdef H5
  if (config.features.oauth) {
    void loadOAuthProviders()
    void consumeOAuthCallbackTicket()
  }
  // #endif
})

onUnmounted(() => {
  clearCountdown()
})

// --- methods ---
function resetState() {
  identifier.value = ''
  identifierLocked.value = false
  identifierChecked.value = false
  hasPassword.value = null
  loginMode.value = 'code'
  code.value = ''
  password.value = ''
  showPassword.value = false
  loading.value = false
  sendingCode.value = false
  countdown.value = 0
  oauthHint.value = ''
  clearCountdown()
}

function handleClose() {
  emit('close')
}

function handleCloseActivate(event?: CompatEventLike) {
  runCompatAction('login-dialog-close', event, () => {
    handleClose()
  })
}

function clearCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
}

function startCountdown() {
  countdown.value = 60
  clearCountdown()
  countdownTimer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearCountdown()
    }
  }, 1000)
}

async function onIdentifierBlur() {
  const value = identifier.value.trim()
  if (!value) {
    identifierChecked.value = false
    hasPassword.value = null
    return
  }
  if (!isCodeLoginIdentifierValid(value)) {
    identifierType.value = 'identifier'
    identifierChecked.value = false
    hasPassword.value = null
    return
  }
  if (identifierChecked.value && identifierLocked.value) return
  try {
    const res = await authApi.checkIdentifier({ identifier: value })
    identifierType.value =
      res.data.type === 'email'
        ? 'email'
        : res.data.type === 'phone'
          ? 'phone'
          : 'identifier'
    hasPassword.value = !!res.data.hasPassword
    identifierChecked.value = true
  } catch {
    identifierChecked.value = false
    hasPassword.value = null
  }
}

function toggleLoginMode() {
  code.value = ''
  password.value = ''
  loginMode.value = loginMode.value === 'code' ? 'password' : 'code'
}

function handleToggleLoginModeActivate(event?: CompatEventLike) {
  runCompatAction('login-dialog-toggle-mode', event, () => {
    toggleLoginMode()
  })
}

function handleGetCode() {
  if (!canSendCode.value || sendingCode.value) return
  const value = identifier.value.trim()
  if (!isValidPhone(value) && !isValidEmail(value)) {
    uni.showToast({ title: invalidIdentifierText.value, icon: 'none' })
    return
  }
  pendingCaptchaPurpose.value = 'sendCode'
  captchaRef.value?.open()
}

function handleGetCodeActivate(event?: CompatEventLike) {
  runCompatAction('login-dialog-get-code', event, () => {
    handleGetCode()
  })
}

function handleContinue() {
  if (!canContinue.value || loading.value) return
  const value = identifier.value.trim()
  if (loginMode.value === 'code' && !isCodeLoginIdentifierValid(value)) {
    uni.showToast({ title: invalidIdentifierText.value, icon: 'none' })
    return
  }
  if (loginMode.value === 'password' && !isPasswordLoginIdentifierValid(value)) {
    uni.showToast({ title: '请输入账号', icon: 'none' })
    return
  }
  // Auto-detect identifier type if not checked via blur
  if (!identifierChecked.value) {
    identifierType.value = inferLoginIdentifierType(value)
  }

  if (loginMode.value === 'password') {
    pendingCaptchaPurpose.value = 'passwordLogin'
    captchaRef.value?.open()
  } else {
    doCodeLogin()
  }
}

function handleContinueActivate(event?: CompatEventLike) {
  runCompatAction('login-dialog-continue', event, () => {
    handleContinue()
  })
}

async function onCaptchaSuccess(token: string) {
  if (pendingCaptchaPurpose.value === 'sendCode') {
    await doSendCode(token)
  } else {
    await doPasswordLogin(token)
  }
}

async function doSendCode(challengeToken: string) {
  sendingCode.value = true
  try {
    const value = identifier.value.trim()
    const type = isValidEmail(value) ? 'email' : 'phone'
    identifierType.value = type
    if (type === 'phone') {
      await authApi.sendSmsCode({ phone: value, purpose: 'login', challengeToken })
    } else {
      await authApi.sendEmailCode({ email: value, purpose: 'login', challengeToken })
    }
    uni.showToast({
      title: '\u9a8c\u8bc1\u7801\u5df2\u53d1\u9001',
      icon: 'success',
    })
    identifierLocked.value = true
    startCountdown()
  } catch (error: unknown) {
    logger.warn('[LoginDialog] send code failed:', error)
    uni.showToast({
      title: extractErrorMessage(error, '\u53d1\u9001\u9a8c\u8bc1\u7801\u5931\u8d25'),
      icon: 'none',
    })
  } finally {
    sendingCode.value = false
  }
}

async function doCodeLogin() {
  loading.value = true
  try {
    const value = identifier.value.trim()
    const type = identifierType.value
    let res
    if (type === 'phone') {
      res = await authApi.smsLogin({ phone: value, code: code.value, purpose: 'login' })
    } else {
      res = await authApi.emailLogin({ email: value, code: code.value, purpose: 'login' })
    }
    finalizeLogin(res.data)
  } catch (error: unknown) {
    logger.warn('[LoginDialog] code login failed:', error)
    uni.showToast({
      title: extractErrorMessage(error, '\u9a8c\u8bc1\u5931\u8d25'),
      icon: 'none',
    })
  } finally {
    loading.value = false
  }
}

async function doPasswordLogin(challengeToken: string) {
  loading.value = true
  try {
    const value = identifier.value.trim()
    const req: PasswordLoginRequest = buildPasswordLoginRequest(value, password.value, challengeToken)
    const res = await authApi.passwordLogin(req)
    finalizeLogin(res.data)
  } catch (error: unknown) {
    logger.warn('[LoginDialog] password login failed:', error)
    const msg = extractErrorMessage(error, '')
    const isNoPassword = /\u5BC6\u7801|password|\u672A\u8BBE\u7F6E/i.test(msg)
    if (isNoPassword) {
      uni.showToast({
        title: '\u8BE5\u8D26\u6237\u672A\u8BBE\u7F6E\u5BC6\u7801\uFF0C\u8BF7\u4F7F\u7528\u9A8C\u8BC1\u7801\u767B\u5F55',
        icon: 'none',
      })
      loginMode.value = 'code'
      password.value = ''
    } else {
      uni.showToast({
        title: msg || '\u767b\u5f55\u5931\u8d25',
        icon: 'none',
      })
    }
  } finally {
    loading.value = false
  }
}

function finalizeLogin(data: LoginResponse) {
  const authStore = useAuthStore()
  authStore.handleLoginSuccess(data)
  const needSetPassword = data.isNewUser || hasPassword.value === false
  emit('close')
  uni.showToast({ title: loginSuccessText.value, icon: 'success' })
  setTimeout(() => {
    uni.reLaunch({ url: APP_CONFIG.loginSuccessPage })
    emit('login-success', { needSetPassword })
  }, 800)
}

// --- OAuth ---
// #ifdef H5
function getOauthButtonText(displayName: string) {
  return `\u7ee7\u7eed\u4f7f\u7528 ${displayName} \u767b\u5f55`
}

async function loadOAuthProviders() {
  if (!config.features.oauth) return
  try {
    const res = await authApi.getOAuthProviders('user')
    oauthProviders.value = Array.isArray(res.data?.providers) ? res.data.providers : []
  } catch {
    oauthProviders.value = []
  }
}

function buildOAuthRedirectUri() {
  const { origin, pathname } = window.location
  return `${origin}${pathname}#/pages/index/index`
}

function getHashQueryParams() {
  const hash = window.location.hash || ''
  const index = hash.indexOf('?')
  const query = index >= 0 ? hash.slice(index + 1) : ''
  return new URLSearchParams(query)
}

function clearHashOAuthParams() {
  const route = (window.location.hash || '#/pages/index/index').split('?')[0] || '#/pages/index/index'
  window.history.replaceState(null, document.title, `${window.location.origin}${window.location.pathname}${route}`)
}

async function consumeOAuthCallbackTicket() {
  const params = getHashQueryParams()
  const errorMessage = params.get('oauthMessage') || params.get('oauthError')
  if (errorMessage) {
    oauthHint.value = errorMessage
    clearHashOAuthParams()
    return
  }
  const ticket = params.get('oauthTicket')
  if (!ticket) return
  oauthConsuming.value = true
  uni.showLoading({ title: oauthLoadingText.value, mask: true })
  try {
    const res = await authApi.consumeOAuthTicket({ ticket, scene: 'user' })
    clearHashOAuthParams()
    finalizeLogin(res.data)
  } catch (error: unknown) {
    oauthHint.value = extractErrorMessage(error, oauthFailedText.value)
    clearHashOAuthParams()
  } finally {
    oauthConsuming.value = false
    uni.hideLoading()
  }
}

async function startOAuth(provider: string) {
  if (oauthBusy.value) return
  oauthLoading.value = provider
  oauthHint.value = ''
  uni.showLoading({ title: oauthRedirectingText.value, mask: true })
  try {
    const system = uni.getSystemInfoSync()
    const res = await authApi.startOAuthLogin(provider, {
      scene: 'user',
      redirectUri: buildOAuthRedirectUri(),
      device: {
        deviceType: 'h5',
        deviceName: system.model || system.browserName || 'h5',
        brand: config.brandId,
      },
    })
    window.location.assign(res.data.authorizeUrl)
  } catch (error: unknown) {
    oauthHint.value = extractErrorMessage(error, oauthUnavailableText.value)
    uni.showToast({ title: oauthHint.value, icon: 'none' })
  } finally {
    oauthLoading.value = ''
    uni.hideLoading()
  }
}

function handleOAuthActivate(provider: string, event?: CompatEventLike) {
  runCompatAction(`login-dialog-oauth:${provider}`, event, () => {
    void startOAuth(provider)
  })
}
// #endif
</script>

<style scoped>
.login-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: var(--z-modal-backdrop);
  animation: loginFadeIn 150ms ease;
}

.login-dialog-wrap {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.login-dialog {
  pointer-events: auto;
  position: relative;
  width: 400px;
  max-width: 90vw;
  max-height: 90vh;
  overflow-y: auto;
  background: #ffffff;
  border-radius: 16px;
  padding: 48px 40px 32px;
  box-shadow: 0 24px 48px -12px rgba(0, 0, 0, 0.18);
  animation: loginSlideUp 200ms cubic-bezier(0.16, 1, 0.3, 1);
  font-family: -apple-system, "Segoe UI", "Helvetica Neue", sans-serif;
}

.login-close-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  cursor: pointer;
  transition: background 120ms ease, color 120ms ease;
}

.login-close-btn:hover {
  background: #f3f4f6;
  color: #374151;
}

/* --- header --- */
.login-header {
  text-align: center;
  margin-bottom: 24px;
}

.login-title {
  display: block;
  font-size: 28px;
  font-weight: 700;
  color: #0d0d0d;
  margin-bottom: 8px;
  line-height: 1.3;
}

.login-subtitle {
  display: block;
  font-size: 15px;
  color: #6e6e80;
  line-height: 1.5;
}

/* --- oauth --- */
.login-oauth-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.login-oauth-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52px;
  border: 1px solid #c2c8d0;
  border-radius: 26px;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
}

.login-oauth-btn:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.login-oauth-btn-loading {
  opacity: 0.6;
  pointer-events: none;
}

.login-oauth-text {
  font-size: 15px;
  color: #0d0d0d;
  font-weight: 500;
}

/* --- divider --- */
.login-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.login-divider-line {
  flex: 1;
  height: 1px;
  background: #e5e7eb;
}

.login-divider-text {
  font-size: 13px;
  color: #9ca3af;
}

/* --- form --- */
.login-form-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.login-input {
  height: 52px;
  padding: 0 16px;
  border: 1px solid #c2c8d0;
  border-radius: 26px;
  font-size: 15px;
  color: #0d0d0d;
  outline: none;
  transition: border-color 150ms ease;
  background: #ffffff;
}

.login-input:focus {
  border-color: #0d0d0d;
}

.login-input[disabled] {
  background: #f9fafb;
  color: #6b7280;
}

/* --- code input with inline get-code --- */
.login-code-wrap {
  position: relative;
  display: flex;
  align-items: center;
  height: 52px;
  border: 1px solid #c2c8d0;
  border-radius: 26px;
  background: #ffffff;
  transition: border-color 150ms ease;
}

.login-code-wrap:focus-within {
  border-color: #0d0d0d;
}

.login-code-field {
  flex: 1;
  height: 100%;
  padding: 0 16px;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: #0d0d0d;
  letter-spacing: 2px;
}

.login-get-code-text {
  flex-shrink: 0;
  padding: 0 18px;
  font-size: 14px;
  font-weight: 500;
  color: #0d0d0d;
  cursor: pointer;
  white-space: nowrap;
  border-left: 1px solid #e5e7eb;
  height: 28px;
  line-height: 28px;
}

.login-get-code-text:active {
  opacity: 0.6;
}

.login-get-code-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* --- password row --- */
.login-pwd-row {
  position: relative;
}

/* --- mode toggle (input-style view) --- */
.login-mode-toggle-btn {
  height: 52px;
  border-radius: 26px;
  background: #ffffff;
  border: 1px solid #c2c8d0;
  cursor: pointer;
  transition: background 120ms ease, border-color 120ms ease;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.login-mode-toggle-btn:hover {
  background: #f9fafb;
  border-color: #9ca3af;
}

.login-mode-toggle-btn:active {
  background: #f3f4f6;
}

.login-mode-toggle-text {
  font-size: 15px;
  font-weight: 500;
  color: #0d0d0d;
}

/* --- continue button --- */
.login-continue-btn {
  height: 52px;
  line-height: 52px;
  border-radius: 26px;
  background: #0d0d0d;
  color: #ffffff;
  font-size: 16px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  transition: background 150ms ease, opacity 150ms ease;
}

.login-continue-btn::after {
  border: none;
}

.login-continue-btn:active:not([disabled]) {
  background: #333333;
}

.login-continue-btn[disabled] {
  opacity: 0.4;
  cursor: not-allowed;
}

/* --- terms --- */
.login-terms {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
}

.login-terms-link {
  font-size: 12px;
  color: #6e6e80;
  text-decoration: none;
}

.login-terms-link:active {
  text-decoration: underline;
}

.login-terms-dot {
  font-size: 12px;
  color: #c2c8d0;
}

/* --- hint --- */
.login-hint {
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
  color: #d97706;
}

/* #ifdef MP-WEIXIN */
.login-dialog-wrap {
  align-items: flex-end;
  padding: 12px 12px calc(12px + var(--app-safe-bottom, 0px));
  box-sizing: border-box;
}

.login-dialog {
  width: 100%;
  max-width: none;
  max-height: calc(100vh - var(--app-safe-top, 0px) - var(--app-safe-bottom, 0px) - 12px);
  border-radius: 20px 20px 0 0;
  padding: 44px 20px 24px;
}

.login-header {
  text-align: left;
  padding-right: 36px;
}

.login-title {
  font-size: 24px;
}

.login-close-btn {
  top: 12px;
  right: 12px;
}
/* #endif */

@keyframes loginFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes loginSlideUp {
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
