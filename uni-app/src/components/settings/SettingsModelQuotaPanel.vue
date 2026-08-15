<template>
  <view class="settings-model-panel">
    <view class="settings-panel-title">
      <text>模型额度</text>
    </view>

    <view v-if="isInitialLoading" class="panel-state-card">
      <text class="panel-state-title">正在同步额度信息</text>
      <text class="panel-state-desc">正在获取当前账号角色、配额和计费模型信息。</text>
    </view>

    <template v-else>
      <view v-if="quotaError" class="panel-state-card panel-state-card-error">
        <text class="panel-state-title">额度信息加载失败</text>
        <text class="panel-state-desc">{{ quotaError }}</text>
        <button
          class="panel-action"
          :disabled="refreshing"
          @click="handleRefreshActivate"
          @tap="handleRefreshActivate"
        >
          {{ refreshing ? '重新加载中...' : '重新加载' }}
        </button>
      </view>

      <view v-else-if="quotaData" class="summary-grid">
        <view
          v-for="card in quotaSummaryCards"
          :key="card.label"
          class="summary-card"
          :class="{ 'summary-card-emphasis': card.emphasis }"
        >
          <text class="summary-card-label">{{ card.label }}</text>
          <text class="summary-card-value">{{ card.value }}</text>
          <text class="summary-card-hint">{{ card.hint }}</text>
          <view v-if="card.progress != null" class="summary-card-progress">
            <view class="summary-card-progress-bar" :style="{ width: `${card.progress}%` }"></view>
          </view>
        </view>
      </view>

      <view v-else class="panel-state-card">
        <text class="panel-state-title">暂无额度信息</text>
        <text class="panel-state-desc">当前账号还没有可展示的额度数据。</text>
        <button
          class="panel-action"
          :disabled="refreshing"
          @click="handleRefreshActivate"
          @tap="handleRefreshActivate"
        >
          {{ refreshing ? '重新加载中...' : '重新加载' }}
        </button>
      </view>

      <view class="panel-section">
        <view class="section-header">
          <view class="section-copy">
            <text class="section-title">积分概览</text>
            <text class="section-subtitle">展示当前账户的积分余额、周期额度和人工调整信息。</text>
          </view>
        </view>

        <template v-if="quotaData?.credits">
          <view class="credits-hero">
            <text class="credits-hero-label">有效余额</text>
            <text class="credits-hero-value">{{ effectiveBalanceDisplay }}</text>
            <text class="credits-hero-note">{{ creditsStatusText }}</text>
          </view>

          <view class="credits-grid">
            <view v-for="item in creditsSummaryItems" :key="item.label" class="credits-item">
              <text class="credits-item-label">{{ item.label }}</text>
              <text class="credits-item-value">{{ item.value }}</text>
            </view>
          </view>
        </template>

        <view v-else class="panel-state-card">
          <text class="panel-state-title">暂无积分摘要</text>
          <text class="panel-state-desc">当前账号暂未返回积分账户信息。</text>
        </view>
      </view>

      <view class="panel-section">
        <view class="section-header">
          <view class="section-copy">
            <text class="section-title">计费模型</text>
            <text class="section-subtitle">展示当前模型目录中已开启计费或已配置价格的模型。</text>
          </view>
          <button
            class="panel-action panel-action-secondary"
            :disabled="refreshing"
            @click="handleRefreshActivate"
            @tap="handleRefreshActivate"
          >
            {{ refreshing ? '刷新中...' : '刷新' }}
          </button>
        </view>

        <view v-if="showModelLoadingState" class="panel-state-card">
          <text class="panel-state-title">正在加载模型目录</text>
          <text class="panel-state-desc">正在同步模型价格和可用状态。</text>
        </view>

        <view v-else-if="modelStore.loadError" class="panel-state-card panel-state-card-error">
          <text class="panel-state-title">模型目录加载失败</text>
          <text class="panel-state-desc">{{ modelStore.loadError }}</text>
        </view>

        <view v-else-if="billedModelViews.length === 0" class="panel-state-card">
          <text class="panel-state-title">暂无计费模型</text>
          <text class="panel-state-desc">{{ billedModelsEmptyText }}</text>
        </view>

        <view v-else class="pricing-list">
          <view v-for="item in billedModelViews" :key="item.id" class="pricing-item">
            <view class="pricing-item-header">
              <view class="pricing-item-meta">
                <image
                  class="pricing-item-avatar"
                  :src="modelStore.getAvatarPath(item.avatar || 'default')"
                  mode="aspectFill"
                />
                <view class="pricing-item-copy">
                  <text class="pricing-item-name">{{ item.name }}</text>
                  <text class="pricing-item-desc">
                    {{ item.description || '当前模型已开启计费或配置了价格。' }}
                  </text>
                </view>
              </view>
              <text class="pricing-item-tag">{{ item.tag }}</text>
            </view>

            <view v-if="item.metrics.length" class="pricing-metrics">
              <view v-for="metric in item.metrics" :key="metric.label" class="pricing-metric">
                <text class="pricing-metric-label">{{ metric.label }}</text>
                <text class="pricing-metric-value">{{ metric.value }}</text>
              </view>
            </view>

            <text v-else class="pricing-item-note">
              当前模型已开启计费，但接口暂未返回更具体的价格明细。
            </text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  getUserQuota,
  type ModelCatalogItem,
  type NumericLike,
  type UserQuotaCreditsSummary,
  type UserQuotaResponse,
} from '@/api/models'
import { useModelStore } from '@/stores/models'
import { extractErrorMessage } from '@/utils/errorMessage'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

