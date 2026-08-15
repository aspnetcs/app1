<template>
  <view class="settings-agent-panel">
    <view class="settings-panel-title">
      <text>智能体</text>
    </view>

    <view class="agent-summary-card">
      <text class="agent-summary-title">默认智能体</text>
      <text class="agent-summary-desc">
        {{ defaultAgentSummary }}
      </text>
    </view>

    <view v-if="userPreferencesStore.error" class="settings-status settings-status-error">
      <text>{{ userPreferencesStore.error }}</text>
    </view>
    <view v-if="userPreferencesStore.savedAssetErrors.AGENT" class="settings-status settings-status-error">
      <text>{{ userPreferencesStore.savedAssetErrors.AGENT }}</text>
    </view>

    <view class="agent-section">
      <view class="section-header">
        <text class="section-title">已保存智能体</text>
        <button
          class="section-link-btn"
          :disabled="userPreferencesStore.savedAssetsLoading.AGENT"
          @click="openMarketPage"
          @tap="openMarketPage"
        >
          前往市场
        </button>
      </view>

      <view v-if="userPreferencesStore.savedAssetsLoading.AGENT" class="context-empty">
        <text>正在加载已保存智能体...</text>
      </view>
      <view v-else-if="userPreferencesStore.savedAgentAssets.length === 0" class="context-empty">
        <text>还没有保存任何智能体。先在市场中保存一个智能体，再回来设置默认项。</text>
      </view>
      <view v-else class="asset-list">
        <view
          v-for="asset in userPreferencesStore.savedAgentAssets"
          :key="asset.assetType + ':' + asset.sourceId"
          class="asset-card"
          :class="{ 'asset-card-default': isDefaultAgent(asset) }"
        >
          <view class="asset-copy">
            <view class="asset-title-row">
              <text class="asset-title">{{ getMarketAssetTitle(asset) }}</text>
              <text v-if="isDefaultAgent(asset)" class="asset-tag">默认</text>
            </view>
            <text class="asset-desc">{{ getMarketAssetSummary(asset) || '暂无简介' }}</text>
          </view>
          <view class="asset-actions">
            <button
              class="asset-action-btn"
              :class="{ 'asset-action-btn-active': isDefaultAgent(asset) }"
              :disabled="userPreferencesStore.saving && isDefaultAgent(asset)"
              @click="handleSetDefault(asset)"
              @tap="handleSetDefault(asset)"
            >
              {{ isDefaultAgent(asset) ? '当前默认' : '设为默认' }}
            </button>
            <button
              class="asset-action-btn asset-action-btn-ghost"
              @click="openAgentConversation(asset)"
              @tap="openAgentConversation(asset)"
            >
              进入对话
            </button>
          </view>
        </view>
      </view>
    </view>

    <view v-if="showMissingDefaultNotice" class="agent-note-card">
      <text class="agent-note-title">默认项提示</text>
      <text class="agent-note-desc">
        当前默认智能体仍有记录，但它已经不在已保存列表中。你可以重新保存，或从下方重新选择新的默认智能体。
      </text>
      <button class="section-link-btn section-link-btn-inline" @click="clearDefaultAgent" @tap="clearDefaultAgent">
        清除默认项
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import {
  agentAssetMatchesId,
  getMarketAssetSummary,
  getMarketAssetTitle,
  resolveAgentAssetRuntimeId,
  type MarketAsset,
} from '@/api/market'
import { useUserPreferencesStore } from '@/stores/userPreferences'

const userPreferencesStore = useUserPreferencesStore()

const showMissingDefaultNotice = computed(() =>
  Boolean(userPreferencesStore.preferences.defaultAgentId) && !userPreferencesStore.defaultAgentAsset,
)

const defaultAgentSummary = computed(() => {
  if (userPreferencesStore.loading) {
    return '正在加载偏好设置...'
  }
  if (userPreferencesStore.defaultAgentAsset) {
    return `当前默认智能体为“${getMarketAssetTitle(userPreferencesStore.defaultAgentAsset)}”。`
  }
  if (showMissingDefaultNotice.value) {
    return '当前默认智能体已经不在已保存列表中，请重新确认。'
  }
  return '尚未设置默认智能体。你可以在下方已保存列表中选择一个常用智能体。'
})

onMounted(() => {
  void userPreferencesStore.loadPreferences()
  void userPreferencesStore.loadSavedAssets('AGENT')
})

function isDefaultAgent(asset: MarketAsset) {
  return agentAssetMatchesId(asset, userPreferencesStore.preferences.defaultAgentId)
}

function openMarketPage() {
  uni.navigateTo({ url: '/market/pages/index/index' })
}

function openAgentConversation(asset: MarketAsset) {
  const runtimeId = resolveAgentAssetRuntimeId(asset)
  uni.navigateTo({
    url: `/chat/pages/index/index?agentId=${encodeURIComponent(runtimeId)}&agentName=${encodeURIComponent(getMarketAssetTitle(asset))}`,
  })
}

function handleSetDefault(asset: MarketAsset) {
  void userPreferencesStore.updatePreferencesPatch({
    defaultAgentId: resolveAgentAssetRuntimeId(asset),
  })
}

function clearDefaultAgent() {
  void userPreferencesStore.updatePreferencesPatch({ defaultAgentId: null })
}
</script>

<style scoped>
.settings-agent-panel {
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

.agent-summary-card,
.agent-note-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 12px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
}

.agent-summary-title,
.agent-note-title,
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.agent-summary-desc,
.agent-note-desc,
.asset-desc,
.context-empty,
.settings-status {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.settings-status-error {
  color: var(--app-danger);
}

.agent-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
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

.section-link-btn-inline {
  align-self: flex-start;
}

.context-empty {
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface);
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.asset-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.asset-card-default {
  border-color: var(--app-accent-soft);
  background: var(--app-accent-soft);
}

.asset-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.asset-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.asset-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.asset-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  font-weight: 600;
}

.asset-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.asset-action-btn {
  margin: 0;
  min-width: 92px;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
  font-size: 12px;
  font-weight: 600;
}

.asset-action-btn::after {
  border: none;
}

.asset-action-btn-active {
  background: var(--app-neutral-strong);
}

.asset-action-btn-ghost {
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

@media (max-width: 720px) {
  .asset-card {
    flex-direction: column;
    align-items: stretch;
  }

  .asset-actions {
    width: 100%;
  }

  .asset-action-btn {
    flex: 1;
  }
}
</style>

