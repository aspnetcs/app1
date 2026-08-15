<template>
  <view class="settings-appearance-panel">
    <view class="settings-panel-title">
      <text>外观</text>
    </view>

    <view class="settings-panel-scroll">
      <view class="appearance-summary-card">
        <text class="section-title">当前偏好</text>
        <text class="summary-copy">{{ summaryText }}</text>
      </view>

      <view v-if="userPreferencesStore.error" class="settings-status settings-status-error">
        <text>{{ userPreferencesStore.error }}</text>
      </view>

      <view class="appearance-section">
        <text class="section-title">主题模式</text>
        <view class="option-grid">
          <button
            v-for="item in themeModeOptions"
            :key="item.value"
            class="option-chip"
            :class="{ 'option-chip-active': draftPreferences.themeMode === item.value }"
            @click="previewAppearance({ themeMode: item.value })"
            @tap="previewAppearance({ themeMode: item.value })"
          >
            {{ item.label }}
          </button>
        </view>
      </view>

      <view class="appearance-section">
        <text class="section-title">代码主题</text>
        <view class="option-grid">
          <button
            v-for="item in codeThemeOptions"
            :key="item.value"
            class="option-chip"
            :class="{ 'option-chip-active': draftPreferences.codeTheme === item.value }"
            @click="previewAppearance({ codeTheme: item.value })"
            @tap="previewAppearance({ codeTheme: item.value })"
          >
            {{ item.label }}
          </button>
        </view>
      </view>

      <view class="appearance-section">
        <text class="section-title">字号偏好</text>
        <view class="option-grid">
          <button
            v-for="item in fontScaleOptions"
            :key="item.value"
            class="option-chip"
            :class="{ 'option-chip-active': draftPreferences.fontScale === item.value }"
            @click="previewAppearance({ fontScale: item.value })"
            @tap="previewAppearance({ fontScale: item.value })"
          >
            {{ item.label }}
          </button>
        </view>
      </view>

      <view class="appearance-section">
        <text class="section-title">上下间距 (Vertical)</text>
        <view class="option-grid">
          <button
            v-for="item in spacingOptions"
            :key="item.value"
            class="option-chip"
            :class="{ 'option-chip-active': draftPreferences.spacingVertical === item.value }"
            @click="previewAppearance({ spacingVertical: item.value })"
            @tap="previewAppearance({ spacingVertical: item.value })"
          >
            {{ item.label }}
          </button>
        </view>
      </view>

      <view class="appearance-section">
        <text class="section-title">左右间距 (Horizontal)</text>
        <view class="option-grid">
          <button
            v-for="item in spacingOptions"
            :key="item.value"
            class="option-chip"
            :class="{ 'option-chip-active': draftPreferences.spacingHorizontal === item.value }"
            @click="previewAppearance({ spacingHorizontal: item.value })"
            @tap="previewAppearance({ spacingHorizontal: item.value })"
          >
            {{ item.label }}
          </button>
        </view>
      </view>
    </view>

    <view class="settings-actions">
      <button class="settings-btn settings-btn-secondary" @click="resetPreferences" @tap="resetPreferences">重置默认</button>
      <button
        class="settings-btn settings-btn-primary"
        :disabled="userPreferencesStore.saving"
        @click="savePreferences"
        @tap="savePreferences"
      >
        {{ userPreferencesStore.saving ? '保存中...' : '保存设置' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, watch } from 'vue'
import type { CodeTheme, FontScale, ThemeMode, UserPreferences } from '@/api/preferences'
import { useThemeStore } from '@/stores/theme'
import { useUserPreferencesStore } from '@/stores/userPreferences'

const userPreferencesStore = useUserPreferencesStore()
const themeStore = useThemeStore()

const draftPreferences = reactive<Partial<UserPreferences>>({})

watch(
  () => userPreferencesStore.preferences,
  (newPrefs) => {
    Object.assign(draftPreferences, newPrefs)
  },
  { immediate: true, deep: true }
)

const themeModeOptions: Array<{ value: ThemeMode; label: string }> = [
  { value: 'system', label: '跟随系统' },
  { value: 'light', label: '浅色' },
  { value: 'dark', label: '深色' },
]

const codeThemeOptions: Array<{ value: CodeTheme; label: string }> = [
  { value: 'system', label: '跟随主题' },
  { value: 'light', label: '浅色代码' },
  { value: 'dark', label: '深色代码' },
]

const fontScaleOptions: Array<{ value: FontScale; label: string }> = [
  { value: 'sm', label: '紧凑' },
  { value: 'md', label: '标准' },
  { value: 'lg', label: '舒适' },
]

const spacingOptions = [
  { value: '12px', label: '紧凑 (12px)' },
  { value: '16px', label: '标准 (16px)' },
  { value: '20px', label: '宽松 (20px)' },
]

const themeModeLabelMap: Record<ThemeMode, string> = {
  light: '浅色',
  dark: '深色',
  system: '跟随系统',
}

const codeThemeLabelMap: Record<CodeTheme, string> = {
  light: '浅色代码',
  dark: '深色代码',
  system: '跟随主题',
}

const fontScaleLabelMap: Record<FontScale, string> = {
  sm: '紧凑',
  md: '标准',
  lg: '舒适',
}

const summaryText = computed(() => {
  const appliedThemeLabel = themeStore.resolvedTheme === 'dark' ? '深色' : '浅色'
  const appliedCodeThemeLabel = themeStore.resolvedCodeTheme === 'dark' ? '深色代码' : '浅色代码'
  const vt = draftPreferences.spacingVertical || '16px'
  const ht = draftPreferences.spacingHorizontal || '16px'
  
  return `当前支持即时预览，当前预览间距: 上下 ${vt}, 左右 ${ht}。点击保存后才会持久生效；离开设置未保存会恢复。当前实际生效主题为${appliedThemeLabel}，代码主题为${appliedCodeThemeLabel}。`
})

onMounted(() => {
  void userPreferencesStore.loadPreferences()
})

onBeforeUnmount(() => {
  themeStore.syncFromPreferences(userPreferencesStore.preferences)
})

function previewAppearance(patch: Partial<UserPreferences>) {
  Object.assign(draftPreferences, patch)
  // 即时预览：修改快照后，直接同步给 themeStore，但此时还未写入 userPreferencesStore
  themeStore.syncFromPreferences(draftPreferences)
}

function resetPreferences() {
  draftPreferences.themeMode = 'system'
  draftPreferences.codeTheme = 'system'
  draftPreferences.fontScale = 'md'
  draftPreferences.spacingVertical = '16px'
  draftPreferences.spacingHorizontal = '16px'
  themeStore.syncFromPreferences(draftPreferences)
}

function savePreferences() {
  void userPreferencesStore.updatePreferencesPatch(draftPreferences)
}
</script>

<style scoped>
.settings-appearance-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.settings-panel-scroll {
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  flex: 1;
  padding-bottom: 20px;
}

.settings-panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text-primary);
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-border-color-soft);
  margin-bottom: 16px;
}

.settings-actions {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--app-border-color-soft);
  margin-top: 10px;
  justify-content: flex-end;
}

.settings-btn {
  height: 36px;
  line-height: 36px;
  padding: 0 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.settings-btn-primary {
  background: var(--app-accent);
  color: var(--app-neutral-strong-contrast);
}

.settings-btn-secondary {
  background: var(--app-surface-muted);
  color: var(--app-text-primary);
  border: 1px solid var(--app-border-color);
}

.appearance-summary-card,
.appearance-section {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-vertical); /* previously 12px */
  padding: var(--app-space-vertical) var(--app-space-horizontal); /* previously 16px */
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.summary-copy,
.settings-status {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.settings-status-error {
  color: var(--app-danger);
}

.option-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.option-chip {
  margin: 0;
  min-width: 92px;
  height: 36px;
  line-height: 36px;
  padding: 0 14px;
  border-radius: 999px;
  border: none;
  background: var(--app-border-color);
  color: var(--app-text-primary);
  font-size: 12px;
  font-weight: 600;
}

.option-chip::after {
  border: none;
}

.option-chip-active {
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}
</style>

