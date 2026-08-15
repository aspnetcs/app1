<template>
  <block v-if="visible">
    <view class="acd-overlay" @click="emit('close')" @tap="emit('close')"></view>
    <view class="acd-shell">
      <view class="acd-dialog" @click.stop @tap.stop>
        <view class="acd-kicker">会话整理</view>
        <view class="acd-title">{{ title }}</view>

        <view class="acd-card">
          <view v-if="name" class="acd-name">{{ name }}</view>
          <view class="acd-message">{{ message }}</view>
        </view>

        <view v-if="tip" class="acd-tip">{{ tip }}</view>

        <view class="acd-actions">
          <button class="acd-btn acd-btn-cancel" :disabled="busy" @click="emit('close')" @tap="emit('close')">
            取消
          </button>
          <button class="acd-btn acd-btn-confirm" :disabled="busy" @click="emit('confirm')" @tap="emit('confirm')">
            {{ busy ? busyText : confirmText }}
          </button>
        </view>
      </view>
    </view>
  </block>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
  title?: string
  name?: string
  message?: string
  tip?: string
  confirmText?: string
  busy?: boolean
  busyText?: string
}>(), {
  title: '归档对话',
  name: '',
  message: '归档后可在设置页面中的“已归档”列表中恢复。',
  tip: '归档不会删除内容，只会从当前列表移动到“已归档”列表。',
  confirmText: '确认归档',
  busy: false,
  busyText: '归档中...',
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'confirm'): void
}>()
</script>

<style scoped>
.acd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  z-index: var(--z-modal-backdrop, 99998);
}

.acd-shell {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  box-sizing: border-box;
  z-index: var(--z-modal, 99999);
  pointer-events: none;
}

.acd-dialog {
  width: calc(100vw - 32px);
  max-width: 420px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 28px;
  padding: 28px;
  box-sizing: border-box;
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.18);
  pointer-events: auto;
}

.acd-kicker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 14px;
  border-radius: 999px;
  background: #fff1f2;
  color: #be123c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.acd-title {
  margin-top: 16px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 700;
  color: #0f172a;
}

.acd-card {
  margin-top: 18px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
}

.acd-name {
  font-size: 16px;
  line-height: 1.5;
  font-weight: 700;
  color: #0f172a;
  word-break: break-word;
}

.acd-message {
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: #475569;
}

.acd-tip {
  margin-top: 14px;
  font-size: 13px;
  line-height: 1.75;
  color: #94a3b8;
}

.acd-actions {
  display: flex;
  gap: 12px;
  margin-top: 22px;
}

.acd-btn {
  flex: 1;
  min-width: 0;
  height: 46px;
  line-height: 46px;
  border-radius: 14px;
  font-size: 15px;
  font-weight: 700;
  border: none;
  margin: 0;
}

.acd-btn::after {
  border: none;
}

.acd-btn-cancel {
  background: #e2e8f0;
  color: #334155;
}

.acd-btn-confirm {
  background: #ef4444;
  color: #ffffff;
  box-shadow: 0 12px 24px rgba(239, 68, 68, 0.18);
}

.acd-btn-cancel:disabled,
.acd-btn-confirm:disabled {
  opacity: 0.72;
}

@media screen and (max-width: 600px) {
  .acd-shell {
    align-items: flex-end;
    padding: 12px;
  }

  .acd-dialog {
    width: 100%;
    max-width: 100%;
    border-radius: 24px;
    padding: 24px 20px 20px;
  }

  .acd-title {
    font-size: 20px;
  }

  .acd-actions {
    gap: 10px;
  }

  .acd-btn {
    height: 44px;
    line-height: 44px;
    font-size: 14px;
  }
}
</style>
