<template>
  <view class="settings-context-panel">
    <view class="settings-panel-title">
      <text>知识库</text>
    </view>

    <view class="context-card">
      <text class="context-card-title">AI 使用方式</text>
      <text class="context-card-desc">
        命中知识库后，AI 只会检索相关片段作为上下文，不会把知识库内容当成必须逐条遵循的执行指令。
      </text>
      <text class="context-card-badge">retrieval</text>
    </view>

    <view class="context-card context-card-muted">
      <text class="context-card-title">当前会话绑定状态</text>
      <text class="context-card-desc">{{ conversationHint }}</text>
    </view>

    <view v-if="serviceUnavailable" class="context-empty">
      <text>{{ unavailableDescription }}</text>
    </view>

    <view v-else-if="contextStore.knowledgeSyncError" class="context-status context-status-error">
      <text>{{ contextStore.knowledgeSyncError }}</text>
    </view>

    <view v-if="!serviceUnavailable" class="context-section">
      <view class="section-header">
        <text class="section-title">已保存知识库资产</text>
        <button class="section-link-btn" @click="openMarketPage" @tap="openMarketPage">前往市场</button>
      </view>

      <view v-if="userPreferencesStore.savedAssetsLoading.KNOWLEDGE" class="context-empty">
        <text>正在加载已保存知识库资产...</text>
      </view>
      <view v-else-if="userPreferencesStore.savedKnowledgeAssets.length === 0" class="context-empty">
        <text>还没有保存的知识库资产。可以先去市场保存知识库，再回到这里绑定到当前会话。</text>
      </view>
      <view v-else class="knowledge-list">
        <view
          v-for="asset in userPreferencesStore.savedKnowledgeAssets"
          :key="asset.assetType + ':' + asset.sourceId"
          class="knowledge-item"
          :class="{ 'knowledge-item-active': isAssetBound(asset.sourceId) }"
        >
          <view class="knowledge-item-main">
            <view class="knowledge-item-title-row">
              <text class="knowledge-item-name">{{ getMarketAssetTitle(asset) }}</text>
              <text class="knowledge-tag">{{ getUsageModeLabel(asset) }}</text>
              <text v-if="isAssetBound(asset.sourceId)" class="knowledge-tag knowledge-tag-active">已绑定</text>
            </view>
            <text class="knowledge-item-meta">
              {{ getMarketAssetSummary(asset) || '暂无简介' }}
            </text>
            <text class="knowledge-item-meta">
              文档数：{{ asset.source?.documentCount ?? 0 }}
            </text>
            <text class="knowledge-item-instruction">
              {{ getAssetInstruction(asset) }}
            </text>
          </view>
          <button
            class="knowledge-action-btn"
            :class="{ 'knowledge-action-btn-active': isAssetBound(asset.sourceId) }"
            :disabled="!hasPersistentConversation"
            @click="handleToggle(asset.sourceId)"
            @tap="handleToggle(asset.sourceId)"
          >
            {{ isAssetBound(asset.sourceId) ? '取消绑定' : '绑定会话' }}
          </button>
        </view>
      </view>
    </view>

    <view v-if="!hasPersistentConversation" class="context-empty">
      <text>请先打开一个普通会话，再在这里绑定知识库。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useAiContextStore } from '@/stores/aiContext'
import { useUserPreferencesStore } from '@/stores/userPreferences'
import { config } from '@/config'
import {
  getMarketAssetSummary,
  getMarketAssetTitle,
  getMarketAssetUsageInstruction,
  getMarketAssetUsageMode,
  type MarketAsset,
} from '@/api/market'

const chatStore = useChatStore()
const contextStore = useAiContextStore()
const userPreferencesStore = useUserPreferencesStore()

const hasPersistentConversation = computed(() =>
  Boolean(chatStore.currentConversationId) && chatStore.conversationScope === 'persistent',
)

const conversationHint = computed(() => {
  if (!chatStore.currentConversationId) return '当前没有打开中的会话。'
  if (chatStore.conversationScope !== 'persistent') return '临时会话不支持绑定知识库。'
  return `当前会话已绑定 ${contextStore.selectedKnowledgeBaseIds.length} 个知识库。`
})

const serviceUnavailable = computed(() =>
  !config.features.knowledgeBase || Boolean(userPreferencesStore.savedAssetErrors.KNOWLEDGE),
)

const unavailableDescription = computed(() => {
  if (!config.features.knowledgeBase) {
    return '当前品牌配置未启用知识库，这里先保留入口说明，不发起知识库请求。'
  }
  return '知识库服务暂时不可用，这里先保留入口说明。等后端接通后，再在这里绑定会话知识库。'
})

onMounted(() => {
  if (!config.features.knowledgeBase) return
  void userPreferencesStore.loadSavedAssets('KNOWLEDGE')
  void contextStore.loadKnowledgeOptions()
})

watch(
  () => chatStore.currentConversationId,
  (conversationId) => {
    if (!config.features.knowledgeBase) return
    void contextStore.loadConversationKnowledgeSelection(conversationId)
  },
  { immediate: true },
)

function openMarketPage() {
  uni.navigateTo({ url: '/market/pages/index/index' })
}

function isAssetBound(sourceId: string) {
  return contextStore.selectedKnowledgeBaseIds.includes(sourceId)
}

function handleToggle(sourceId: string) {
  if (!config.features.knowledgeBase) return
  if (!hasPersistentConversation.value) return
  void contextStore.toggleKnowledgeSelection(sourceId, chatStore.currentConversationId)
}

function getUsageModeLabel(asset: MarketAsset) {
  return getMarketAssetUsageMode(asset) === 'retrieval' ? '检索上下文' : '知识资产'
}

function getAssetInstruction(asset: MarketAsset) {
  return getMarketAssetUsageInstruction(asset) || '命中后仅检索相关片段作为上下文。'
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

.context-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.context-card {
  padding: 16px;
  border-radius: 12px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.context-card-muted {
  background: var(--app-surface-muted);
}

.context-card-title,
.section-title,
.knowledge-item-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.context-card-desc,
.context-status,
.context-empty,
.knowledge-item-meta,
.knowledge-item-instruction {
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.context-card-badge,
.knowledge-tag {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  font-weight: 600;
}

.knowledge-tag-active {
  background: var(--app-success-soft);
  color: var(--app-success-contrast);
}

.context-status-error {
  color: var(--app-danger);
}

.context-empty {
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-link-btn {
  margin: 0;
  height: 34px;
  line-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid var(--app-border-color);
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 12px;
  font-weight: 600;
}

.section-link-btn::after {
  border: none;
}

.knowledge-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.knowledge-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.knowledge-item-active {
  border-color: var(--app-accent-soft);
  background: var(--app-accent-soft);
}

.knowledge-item-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.knowledge-item-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.knowledge-action-btn {
  margin: 0;
  min-width: 96px;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.knowledge-action-btn::after {
  border: none;
}

.knowledge-action-btn-active {
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
}

@media (max-width: 720px) {
  .knowledge-item {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

