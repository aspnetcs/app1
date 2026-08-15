<template>
  <view class="settings-mcp-panel">
    <view class="settings-panel-title">
      <text>MCP</text>
    </view>

    <view class="mcp-summary-card">
      <text class="section-title">当前模式</text>
      <text class="summary-copy">{{ summaryText }}</text>
    </view>

    <view v-if="serviceUnavailable" class="context-empty">
      <text>{{ unavailableDescription }}</text>
    </view>

    <view v-else class="mcp-section">
      <text class="section-title">模式选择</text>
      <view class="mode-chip-row">
        <button
          v-for="item in modeOptions"
          :key="item.value"
          class="mode-chip"
          :class="{ 'mode-chip-active': preferences.mcpMode === item.value }"
          :disabled="userPreferencesStore.saving"
          @click="handleModeChange(item.value)"
          @tap="handleModeChange(item.value)"
        >
          {{ item.label }}
        </button>
      </view>
      <text class="section-note">
        自动会按当前偏好接入可用服务，手动会优先使用你指定的默认服务，关闭则不启用 MCP。
      </text>
    </view>

    <view v-if="!serviceUnavailable" class="mcp-section">
      <view class="section-header">
        <text class="section-title">已保存服务</text>
        <text class="section-note">{{ preferredServerText }}</text>
      </view>

      <view v-if="userPreferencesStore.savedAssetsLoading.MCP" class="context-empty">
        <text>正在加载已保存 MCP 服务...</text>
      </view>
      <view v-else-if="userPreferencesStore.savedMcpAssets.length === 0" class="context-empty">
        <text>还没有保存的 MCP 服务。你可以先在市场中保存服务，再回来指定默认接入项。</text>
      </view>
      <view v-else class="mcp-list">
        <view
          v-for="asset in userPreferencesStore.savedMcpAssets"
          :key="asset.assetType + ':' + asset.sourceId"
          class="mcp-card"
          :class="{ 'mcp-card-active': isPreferredAsset(asset) }"
        >
          <view class="mcp-copy">
            <view class="mcp-title-row">
              <text class="mcp-title">{{ getMarketAssetTitle(asset) }}</text>
              <text v-if="isPreferredAsset(asset)" class="mcp-tag">默认</text>
            </view>
            <text class="mcp-desc">{{ getMarketAssetSummary(asset) || '暂无说明' }}</text>
            <text class="mcp-meta">{{ getTransportText(asset) }}</text>
          </view>
          <button
            class="mcp-action-btn"
            :class="{ 'mcp-action-btn-active': isPreferredAsset(asset) }"
            :disabled="userPreferencesStore.saving && isPreferredAsset(asset)"
            @click="handlePreferredChange(asset)"
            @tap="handlePreferredChange(asset)"
          >
            {{ isPreferredAsset(asset) ? '当前默认' : '设为默认' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { getMarketAssetSourceId, getMarketAssetSummary, getMarketAssetTitle, type MarketAsset } from '@/api/market'
import type { McpMode } from '@/api/preferences'
import { useUserPreferencesStore } from '@/stores/userPreferences'
import { config } from '@/config'

const userPreferencesStore = useUserPreferencesStore()

const preferences = computed(() => userPreferencesStore.preferences)

const modeOptions: Array<{ value: McpMode; label: string }> = [
  { value: 'disabled', label: '关闭' },
  { value: 'auto', label: '自动' },
  { value: 'manual', label: '手动' },
]

const modeSummary: Record<McpMode, string> = {
  disabled: '当前不会在对话中调用 MCP 服务。',
  auto: '系统会优先按当前偏好自动接入可用服务。',
  manual: '系统会优先使用你指定的默认 MCP 服务。',
}

const serviceUnavailable = computed(() =>
  !config.features.mcp
  || Boolean(userPreferencesStore.error)
  || Boolean(userPreferencesStore.savedAssetErrors.MCP),
)

const summaryText = computed(() => {
  if (!config.features.mcp) {
    return '当前品牌配置未启用 MCP。'
  }
  const currentMode = preferences.value.mcpMode
  const preferred = userPreferencesStore.preferredMcpAsset
  if (currentMode === 'manual' && preferred) {
    return `当前处于手动模式，优先服务为“${getMarketAssetTitle(preferred)}”。`
  }
  if (currentMode === 'manual') {
    return '当前处于手动模式，但你还没有指定默认 MCP 服务。'
  }
  return modeSummary[currentMode]
})

const preferredServerText = computed(() => {
  if (userPreferencesStore.preferredMcpAsset) {
    return `默认服务：${getMarketAssetTitle(userPreferencesStore.preferredMcpAsset)}`
  }
  return '尚未指定默认服务'
})

const unavailableDescription = computed(() => {
  if (!config.features.mcp) {
    return '当前版本先保留 MCP 入口说明，不发起 MCP 配置请求。'
  }
  return 'MCP 服务暂时不可用，这里先保留入口说明，避免设置页直接落成错误状态。'
})

onMounted(() => {
  if (!config.features.mcp) return
  void userPreferencesStore.loadPreferences()
  void userPreferencesStore.loadSavedAssets('MCP')
})

function isPreferredAsset(asset: MarketAsset) {
  const sourceId = getMarketAssetSourceId(asset)
  return Boolean(sourceId) && sourceId === preferences.value.preferredMcpServerId
}

function handleModeChange(mode: McpMode) {
  if (preferences.value.mcpMode === mode) return
  void userPreferencesStore.updatePreferencesPatch({ mcpMode: mode })
}

function handlePreferredChange(asset: MarketAsset) {
  const sourceId = getMarketAssetSourceId(asset) ?? asset.sourceId
  void userPreferencesStore.updatePreferencesPatch({
    preferredMcpServerId: sourceId,
    mcpMode: preferences.value.mcpMode === 'disabled' ? 'manual' : preferences.value.mcpMode,
  })
}

function getTransportText(asset: MarketAsset) {
  const transportType = String(asset.source?.transportType ?? '').trim()
  if (!transportType) {
    return '传输方式待确认'
  }
  return `传输方式：${transportType}`
}
</script>

<style scoped>
.settings-mcp-panel {
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

.mcp-summary-card,
.mcp-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
}

.section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.summary-copy,
.section-note,
.settings-status,
.context-empty,
.mcp-desc,
.mcp-meta {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.settings-status-error {
  color: var(--app-danger);
}

.mode-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mode-chip,
.mcp-action-btn {
  margin: 0;
  min-width: 88px;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  font-size: 12px;
  font-weight: 600;
}

.mode-chip::after,
.mcp-action-btn::after {
  border: none;
}

.mode-chip {
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

.mode-chip-active {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.context-empty {
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface-raised);
}

.mcp-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mcp-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.mcp-card-active {
  border-color: var(--app-accent-soft);
  background: var(--app-accent-soft);
}

.mcp-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mcp-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mcp-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.mcp-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  font-weight: 600;
}

.mcp-action-btn {
  flex-shrink: 0;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

.mcp-action-btn-active {
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
}

@media (max-width: 720px) {
  .section-header,
  .mcp-card {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>

