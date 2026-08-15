<template>
  <view class="settings-skill-panel">
    <view class="settings-panel-title">
      <text>技能库</text>
    </view>

    <view class="skill-info-card">
      <text class="section-title">AI 使用方式</text>
      <text class="skill-copy">
        命中技能后，AI 会加载整份技能内容，并严格遵循其中的约束、流程和执行要求。技能不是检索片段，而是完整指令合同。
      </text>
      <text class="skill-copy">
        当前已保存且可用的技能，会在发送聊天时自动作为候选技能合同加载到 AI 上下文。模型会先判断是否命中技能，命中后必须遵循完整 skill 内容。
      </text>
      <text class="skill-badge">full_instruction</text>
    </view>

    <view v-if="userPreferencesStore.savedAssetErrors.SKILL" class="skill-status skill-status-error">
      <text>{{ userPreferencesStore.savedAssetErrors.SKILL }}</text>
    </view>

    <view class="skill-section">
      <view class="section-header">
        <text class="section-title">已保存技能</text>
        <button class="section-link-btn" @click="openMarketPage" @tap="openMarketPage">前往市场</button>
      </view>

      <view v-if="userPreferencesStore.savedAssetsLoading.SKILL" class="skill-empty-card">
        <text class="skill-copy">正在加载已保存技能...</text>
      </view>
      <view v-else-if="savedSkills.length === 0" class="skill-empty-card">
        <text class="skill-copy">
          还没有保存技能。先去市场保存一个本地技能，回到这里就可以查看完整说明和当前可用状态。
        </text>
        <text class="skill-copy">
          保存后，可用技能会在聊天请求中自动参与技能匹配和执行。
        </text>
      </view>
      <view v-else class="skill-layout">
        <view class="skill-list">
          <view
            v-for="asset in savedSkills"
            :key="asset.assetType + ':' + asset.sourceId"
            class="skill-list-item"
            :class="{ 'skill-list-item-active': selectedSkill?.sourceId === asset.sourceId }"
            @click="selectSkill(asset.sourceId)"
            @tap="selectSkill(asset.sourceId)"
          >
            <view class="skill-list-header">
              <text class="skill-name">{{ getMarketAssetTitle(asset) }}</text>
              <text class="skill-badge" :class="{ 'skill-badge-warning': asset.available === false }">
                {{ asset.available === false ? '不可用' : '已启用' }}
              </text>
            </view>
            <text class="skill-copy">{{ getMarketAssetSummary(asset) || '暂无简介' }}</text>
          </view>
        </view>

        <view v-if="selectedSkill" class="skill-detail-card">
          <view class="skill-detail-head">
            <view class="skill-detail-copy">
              <text class="skill-detail-title">{{ getMarketAssetTitle(selectedSkill) }}</text>
              <text class="skill-copy">{{ getMarketAssetSummary(selectedSkill) || '暂无简介' }}</text>
            </view>
            <button
              class="skill-remove-btn"
              @click="removeSkill(selectedSkill)"
              @tap="removeSkill(selectedSkill)"
            >
              取消保存
            </button>
          </view>

          <view class="skill-meta-grid">
            <view class="skill-meta-item">
              <text class="skill-meta-label">执行语义</text>
              <text class="skill-meta-value">{{ getUsageModeLabel(selectedSkill) }}</text>
            </view>
            <view class="skill-meta-item">
              <text class="skill-meta-label">当前状态</text>
              <text class="skill-meta-value">
                {{ selectedSkill.available === false ? '本地技能缺失' : (selectedSkill.enabled === false ? '已保存但未启用' : '可用') }}
              </text>
            </view>
            <view class="skill-meta-item skill-meta-item-wide">
              <text class="skill-meta-label">AI 使用说明</text>
              <text class="skill-meta-value">{{ getAssetInstruction(selectedSkill) }}</text>
            </view>
            <view class="skill-meta-item skill-meta-item-wide">
              <text class="skill-meta-label">技能文件</text>
              <text class="skill-meta-value">{{ String(selectedSkill.source?.absolutePath || selectedSkill.sourceId) }}</text>
            </view>
          </view>

          <view v-if="selectedSkill.available === false" class="skill-status skill-status-warning">
            <text>本地技能目录里已经找不到这个技能。可以重新同步技能目录，或者直接取消保存这条记录。</text>
          </view>

          <view class="skill-preview-card">
            <text class="skill-preview-title">完整内容预览</text>
            <scroll-view scroll-y class="skill-preview-scroll">
              <text class="skill-preview-content">{{ getAssetContent(selectedSkill) || '当前没有可展示的技能内容。' }}</text>
            </scroll-view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useUserPreferencesStore } from '@/stores/userPreferences'