type SummaryCard = {
  label: string
  value: string
  hint: string
  emphasis?: boolean
  progress?: number | null
}

type CreditsSummaryItem = {
  label: string
  value: string
}

type PricingMetric = {
  label: string
  value: string
}

type BilledModelView = {
  id: string
  avatar: string
  name: string
  description: string
  tag: string
  metrics: PricingMetric[]
}

const modelStore = useModelStore()
const runCompatAction = createCompatActionRunner()

const quotaData = ref<UserQuotaResponse | null>(null)
const quotaError = ref('')
const refreshing = ref(false)
const initialLoadFinished = ref(false)

const isInitialLoading = computed(
  () => !initialLoadFinished.value && (refreshing.value || modelStore.loading),
)
const showModelLoadingState = computed(() => modelStore.loading && !modelStore.loaded)

const quotaSummaryCards = computed<SummaryCard[]>(() => {
  const data = quotaData.value
  if (!data) {
    return []
  }

  const dailyUsed = normalizeNumber(data.dailyQuota?.used)
  const dailyLimit = normalizeNumber(data.dailyQuota?.limit)
  const dailyProgress = resolveDailyQuotaProgress(dailyUsed, dailyLimit)

  return [
    {
      label: '当前角色',
      value: formatRole(data.role),
      hint: data.role ? `角色标识：${data.role}` : '当前账号未返回角色标识',
      emphasis: true,
    },
    {
      label: '每日额度',
      value: formatUsage(dailyUsed, dailyLimit),
      hint:
        dailyLimit != null && dailyLimit >= 0
          ? `今日已使用 ${formatCount(dailyUsed)} 次`
          : '当前角色未限制每日额度',
      progress: dailyProgress,
    },
    {
      label: '每分钟请求',
      value: formatLimit(data.rateLimit?.perMinute),
      hint: '当前账号的全局请求频率限制',
    },
    {
      label: '模型频率',
      value: formatLimit(data.rateLimit?.modelPerMinute),
      hint: '单模型维度的每分钟请求限制',
    },
    {
      label: '可用模型数',
      value: formatAllowedModelCount(data.allowedModelCount),
      hint: '当前角色允许访问的模型数量',
    },
  ]
})

