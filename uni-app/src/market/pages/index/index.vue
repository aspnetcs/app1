<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import {
  getMarketAssetSummary,
  getMarketAssetTags,
  getMarketAssetTitle,
  listMarketAssets,
  resolveAgentAssetRuntimeId,
  type MarketAsset,
  type MarketAssetType,
} from '@/api/market'
import { useUserPreferencesStore } from '@/stores/userPreferences'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import {
  MARKET_FILTER_OPTIONS,
  getMarketAssetTypeLabel,
  getMarketCatalogEmptyCopy,
  type MarketFilter,
} from './marketText'

const userPreferencesStore = useUserPreferencesStore()
const runCompatAction = createCompatActionRunner()

const searchQuery = ref('')
const activeFilter = ref<MarketFilter>('ALL')
const loading = ref(false)
const actionLoadingKey = ref('')
const error = ref('')
const catalogAssets = ref<MarketAsset[]>([])
const selectedAssetKey = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

const assetTypeFilter = computed<MarketAssetType | undefined>(() =>
  activeFilter.value === 'ALL' ? undefined : activeFilter.value,
)

const savedAssets = computed(() => {
  const groups = activeFilter.value === 'ALL'
    ? Object.values(userPreferencesStore.savedAssets)
    : [userPreferencesStore.savedAssets[activeFilter.value]]
  return groups.flat().filter((asset) => matchesSearch(asset, searchQuery.value))
})

const selectedAsset = computed(() => {
  if (!selectedAssetKey.value) return null
  return findAssetByKey(selectedAssetKey.value)
})

const selectedSavedAsset = computed(() => {
  if (!selectedAsset.value) return null
  return findSavedAsset(selectedAsset.value)
})

const catalogEmptyCopy = computed(() => getMarketCatalogEmptyCopy(activeFilter.value))
const unsavedCatalogAssets = computed(() =>
  catalogAssets.value.filter(asset => !findSavedAsset(asset)),
)
const visibleAssets = computed(() => [
  ...savedAssets.value,
  ...unsavedCatalogAssets.value,
])

watch(activeFilter, () => {
  void reloadCatalog()
})

watch(searchQuery, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    void reloadCatalog()
  }, 240)
})

onUnmounted(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})

onMounted(() => {
  void userPreferencesStore.loadPreferences()
  void userPreferencesStore.loadAllSavedAssets()
  void reloadCatalog()
})

async function reloadCatalog() {
  loading.value = true
  try {
    const response = await listMarketAssets({
      assetType: assetTypeFilter.value,
      keyword: searchQuery.value.trim() || undefined,
    })
    catalogAssets.value = Array.isArray(response.data) ? response.data : []
    error.value = ''
    if (selectedAssetKey.value && !findAssetByKey(selectedAssetKey.value)) {
      selectedAssetKey.value = ''
    }
  } catch (nextError) {
    catalogAssets.value = []
    error.value = nextError instanceof Error ? nextError.message : '市场目录加载失败'
  } finally {
    loading.value = false
  }
}

function handleFilterChange(filter: MarketFilter, event?: CompatEventLike) {
  runCompatAction(`market-filter:${filter}`, event, () => {
    activeFilter.value = filter
  })
}

function openDetail(asset: MarketAsset, event?: CompatEventLike) {
  runCompatAction(`market-detail:${toAssetKey(asset)}`, event, () => {
    selectedAssetKey.value = toAssetKey(asset)
  })
}

function closeDetail() {
  selectedAssetKey.value = ''
}

function isActionLoading(asset: MarketAsset) {
  return actionLoadingKey.value === toAssetKey(asset)
}

function isSaved(asset: MarketAsset) {
  return Boolean(findSavedAsset(asset))
}

function findSavedAsset(asset: MarketAsset) {
  return userPreferencesStore.savedAssets[asset.assetType].find((item) => item.sourceId === asset.sourceId) ?? null
}

function findAssetByKey(assetKey: string) {
  return [...savedAssets.value, ...catalogAssets.value].find((asset) => toAssetKey(asset) === assetKey) ?? null
}

function toAssetKey(asset: MarketAsset) {
  return `${asset.assetType}:${asset.sourceId}`
}

