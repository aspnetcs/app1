<template>
  <view class="settings-context-panel">
    <view class="settings-panel-title">
      <text>记忆</text>
    </view>

    <view class="context-card">
      <text class="context-card-title">记忆状态</text>
      <text class="context-card-desc">{{ description }}</text>
    </view>

    <view v-if="contextStore.memoryRuntimeError" class="context-status context-status-error">
      <text>{{ contextStore.memoryRuntimeError }}</text>
    </view>

    <view v-if="serviceUnavailable" class="context-empty">
      <text>{{ unavailableDescription }}</text>
    </view>

    <view v-else class="memory-section">
      <view class="memory-toggle-row">
        <view class="memory-toggle-copy">
          <text class="memory-toggle-title">启用记忆</text>
          <text class="memory-toggle-desc">用于记住长期偏好、常用表达和持续性的上下文信息。</text>
        </view>
        <button
          class="memory-toggle-btn"
          :class="{ 'memory-toggle-btn-active': Boolean(contextStore.memoryRuntime?.enabled) }"
          :disabled="contextStore.memoryLoading"
          @click="handleToggle"
          @tap="handleToggle"
        >
          {{ contextStore.memoryLoading ? '处理中...' : Boolean(contextStore.memoryRuntime?.enabled) ? '已开启' : '已关闭' }}
        </button>
      </view>
    </view>

    <view v-if="contextStore.memoryEntriesError" class="context-status context-status-error">
      <text>{{ contextStore.memoryEntriesError }}</text>
    </view>

    <view v-if="!serviceUnavailable" class="memory-section">
      <view class="memory-section-header">
        <text class="memory-toggle-title">最近记忆</text>
        <button
          class="memory-refresh-btn"
          :disabled="contextStore.memoryEntriesLoading"
          @click="reloadEntries"
          @tap="reloadEntries"
        >
          刷新
        </button>
      </view>

      <view v-if="contextStore.memoryEntriesLoading" class="context-empty">
        <text>正在加载记忆条目...</text>
      </view>
      <view v-else-if="contextStore.memoryEntries.length === 0" class="context-empty">
        <text>还没有可展示的记忆条目。对话过程中形成的新记忆会出现在这里。</text>
      </view>
      <view v-else class="memory-list">
        <view v-for="entry in contextStore.memoryEntries" :key="entry.id" class="memory-entry-card">
          <view class="memory-entry-copy">
            <text class="memory-entry-summary">{{ entry.summary || entry.content || '未命名记忆' }}</text>
            <text class="memory-entry-meta">
              {{ formatSourceType(entry.sourceType) }} | {{ formatDateTime(entry.updatedAt || entry.createdAt) }}
            </text>
          </view>
          <button
            class="memory-delete-btn"
            @click="confirmDelete(entry.id)"
            @tap="confirmDelete(entry.id)"
          >
            删除
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useAiContextStore } from '@/stores/aiContext'
import { config } from '@/config'

const contextStore = useAiContextStore()

const serviceUnavailable = computed(() =>
  !config.features.memory
  || contextStore.memoryRuntime?.summary === '记忆设置不可用。',
)

const description = computed(() => {
  if (!config.features.memory) return '当前版本未开启记忆能力。'
  if (contextStore.memoryLoading) return '正在加载记忆设置...'
  if (contextStore.memoryRuntime?.summary) return contextStore.memoryRuntime.summary
  if (contextStore.memoryRuntime?.consentRequired && !contextStore.memoryRuntime?.consentGranted) {
    return '使用记忆功能前需要你先授权同意。'
  }
  return contextStore.memoryRuntime?.enabled ? '记忆功能已启用。' : '记忆功能已关闭。'
})

const unavailableDescription = computed(() => {
  if (!config.features.memory) {
    return '当前版本暂未开放记忆功能。'
  }
  return '记忆服务暂时不可用，请稍后再试。'
})

onMounted(() => {
  if (!config.features.memory) return
  void contextStore.loadMemoryRuntime()
  void contextStore.loadMemoryEntries(6)
})

function handleToggle() {
  void contextStore.updateMemoryRuntimeConsent(!Boolean(contextStore.memoryRuntime?.enabled))
}

function reloadEntries() {
  void contextStore.loadMemoryEntries(6)
}

function confirmDelete(entryId: string) {
  uni.showModal({
    title: '删除记忆',
    content: '删除后，该条记忆不会继续在后续会话中被优先引用。',
    success: (result) => {
      if (!result.confirm) return
      void contextStore.deleteMemoryEntryById(entryId)
    },
  })
}

function formatSourceType(sourceType?: string) {
  if (!sourceType) return '手动整理'
  if (sourceType === 'chat_summary') return '对话摘要'
  if (sourceType === 'manual') return '手动整理'
  return sourceType
}

function formatDateTime(value?: string | null) {
  if (!value) return '刚刚更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}
</script>

<style scoped>
.settings-context-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.settings-panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text-primary);
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.context-card,
.memory-section {
  padding: 16px;
  border-radius: 12px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
}

.context-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.context-card-title,
.memory-toggle-title,
.memory-entry-summary {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.context-card-desc,
.memory-toggle-desc,
.context-status,
.context-empty,
.memory-entry-meta {
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.context-status-error {
  color: var(--app-danger);
}

.memory-toggle-row,
.memory-section-header,
.memory-entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.memory-toggle-copy,
.memory-entry-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.memory-toggle-btn,
.memory-refresh-btn,
.memory-delete-btn {
  margin: 0;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.memory-toggle-btn::after,
.memory-refresh-btn::after,
.memory-delete-btn::after {
  border: none;
}

.memory-toggle-btn {
  min-width: 96px;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

.memory-toggle-btn-active {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.memory-refresh-btn {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.memory-delete-btn {
  background: var(--app-danger-soft);
  color: var(--app-danger-contrast);
}

.context-empty {
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface);
}

.memory-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.memory-entry-card {
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--app-surface-raised);
  border: 1px solid var(--app-border-color);
}

@media (max-width: 720px) {
  .memory-toggle-row,
  .memory-entry-card {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