const creditsStatusText = computed(() => {
  const credits = quotaData.value?.credits
  if (!credits) {
    return ''
  }
  if (credits.creditsSystemEnabled === false) {
    return '积分系统未启用'
  }
  if (credits.freeModeEnabled) {
    return '当前为免积分模式，调用不会扣减余额'
  }
  if (credits.hasAccount === false) {
    return '当前账号尚未开通积分账户'
  }
  return '当前余额会随模型结算实时变化'
})

const effectiveBalanceDisplay = computed(() =>
  formatCreditsValue(quotaData.value?.credits?.effectiveBalance ?? quotaData.value?.credits?.creditBalance),
)

const creditsSummaryItems = computed<CreditsSummaryItem[]>(() => {
  const credits = quotaData.value?.credits
  if (!credits) {
    return []
  }

  const items: CreditsSummaryItem[] = [
    {
      label: '账户状态',
      value: credits.hasAccount === false ? '未开通' : '已开通',
    },
    {
      label: '累计消耗',
      value: formatCreditsValue(credits.creditUsed),
    },
  ]

  if (credits.unlimited === true) {
    items.push({
      label: '周期额度',
      value: `不限 / ${formatPeriodType(credits.periodType)}`,
    })
  } else if (hasNumericValue(credits.periodCredits)) {
    items.push({
      label: '周期额度',
      value: `${formatCreditsValue(credits.periodCredits)} / ${formatPeriodType(credits.periodType)}`,
    })
  }

  const manualAdjustment = credits.manualCreditAdjustment ?? credits.manualAdjustment
  if (hasNumericValue(manualAdjustment) && normalizeNumber(manualAdjustment) !== 0) {
    items.push({
      label: '人工调整',
      value: formatSignedCreditsValue(manualAdjustment),
    })
  }

  const periodRange = formatPeriodRange(credits)
  if (periodRange) {
    items.push({
      label: '周期区间',
      value: periodRange,
    })
  }

  if (credits.role) {
    items.push({
      label: '积分角色',
      value: formatRole(credits.role),
    })
  }

  return items
})

const billedModelViews = computed<BilledModelView[]>(() =>
  modelStore.models
    .filter(isBilledModel)
    .map(model => ({
      id: model.id,
      avatar: model.avatar,
      name: model.name,
      description: model.description,
      tag: model.billingEnabled ? '已开启计费' : '已配置价格',
      metrics: resolvePricingMetrics(model),
    })),
)

const billedModelsEmptyText = computed(() => {
  const credits = quotaData.value?.credits
  if (!modelStore.loaded) {
    return '模型目录尚未完成同步。'
  }
  if (modelStore.models.length === 0) {
    return '当前没有可展示的模型目录数据。'
  }
  if (credits?.creditsSystemEnabled === false) {
    return '积分系统未启用，当前不展示计费模型价格。'
  }
  if (credits?.freeModeEnabled) {
    return '当前为免积分模式，计费模型价格不会影响实际扣费。'
  }
  return '当前模型目录里还没有开启计费或配置价格的模型。'
})

function normalizeNumber(value: NumericLike) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) {
      return parsed
    }
  }
  return null
}

function hasNumericValue(value: NumericLike) {
  return normalizeNumber(value) != null
}