function matchesSearch(asset: MarketAsset, keyword: string) {
  const normalizedKeyword = keyword.trim().toLowerCase()
  if (!normalizedKeyword) return true
  const haystack = [
    getMarketAssetTitle(asset),
    getMarketAssetSummary(asset),
    asset.category,
    asset.assetType,
    asset.sourceId,
    getMarketAssetTags(asset).join(' '),
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return haystack.includes(normalizedKeyword)
}

function openAgentConversation(asset: MarketAsset) {
  const runtimeId = resolveAgentAssetRuntimeId(asset)
  uni.navigateTo({
    url: `/chat/pages/index/index?agentId=${encodeURIComponent(runtimeId)}&agentName=${encodeURIComponent(getMarketAssetTitle(asset))}`,
  })
}

function getPrimaryActionLabel(asset: MarketAsset) {
  if (asset.assetType === 'AGENT') {
    return isSaved(asset) ? '进入对话' : '保存并使用'
  }
  return isSaved(asset) ? '已保存' : '保存'
}

function getSecondaryActionLabel(asset: MarketAsset) {
  return isSaved(asset) ? '取消保存' : '预览'
}

async function handlePrimaryAction(asset: MarketAsset, event?: CompatEventLike) {
  runCompatAction(`market-primary:${toAssetKey(asset)}`, event, async () => {
    if (asset.assetType === 'AGENT') {
      const savedAsset = findSavedAsset(asset)
      if (savedAsset) {
        openAgentConversation(savedAsset)
        return
      }
      const nextAsset = await saveAsset(asset)
      if (nextAsset) {
        openAgentConversation(nextAsset)
      }
      return
    }
    if (isSaved(asset)) {
      return
    }
    await saveAsset(asset)
  })
}

function handleSecondaryAction(asset: MarketAsset, event?: CompatEventLike) {
  runCompatAction(`market-secondary:${toAssetKey(asset)}`, event, () => {
    if (!isSaved(asset)) {
      openDetail(asset)
      return
    }
    uni.showModal({
      title: '取消保存',
      content: `确定取消保存“${getMarketAssetTitle(asset)}”吗？`,
      success: (result) => {
        if (!result.confirm) return
        void unsaveAsset(asset)
      },
    })
  })
}

async function saveAsset(asset: MarketAsset) {
  actionLoadingKey.value = toAssetKey(asset)
  try {
    const savedAsset = await userPreferencesStore.saveAssetRelation(asset.assetType, asset.sourceId)
    error.value = ''
    if (savedAsset) {
      if (selectedAssetKey.value === toAssetKey(asset)) {
        selectedAssetKey.value = toAssetKey(savedAsset)
      }
      return savedAsset
    }
    return findSavedAsset(asset) ?? asset
  } catch (nextError) {
    error.value = nextError instanceof Error ? nextError.message : '保存失败'
    return null
  } finally {
    actionLoadingKey.value = ''
  }
}

async function unsaveAsset(asset: MarketAsset) {
  actionLoadingKey.value = toAssetKey(asset)
  try {
    await userPreferencesStore.removeSavedAssetRelation(asset.assetType, asset.sourceId)
    error.value = ''
    if (selectedAssetKey.value === toAssetKey(asset) && !findSavedAsset(asset)) {
      const nextCatalogAsset = catalogAssets.value.find((item) => toAssetKey(item) === toAssetKey(asset))
      selectedAssetKey.value = nextCatalogAsset ? toAssetKey(nextCatalogAsset) : ''
    }
  } catch (nextError) {
    error.value = nextError instanceof Error ? nextError.message : '取消保存失败'
  } finally {
    actionLoadingKey.value = ''
  }
}

function getSectionBadge(asset: MarketAsset) {
  return isSaved(asset) ? '已保存' : getMarketAssetTypeLabel(asset.assetType)
}

function getDetailPrimaryLabel(asset: MarketAsset) {
  if (asset.assetType === 'AGENT') {
    return isSaved(asset) ? '进入对话' : '保存并使用'
  }
  return isSaved(asset) ? '已保存' : '保存'
}

function handleDetailSecondaryAction() {
  if (!selectedAsset.value) return
  if (!selectedSavedAsset.value) {
    closeDetail()
    return
  }
  handleSecondaryAction(selectedAsset.value)
}
</script>

<template>
  <AppLayout>
    <view class="market-page">
      <view class="market-header">
        <view class="market-title-wrap">
          <text class="market-title">市场</text>
          <text class="market-subtitle">统一管理智能体、知识、MCP 服务以及后续可接入的能力资源。</text>
        </view>
        <input
          v-model="searchQuery"
          class="market-search"
          placeholder="搜索标题、简介、分类或标签"
        />
      </view>

      <scroll-view scroll-x class="market-filter-scroll">
        <view class="market-filter-row">
          <view
            v-for="item in MARKET_FILTER_OPTIONS"
            :key="item.value"
            class="market-filter-chip"
            :class="{ 'market-filter-chip-active': activeFilter === item.value }"
            @click="handleFilterChange(item.value, $event)"
            @tap="handleFilterChange(item.value, $event)"
          >
            <text>{{ item.label }}</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="error" class="market-status market-status-error">
        <text>{{ error }}</text>
      </view>

      <scroll-view scroll-y class="market-content">
        <view class="market-section market-section-flat">
          <view v-if="visibleAssets.length === 0" class="market-empty">
            <text v-if="loading">正在加载市场目录...</text>
            <text v-else>{{ catalogEmptyCopy }}</text>
          </view>
          <view v-else class="market-card-list">
            <view
              v-for="asset in visibleAssets"
              :key="toAssetKey(asset)"
              class="market-card"
              @click="openDetail(asset, $event)"
              @tap="openDetail(asset, $event)"
            >
              <view class="market-card-copy">
                <view class="market-card-title-row">
                  <text class="market-card-title">{{ getMarketAssetTitle(asset) }}</text>
                  <text class="market-card-badge">{{ getSectionBadge(asset) }}</text>
                </view>
                <text class="market-card-desc">{{ getMarketAssetSummary(asset) || '暂无简介' }}</text>
                <view class="market-tag-row">
                  <text
                    v-for="tag in getMarketAssetTags(asset).slice(0, 3)"
                    :key="toAssetKey(asset) + ':' + tag"
                    class="market-tag"
                  >
                    {{ tag }}
                  </text>
                </view>
              </view>
              <view class="market-card-actions">
                <button
                  class="market-action-btn market-action-btn-primary"
                  :disabled="isActionLoading(asset) || (asset.assetType !== 'AGENT' && isSaved(asset))"
                  @click.stop="handlePrimaryAction(asset, $event)"
                  @tap.stop="handlePrimaryAction(asset, $event)"
                >
                  {{ isActionLoading(asset) ? '处理中...' : getPrimaryActionLabel(asset) }}
                </button>
                <button
                  class="market-action-btn market-action-btn-secondary"
                  :disabled="isActionLoading(asset)"
                  @click.stop="handleSecondaryAction(asset, $event)"
                  @tap.stop="handleSecondaryAction(asset, $event)"
                >
                  {{ getSecondaryActionLabel(asset) }}
                </button>
              </view>
            </view>
          </view>
          <view v-if="loading && visibleAssets.length > 0" class="market-empty market-loading-hint">
            <text>正在加载更多内容...</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="selectedAsset" class="market-detail-backdrop" @click="closeDetail" @tap="closeDetail"></view>
      <view v-if="selectedAsset" class="market-detail-wrap">
        <view class="market-detail-card" @click.stop @tap.stop>
          <view class="market-detail-header">
            <view class="market-detail-copy">
              <text class="market-detail-title">{{ getMarketAssetTitle(selectedAsset) }}</text>
              <text class="market-detail-meta">
                {{ getMarketAssetTypeLabel(selectedAsset.assetType) }} · {{ selectedSavedAsset ? '已保存' : '未保存' }}
              </text>
            </view>
            <view class="market-detail-close" @click="closeDetail" @tap="closeDetail">
              <text>x</text>
            </view>
          </view>

          <scroll-view scroll-y class="market-detail-body">
            <view class="market-detail-section">
              <text class="market-detail-section-title">简介</text>
              <text class="market-detail-section-copy">{{ getMarketAssetSummary(selectedAsset) || '暂无简介' }}</text>
            </view>
            <view class="market-detail-section">
              <text class="market-detail-section-title">分类</text>
              <text class="market-detail-section-copy">{{ selectedAsset.category || '未分类' }}</text>
            </view>
            <view class="market-detail-section">
              <text class="market-detail-section-title">标签</text>
              <view class="market-tag-row">
                <text
                  v-for="tag in getMarketAssetTags(selectedAsset)"
                  :key="toAssetKey(selectedAsset) + ':detail:' + tag"
                  class="market-tag"
                >
                  {{ tag }}
                </text>
                <text v-if="getMarketAssetTags(selectedAsset).length === 0" class="market-detail-section-copy">暂无标签</text>
              </view>
            </view>
            <view class="market-detail-section">
              <text class="market-detail-section-title">来源信息</text>
              <text class="market-detail-section-copy">Source ID: {{ selectedAsset.sourceId }}</text>
              <text v-if="selectedAsset.source?.author" class="market-detail-section-copy">作者: {{ selectedAsset.source.author }}</text>
            </view>
          </scroll-view>

          <view class="market-detail-footer">
            <button
              class="market-action-btn market-action-btn-primary"
              :disabled="isActionLoading(selectedAsset) || (selectedAsset.assetType !== 'AGENT' && Boolean(selectedSavedAsset))"
              @click="handlePrimaryAction(selectedAsset, $event)"
              @tap="handlePrimaryAction(selectedAsset, $event)"
            >
              {{ isActionLoading(selectedAsset) ? '处理中...' : getDetailPrimaryLabel(selectedAsset) }}
            </button>
            <button
              class="market-action-btn market-action-btn-secondary"
              :disabled="isActionLoading(selectedAsset)"
              @click="handleDetailSecondaryAction"
              @tap="handleDetailSecondaryAction"
            >
              {{ selectedSavedAsset ? '取消保存' : '关闭' }}
            </button>
          </view>
        </view>
      </view>
    </view>
  </AppLayout>
</template>

<style scoped>
.market-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 20px 16px 0;
  box-sizing: border-box;
  gap: 16px;
}

