<template>
  <view class="settings-archived-panel">
    <view class="settings-panel-title">
      <text>已归档聊天</text>
    </view>
    <view v-if="archivedConversations.length === 0" class="settings-empty-state">
      <!-- #ifdef MP-WEIXIN -->
      <MpShapeIcon name="archive" :size="48" color="currentColor" :stroke-width="1.5" />
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <polyline points="21 8 21 21 3 21 3 8"/>
        <rect x="1" y="3" width="22" height="5"/>
        <line x1="10" y1="12" x2="14" y2="12"/>
      </svg>
      <!-- #endif -->
      <text class="settings-empty-text">暂无归档聊天</text>
    </view>
    <view v-else class="archived-list">
      <view v-for="conversation in archivedConversations" :key="conversation.id" class="archived-card">
        <view class="archived-card-main">
          <text class="archived-card-title">{{ conversation.title }}</text>
          <text class="archived-card-meta">{{ formatArchivedTime(conversation.deletedAt ?? conversation.updatedAt) }}</text>
        </view>
        <view class="archived-card-actions">
          <button
            type="button"
            class="archived-restore-btn"
            :disabled="restoringId === conversation.id"
            @click="handleRestore(conversation.id)"
            @tap="handleRestore(conversation.id)"
          >
            {{ restoringId === conversation.id ? '恢复中...' : '恢复' }}
          </button>
        </view>
      </view>
      <view class="archived-footer">
        <button type="button" class="archived-history-btn" @click="handleOpenHistory" @tap="handleOpenHistory">
          前往历史记录页管理归档对话
        </button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useChatStore } from '@/stores/chat'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const chatStore = useChatStore()
const archivedConversations = computed(() => chatStore.archivedHistory)
const restoringId = ref<string | null>(null)

onMounted(() => {
  void chatStore.fetchArchivedHistory()
})

async function handleRestore(conversationId: string) {
  if (restoringId.value) return
  restoringId.value = conversationId
  try {
    await chatStore.restoreConversation(conversationId)
    uni.showToast({ title: '已恢复', icon: 'none' })
  } catch (error) {
    uni.showToast({
      title: error instanceof Error ? error.message : '恢复失败',
      icon: 'none',
    })
  } finally {
    restoringId.value = null
  }
}

function handleOpenHistory() {
  uni.navigateTo({ url: '/chat/pages/history/history' })
}

function formatArchivedTime(value?: number | null) {
  if (!value) return ''
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return ''
  return d.toLocaleString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.settings-archived-panel {
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

.settings-empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--app-text-secondary);
}

.settings-empty-text {
  font-size: 14px;
  color: var(--app-text-secondary);
}

.archived-list {
  flex: 1;
  overflow-y: auto;
}

.archived-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 8px;
  background: var(--app-surface-muted);
  border-radius: 10px;
  border: 1px solid var(--app-border-color);
}

.archived-card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.archived-card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.archived-card-meta {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.archived-card-actions {
  flex-shrink: 0;
}

.archived-restore-btn {
  height: 30px;
  line-height: 30px;
  padding: 0 14px;
  border-radius: 6px;
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
  font-size: 12px;
  border: none;
}

.archived-restore-btn::after {
  border: none;
}

.archived-footer {
  margin-top: 16px;
  text-align: center;
}

.archived-history-btn {
  height: 36px;
  line-height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  font-size: 13px;
  border: none;
}

.archived-history-btn::after {
  border: none;
}
</style>