function formatCount(value: NumericLike) {
  const normalized = normalizeNumber(value)
  if (normalized == null) {
    return '--'
  }
  return normalized.toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

function formatCreditsValue(value: NumericLike) {
  const normalized = normalizeNumber(value)
  if (normalized == null) {
    return '--'
  }
  return normalized.toLocaleString('zh-CN', {
    minimumFractionDigits: normalized % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 2,
  })
}

function formatSignedCreditsValue(value: NumericLike) {
  const normalized = normalizeNumber(value)
  if (normalized == null) {
    return '--'
  }
  const prefix = normalized > 0 ? '+' : ''
  return `${prefix}${formatCreditsValue(normalized)}`
}

function formatUsd(value: NumericLike) {
  const normalized = normalizeNumber(value)
  if (normalized == null) {
    return '--'
  }
  return `$${normalized.toLocaleString('en-US', {
    minimumFractionDigits: normalized < 1 ? 2 : 0,
    maximumFractionDigits: 4,
  })}`
}

function formatUsage(used: NumericLike, limit: NumericLike) {
  const formattedUsed = formatCount(used)
  const normalizedLimit = normalizeNumber(limit)
  if (normalizedLimit == null) {
    return `${formattedUsed} / --`
  }
  if (normalizedLimit < 0) {
    return `${formattedUsed} / 不限`
  }
  return `${formattedUsed} / ${formatCount(normalizedLimit)}`
}

function formatLimit(value: NumericLike) {
  const normalized = normalizeNumber(value)
  if (normalized == null) {
    return '--'
  }
  if (normalized < 0) {
    return '不限'
  }
  return `${formatCount(normalized)} / 分钟`
}

function formatAllowedModelCount(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return '--'
  }
  if (value < 0) {
    return '不限'
  }
  return formatCount(value)
}

function resolveDailyQuotaProgress(used: NumericLike, limit: NumericLike) {
  const normalizedUsed = normalizeNumber(used)
  const normalizedLimit = normalizeNumber(limit)
  if (normalizedUsed == null || normalizedLimit == null || normalizedLimit <= 0) {
    return null
  }
  return Math.min(100, Math.max(0, (normalizedUsed / normalizedLimit) * 100))
}

function formatRole(role?: string | null) {
  const normalized = role?.trim().toLowerCase()
  if (!normalized) {
    return '未返回'
  }
  if (normalized === 'admin' || normalized === 'owner') {
    return '管理员'
  }
  if (normalized === 'svip') {
    return 'SVIP'
  }
  if (normalized === 'vip') {
    return 'VIP'
  }
  if (normalized === 'guest') {
    return '游客'
  }
  if (normalized === 'user' || normalized === 'pending') {
    return '普通用户'
  }
  return role ?? '未返回'
}

function formatPeriodType(periodType?: string | null) {
  const normalized = periodType?.trim().toLowerCase()
  if (!normalized) {
    return '当前周期'
  }
  if (normalized === 'day' || normalized === 'daily') {
    return '每日'
  }
  if (normalized === 'week' || normalized === 'weekly') {
    return '每周'
  }
  if (normalized === 'month' || normalized === 'monthly') {
    return '每月'
  }
  if (normalized === 'year' || normalized === 'yearly') {
    return '每年'
  }
  return periodType ?? '当前周期'
}

function formatDateTime(value?: string | null) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function formatPeriodRange(credits: UserQuotaCreditsSummary) {
  const start = formatDateTime(credits.periodStartAt)
  const end = formatDateTime(credits.periodEndAt)
  if (!start && !end) {
    return ''
  }
  if (start && end) {
    return `${start} - ${end}`
  }
  return start || end
}

function isBilledModel(model: ModelCatalogItem) {
  return (
    model.billingEnabled === true ||
    hasNumericValue(model.requestPriceUsd) ||
    hasNumericValue(model.promptPriceUsd) ||
    hasNumericValue(model.inputPriceUsdPer1M) ||
    hasNumericValue(model.outputPriceUsdPer1M)
  )
}

function resolvePricingMetrics(model: ModelCatalogItem) {
  const metrics: PricingMetric[] = []

  if (hasNumericValue(model.requestPriceUsd)) {
    metrics.push({
      label: '按次请求',
      value: `${formatUsd(model.requestPriceUsd)} / 次`,
    })
  }

  if (hasNumericValue(model.promptPriceUsd)) {
    metrics.push({
      label: '按次提示',
      value: `${formatUsd(model.promptPriceUsd)} / 次`,
    })
  }

  if (hasNumericValue(model.inputPriceUsdPer1M)) {
    metrics.push({
      label: '输入价格',
      value: `${formatUsd(model.inputPriceUsdPer1M)} / 1M tokens`,
    })
  }

  if (hasNumericValue(model.outputPriceUsdPer1M)) {
    metrics.push({
      label: '输出价格',
      value: `${formatUsd(model.outputPriceUsdPer1M)} / 1M tokens`,
    })
  }

  return metrics
}