.market-header {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.market-title-wrap {
  display: none;
}

.market-title {
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--app-text-primary);
}

.market-subtitle {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.market-search {
  height: 42px;
  padding: 0 14px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface);
  font-size: 14px;
  color: var(--app-text-primary);
}

.market-filter-scroll {
  white-space: nowrap;
}

.market-filter-row {
  display: flex;
  gap: 10px;
  padding-bottom: 4px;
}

.market-filter-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  font-size: 12px;
  font-weight: 600;
}

.market-filter-chip-active {
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
}

.market-status {
  font-size: 13px;
  line-height: 1.7;
}

.market-status-error {
  color: var(--app-danger);
}

.market-content {
  flex: 1;
  min-height: 0;
}

.market-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 0;
}

.market-section-flat {
  padding-bottom: 20px;
}

.market-section-header {
  display: none;
}

.market-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.market-section-subtitle {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.market-empty {
  padding: 18px 16px;
  border-radius: 12px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface-muted);
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.market-loading-hint {
  padding: 12px 16px;
  border-style: solid;
  border-color: var(--app-border-color);
  background: var(--app-surface-muted);
  font-size: 12px;
}

.market-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.market-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border-radius: 14px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.market-card-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.market-card-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.market-card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.market-card-badge {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  font-weight: 600;
}

.market-card-desc,
.market-detail-meta,
.market-detail-section-copy {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.market-tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.market-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  font-size: 11px;
}

.market-card-actions,
.market-detail-footer {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.market-action-btn {
  margin: 0;
  min-width: 96px;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  font-size: 12px;
  font-weight: 600;
}

.market-action-btn::after {
  border: none;
}

.market-action-btn-primary {
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
}

.market-action-btn-secondary {
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
}

.market-detail-backdrop {
  position: fixed;
  inset: 0;
  background: var(--app-overlay);
  z-index: 40;
}

.market-detail-wrap {
  position: fixed;
  inset: 0;
  z-index: 41;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}

.market-detail-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 520px;
  max-height: 80vh;
  border-radius: 20px;
  background: var(--app-surface-raised);
  overflow: hidden;
}

.market-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.market-detail-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.market-detail-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text-primary);
}

.market-detail-close {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  display: flex;
  align-items: center;
  justify-content: center;
}

.market-detail-body {
  flex: 1;
  min-height: 0;
  padding: 18px 20px;
  box-sizing: border-box;
}

.market-detail-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.market-detail-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.market-detail-footer {
  padding: 16px 20px 20px;
  border-top: 1px solid var(--app-border-color-soft);
}

@media (max-width: 720px) {
  .market-card,
  .market-detail-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .market-card-actions {
    width: 100%;
  }

  .market-action-btn {
    flex: 1;
  }
}
</style>
