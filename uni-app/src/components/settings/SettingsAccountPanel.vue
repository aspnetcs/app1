<template>
  <view class="settings-account-panel">
    <view class="settings-panel-title">
      <text>账户设置</text>
    </view>

    <view class="account-section">
      <view class="account-info-card">
        <view class="account-avatar">
          <text class="account-avatar-text">{{ avatarText }}</text>
        </view>
        <view class="account-info">
          <text class="account-name">{{ displayName }}</text>
          <text class="account-sub">{{ displaySub }}</text>
        </view>
      </view>
    </view>

    <view class="account-section">
      <view class="account-row" @click="handleGoChangePasswordActivate" @tap="handleGoChangePasswordActivate">
        <text class="account-row-label">修改密码</text>
        <!-- #ifdef MP-WEIXIN -->
        <MpShapeIcon name="chevron-right" :size="16" color="currentColor" :stroke-width="2" />
        <!-- #endif -->
        <!-- #ifndef MP-WEIXIN -->
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="9 18 15 12 9 6"/>
        </svg>
        <!-- #endif -->
      </view>
    </view>

    <view class="account-section account-section-bottom">
      <button class="account-logout-btn" @click="handleLogoutActivate" @tap="handleLogoutActivate">
        退出登录
      </button>
    </view>

    <!-- logout confirm dialog -->
    <view v-if="logoutConfirm" class="logout-backdrop" @click="logoutConfirm = false" @tap="logoutConfirm = false">
      <view class="logout-dialog" @click.stop @tap.stop>
        <text class="logout-dialog-title">退出登录</text>
        <text class="logout-dialog-message">确定要退出登录吗？</text>
        <view class="logout-dialog-actions">
          <button class="logout-dialog-btn cancel" @click="logoutConfirm = false" @tap="logoutConfirm = false">取消</button>
          <button class="logout-dialog-btn confirm" :disabled="logoutBusy" @click="doLogout" @tap="doLogout">
            {{ logoutBusy ? '退出中...' : '确认退出' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'change-password'): void
}>()

const authStore = useAuthStore()
const runCompatAction = createCompatActionRunner()
const logoutConfirm = ref(false)
const logoutBusy = ref(false)

onMounted(() => {
  void authStore.fetchUserInfo()
})

const displayName = computed(() => {
  const u = authStore.userInfo
  return u?.phone || u?.email || '未登录用户'
})

const displaySub = computed(() => {
  const u = authStore.userInfo
  if (u?.email && u?.phone) return u.email
  if (u?.userId) return 'UID: ' + u.userId.slice(0, 8)
  return ''
})

const avatarText = computed(() => {
  const name = displayName.value
  return name ? name.charAt(0).toUpperCase() : '?'
})

function goChangePassword() {
  emit('change-password')
}

function handleGoChangePasswordActivate(event?: CompatEventLike) {
  runCompatAction('settings-account-change-password', event, () => {
    goChangePassword()
  })
}

function handleLogout() {
  logoutConfirm.value = true
}

async function doLogout() {
  logoutBusy.value = true
  try {
    await authStore.doLogout()
    emit('close')
    uni.reLaunch({ url: '/pages/index/index' })
  } finally {
    logoutBusy.value = false
    logoutConfirm.value = false
  }
}

function handleLogoutActivate(event?: CompatEventLike) {
  runCompatAction('settings-account-logout', event, () => {
    handleLogout()
  })
}
</script>

<style scoped>
.settings-account-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.settings-panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text-primary);
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-border-color-soft);
  margin-bottom: 24px;
}

.account-section {
  margin-bottom: 24px;
}

.account-section-bottom {
  margin-top: auto;
}

.account-info-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--app-surface-muted);
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
}

.account-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--app-neutral-strong);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.account-avatar-text {
  color: var(--app-neutral-strong-contrast);
  font-size: 18px;
  font-weight: 600;
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.account-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-sub {
  font-size: 13px;
  color: var(--app-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 120ms ease;
}

.account-row:hover {
  background: var(--app-fill-hover);
}

.account-row-label {
  font-size: 14px;
  color: var(--app-text-primary);
}

.account-logout-btn {
  width: 100%;
  padding: 12px 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--app-danger);
  background: var(--app-danger-soft);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: background 120ms ease;
}

.account-logout-btn::after {
  border: none;
}

.account-logout-btn:hover {
  background: var(--app-danger-soft-hover);
}

.logout-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--app-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
}

.logout-dialog {
  background: var(--app-surface-raised);
  border-radius: 16px;
  padding: 24px;
  width: 320px;
  max-width: 90vw;
  box-shadow: var(--app-shadow-elevated);
}

.logout-dialog-title {
  display: block;
  font-size: 17px;
  font-weight: 600;
  color: var(--app-text-primary);
  margin-bottom: 12px;
}

.logout-dialog-message {
  display: block;
  font-size: 14px;
  color: var(--app-text-secondary);
  line-height: 1.5;
  margin-bottom: 20px;
}

.logout-dialog-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.logout-dialog-btn {
  height: 36px;
  line-height: 36px;
  padding: 0 18px;
  border-radius: 8px;
  border: none;
  font-size: 14px;
}

.logout-dialog-btn::after {
  border: none;
}

.logout-dialog-btn.cancel {
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

.logout-dialog-btn.confirm {
  background: var(--app-danger);
  color: var(--app-neutral-strong-contrast);
}
</style>