async function refreshPanel() {
  if (refreshing.value) {
    return
  }

  refreshing.value = true
  quotaError.value = ''

  const [quotaResult] = await Promise.allSettled([
    getUserQuota(),
    modelStore.fetchModels(),
  ])

  if (quotaResult.status === 'fulfilled') {
    quotaData.value =
      quotaResult.value.data && typeof quotaResult.value.data === 'object'
        ? quotaResult.value.data
        : null
  } else {
    quotaError.value = extractErrorMessage(quotaResult.reason, '模型额度加载失败')
  }

  refreshing.value = false
  initialLoadFinished.value = true
}

function handleRefreshActivate(event?: CompatEventLike) {
  runCompatAction('settings-model-quota-refresh', event, () => {
    void refreshPanel()
  })
}

onMounted(() => {
  void refreshPanel()
})
</script>

<style scoped>
.settings-model-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.settings-panel-title {
  padding-bottom: 20px;
  margin-bottom: 24px;
  border-bottom: 1px solid var(--app-border-color-soft);
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.panel-section {
  margin-top: 24px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.panel-state-card,
.credits-hero,
.pricing-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--app-border-color);
  border-radius: 14px;
  background: var(--app-surface-muted);
}

.summary-card-emphasis {
  border-color: var(--app-accent-soft);
  background: linear-gradient(135deg, var(--app-surface) 0%, var(--app-accent-soft) 100%);
}

.summary-card-label,
.panel-state-title,
.section-title,
.pricing-item-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.summary-card-value,
.credits-hero-value {
  font-size: 24px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--app-text-primary);
}

.summary-card-hint,
.panel-state-desc,
.section-subtitle,
.credits-hero-label,
.credits-hero-note,
.credits-item-label,
.pricing-item-desc,
.pricing-item-note,
.pricing-metric-label {
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-text-secondary);
}

.summary-card-progress {
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--app-fill-soft);
}

.summary-card-progress-bar {
  height: 100%;
  border-radius: 999px;
  background: var(--app-accent-gradient);
}

.panel-state-card-error {
  border-color: var(--app-warning-border);
  background: var(--app-warning-soft);
}

.panel-action {
  min-width: 96px;
  height: 36px;
  line-height: 36px;
  margin: 4px 0 0;
  padding: 0 16px;
  align-self: flex-start;
  border: none;
  border-radius: 10px;
  background: var(--app-neutral-strong);
  color: var(--app-neutral-strong-contrast);
  font-size: 13px;
  font-weight: 500;
}

.panel-action::after {
  border: none;
}

.panel-action[disabled] {
  opacity: 0.65;
}

.panel-action-secondary {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.credits-grid {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 12px;
}

.credits-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid var(--app-border-color);
  border-radius: 12px;
  background: var(--app-surface-raised);
}

.credits-item-value,
.pricing-metric-value,
.pricing-item-tag {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.pricing-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pricing-item-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.pricing-item-meta {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
}

.pricing-item-avatar {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: 10px;
  background: var(--app-surface-raised);
}

.pricing-item-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.pricing-item-name,
.pricing-item-desc {
  display: block;
}

.pricing-item-desc {
  overflow: hidden;
  text-overflow: ellipsis;
}

.pricing-item-tag {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--app-info-soft);
  color: var(--app-info-contrast);
}

.pricing-metrics {
  display: grid;
  grid-template-columns: repeat(1, minmax(0, 1fr));
  gap: 10px;
}

.pricing-metric {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--app-border-color);
  border-radius: 12px;
  background: var(--app-surface-raised);
}

@media (min-width: 720px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .credits-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pricing-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 960px) {
  .summary-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