import {
  getMarketAssetContentPreview,
  getMarketAssetSummary,
  getMarketAssetTitle,
  getMarketAssetUsageInstruction,
  getMarketAssetUsageMode,
  type MarketAsset,
} from '@/api/market'

const userPreferencesStore = useUserPreferencesStore()
const selectedSkillId = ref('')

const savedSkills = computed(() => userPreferencesStore.savedSkillAssets)
const selectedSkill = computed(() =>
  savedSkills.value.find((asset) => asset.sourceId === selectedSkillId.value) ?? savedSkills.value[0] ?? null,
)

onMounted(() => {
  void userPreferencesStore.loadSavedAssets('SKILL')
})

watch(
  savedSkills,
  (assets) => {
    if (!assets.length) {
      selectedSkillId.value = ''
      return
    }
    if (!assets.some((asset) => asset.sourceId === selectedSkillId.value)) {
      selectedSkillId.value = assets[0].sourceId
    }
  },
  { immediate: true },
)

function openMarketPage() {
  uni.navigateTo({ url: '/market/pages/index/index' })
}

function selectSkill(sourceId: string) {
  selectedSkillId.value = sourceId
}

function getUsageModeLabel(asset: MarketAsset) {
  return getMarketAssetUsageMode(asset) === 'full_instruction' ? '完整指令合同' : '技能资产'
}

function getAssetInstruction(asset: MarketAsset) {
  return getMarketAssetUsageInstruction(asset) || '命中技能后必须加载并遵循整份技能内容。'
}

function getAssetContent(asset: MarketAsset) {
  return getMarketAssetContentPreview(asset)
}

function removeSkill(asset: MarketAsset) {
  uni.showModal({
    title: '取消保存',
    content: `确定取消保存技能“${getMarketAssetTitle(asset)}”吗？`,
    success: (result) => {
      if (!result.confirm) return
      void userPreferencesStore.removeSavedAssetRelation('SKILL', asset.sourceId)
    },
  })
}
</script>

<style scoped>
.settings-skill-panel {
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

.skill-info-card,
.skill-empty-card,
.skill-detail-card,
.skill-preview-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
}

.skill-detail-card,
.skill-preview-card {
  background: var(--app-surface-raised);
}

.section-header,
.skill-detail-head,
.skill-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title,
.skill-detail-title,
.skill-name,
.skill-preview-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.skill-copy,
.skill-meta-label,
.skill-meta-value,
.skill-preview-content,
.skill-status {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.skill-badge {
  align-self: flex-start;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 11px;
  font-weight: 600;
}

.skill-badge-warning {
  background: var(--app-warning-soft);
  color: var(--app-warning-contrast);
}

.skill-status {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.skill-status-error {
  border-color: var(--app-danger-border);
  color: var(--app-danger-contrast);
  background: var(--app-danger-soft);
}

.skill-status-warning {
  border-color: var(--app-warning-border);
  color: var(--app-warning-contrast);
  background: var(--app-warning-soft);
}

.skill-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-link-btn,
.skill-remove-btn {
  margin: 0;
  height: 34px;
  line-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  background: var(--app-neutral-muted);
  color: var(--app-neutral-muted-contrast);
  font-size: 12px;
  font-weight: 600;
}

.section-link-btn {
  border: 1px solid var(--app-border-color);
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.section-link-btn::after,
.skill-remove-btn::after {
  border: none;
}

.skill-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skill-list-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
}

.skill-list-item-active {
  border-color: var(--app-accent-soft);
  background: var(--app-accent-soft);
}

.skill-detail-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.skill-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.skill-meta-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 12px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
}

.skill-meta-item-wide {
  grid-column: 1 / -1;
}

.skill-preview-scroll {
  max-height: 320px;
}

.skill-preview-content {
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 720px) {
  .skill-layout {
    grid-template-columns: 1fr;
  }

  .skill-detail-head,
  .section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .skill-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>

