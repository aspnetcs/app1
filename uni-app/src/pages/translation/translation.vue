<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { translateMessageApi } from '@/api/translation'
import { config } from '@/config'
import AppLayout from '@/layouts/AppLayout.vue'
import {
  TRANSLATION_LANGUAGE_OPTIONS,
  TRANSLATION_MAX_INPUT_CHARS,
  readTranslationPreferences,
  resolveTranslationLanguageLabel,
  writeTranslationPreferences,
} from '@/config/translation'

type PickerEvent = {
  detail?: {
    value?: number | string
  }
}

const SOURCE_LANGUAGE_OPTIONS = [
  { value: 'auto', label: '自动检测' },
  ...TRANSLATION_LANGUAGE_OPTIONS,
]

const preferences = readTranslationPreferences()

const inputText = ref('')
const translatedText = ref('')
const sourceLanguage = ref('auto')
const targetLanguage = ref(preferences.targetLanguage)
const loading = ref(false)
const errorText = ref('')

const sourceLanguageLabels = SOURCE_LANGUAGE_OPTIONS.map((option) => option.label)
const targetLanguageLabels = TRANSLATION_LANGUAGE_OPTIONS.map((option) => option.label)

const sourceLanguageIndex = computed(() => {
  const index = SOURCE_LANGUAGE_OPTIONS.findIndex((option) => option.value === sourceLanguage.value)
  return index >= 0 ? index : 0
})

const targetLanguageIndex = computed(() => {
  const index = TRANSLATION_LANGUAGE_OPTIONS.findIndex((option) => option.value === targetLanguage.value)
  return index >= 0 ? index : 0
})

const sourceLanguageLabel = computed(() => {
  const current = SOURCE_LANGUAGE_OPTIONS.find((option) => option.value === sourceLanguage.value)
  return current?.label || '自动检测'
})

const targetLanguageLabel = computed(() => resolveTranslationLanguageLabel(targetLanguage.value))
const inputLength = computed(() => inputText.value.length)
const canTranslate = computed(
  () => config.features.translation && !loading.value && inputText.value.trim().length > 0,
)

onLoad((options) => {
  const raw = typeof options?.text === 'string' ? options.text : ''
  if (!raw) return
  try {
    inputText.value = decodeURIComponent(raw).slice(0, TRANSLATION_MAX_INPUT_CHARS)
  } catch {
    inputText.value = raw.slice(0, TRANSLATION_MAX_INPUT_CHARS)
  }
})

function handleSourceLanguageChange(event: PickerEvent) {
  const rawIndex = Number(event.detail?.value ?? 0)
  const option = SOURCE_LANGUAGE_OPTIONS[rawIndex] ?? SOURCE_LANGUAGE_OPTIONS[0]
  if (!option) return
  sourceLanguage.value = option.value
}

function updateTargetLanguage(next: string) {
  targetLanguage.value = next
  writeTranslationPreferences({ targetLanguage: next })
}

function handleTargetLanguageChange(event: PickerEvent) {
  const rawIndex = Number(event.detail?.value ?? 0)
  const option = TRANSLATION_LANGUAGE_OPTIONS[rawIndex] ?? TRANSLATION_LANGUAGE_OPTIONS[0]
  if (!option) return
  updateTargetLanguage(option.value)
}

function handleSwapLanguages() {
  if (sourceLanguage.value === 'auto') return
  const currentSource = sourceLanguage.value
  const currentTarget = targetLanguage.value
  sourceLanguage.value = currentTarget
  updateTargetLanguage(currentSource)
  translatedText.value = ''
  errorText.value = ''
}

function clearDraft() {
  inputText.value = ''
  translatedText.value = ''
  errorText.value = ''
}

function copyTranslatedResult() {
  const content = translatedText.value.trim()
  if (!content) {
    uni.showToast({ title: '暂无可复制内容', icon: 'none' })
    return
  }
  uni.setClipboardData({
    data: content,
    success: () => uni.showToast({ title: '已复制', icon: 'none', duration: 1500 }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none', duration: 1500 }),
  })
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message.trim()) return error.message
  if (error && typeof error === 'object') {
    const record = error as Record<string, unknown>
    if (typeof record.message === 'string' && record.message.trim()) return record.message
  }
  return '翻译失败，请稍后重试'
}

async function handleTranslate() {
  const content = inputText.value.trim()
  if (!config.features.translation) {
    uni.showToast({ title: '翻译功能未开启', icon: 'none' })
    return
  }
  if (!content) {
    uni.showToast({ title: '请输入需要翻译的内容', icon: 'none' })
    return
  }
  if (content.length > TRANSLATION_MAX_INPUT_CHARS) {
    uni.showToast({
      title: `内容不能超过${TRANSLATION_MAX_INPUT_CHARS}字`,
      icon: 'none',
    })
    return
  }

  loading.value = true
  errorText.value = ''

  try {
    const response = await translateMessageApi({
      content,
      targetLanguage: targetLanguage.value,
    })
    translatedText.value = response.data?.translatedText || ''
    updateTargetLanguage(response.data?.targetLanguage || targetLanguage.value)
  } catch (error) {
    errorText.value = toErrorMessage(error)
    uni.showToast({ title: errorText.value, icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function handleRetranslate() {
  if (!inputText.value.trim()) {
    uni.showToast({ title: '请输入需要翻译的内容', icon: 'none' })
    return
  }
  translatedText.value = ''
  await handleTranslate()
}
</script>

<template>
  <AppLayout>
    <view class="translation-page">
      <view v-if="!config.features.translation" class="translation-disabled-card">
        <text class="translation-disabled-title">翻译功能未开启</text>
        <text class="translation-disabled-text">请先在管理端启用翻译功能后再使用。</text>
      </view>

      <view v-else class="translation-shell">
        <view class="translation-toolbar">
          <picker
            mode="selector"
            :range="sourceLanguageLabels"
            :value="sourceLanguageIndex"
            @change="handleSourceLanguageChange"
          >
            <view class="translation-select">
              <text class="translation-select-text">{{ sourceLanguageLabel }}</text>
              <text class="translation-select-arrow">v</text>
            </view>
          </picker>

          <button
            class="translation-swap-button"
            :disabled="sourceLanguage === 'auto'"
            @click="handleSwapLanguages"
            @tap="handleSwapLanguages"
          >
            <text class="translation-swap-icon">切换</text>
          </button>

          <picker
            mode="selector"
            :range="targetLanguageLabels"
            :value="targetLanguageIndex"
            @change="handleTargetLanguageChange"
          >
            <view class="translation-select">
              <text class="translation-select-text">{{ targetLanguageLabel }}</text>
              <text class="translation-select-arrow">v</text>
            </view>
          </picker>
        </view>

        <view class="translation-grid">
          <view class="translation-panel translation-panel-input">
            <view class="translation-panel-head">
              <text class="translation-panel-title">输入文本</text>
              <text class="translation-panel-meta">{{ inputLength }}/{{ TRANSLATION_MAX_INPUT_CHARS }}</text>
            </view>

            <textarea
              v-model="inputText"
              class="translation-textarea"
              :maxlength="TRANSLATION_MAX_INPUT_CHARS"
              auto-height
              placeholder="输入需要翻译的文本"
            />

            <view class="translation-panel-actions">
              <button
                class="translation-secondary-button translation-clear-button"
                :disabled="loading"
                @click="clearDraft"
                @tap="clearDraft"
              >
                清空
              </button>
              <button
                class="translation-primary-button"
                :disabled="!canTranslate"
                @click="handleTranslate"
                @tap="handleTranslate"
              >
                {{ loading ? '翻译中...' : '开始翻译' }}
              </button>
            </view>
          </view>

          <view class="translation-panel translation-panel-output">
            <view class="translation-panel-head">
              <text class="translation-panel-title">翻译</text>
              <text class="translation-panel-meta">{{ targetLanguageLabel }}</text>
            </view>

            <view class="translation-output">
              <text v-if="loading" class="translation-placeholder">正在翻译，请稍候...</text>
              <text v-else-if="translatedText.trim()" class="translation-result" selectable>{{ translatedText }}</text>
              <text v-else class="translation-placeholder">翻译结果会显示在这里</text>
            </view>

            <view class="translation-panel-actions translation-panel-actions-output">
              <button
                class="translation-secondary-button translation-retry-button"
                :disabled="!canTranslate"
                @click="handleRetranslate"
                @tap="handleRetranslate"
              >
                重新翻译
              </button>
              <button
                class="translation-secondary-button translation-copy-button"
                :disabled="!translatedText.trim()"
                @click="copyTranslatedResult"
                @tap="copyTranslatedResult"
              >
                复制结果
              </button>
            </view>

            <text v-if="errorText" class="translation-error">{{ errorText }}</text>
          </view>
        </view>
      </view>
    </view>
  </AppLayout>
</template>

<style scoped>
.translation-page {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  padding: 18px 16px 28px;
  overflow: auto;
}

.translation-disabled-card {
  max-width: 960px;
  margin: 0 auto;
  border-radius: 20px;
  background: var(--app-surface);
  border: 1px solid var(--app-border-color);
  padding: 24px;
  box-shadow: var(--app-shadow-elevated);
}

.translation-disabled-title {
  display: block;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.3;
  color: var(--app-text-primary);
}

.translation-disabled-text {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.translation-shell {
  max-width: 1040px;
  margin: 0 auto;
  border-radius: 22px;
  background: var(--app-surface);
  border: 1px solid var(--app-border-color);
  box-shadow: var(--app-shadow-elevated);
  padding: 14px;
  box-sizing: border-box;
}

.translation-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 64px minmax(0, 1fr);
  gap: 14px;
  align-items: center;
  margin-bottom: 14px;
}

.translation-select {
  height: 48px;
  border-radius: 14px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-sizing: border-box;
}

.translation-select-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.translation-select-arrow {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.translation-swap-button {
  width: 52px;
  height: 52px;
  border-radius: 999px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  color: var(--app-text-secondary);
}

.translation-swap-button::after {
  border: none;
}

.translation-swap-icon {
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}

.translation-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.translation-panel {
  min-height: 620px;
  border-radius: 18px;
  padding: 18px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--app-border-color);
}

.translation-panel-input {
  background: var(--app-surface);
}

.translation-panel-output {
  background: var(--app-surface-muted);
}

.translation-panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.translation-panel-title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  letter-spacing: 0;
  color: var(--app-text-primary);
}

.translation-panel-meta {
  padding-top: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.translation-textarea {
  flex: 1;
  width: 100%;
  min-height: 420px;
  background: transparent;
  border: none;
  padding: 0;
  font-size: 15px;
  line-height: 1.75;
  color: var(--app-text-primary);
  box-sizing: border-box;
}

.translation-output {
  flex: 1;
  min-height: 420px;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
}

.translation-result,
.translation-placeholder {
  font-size: 15px;
  line-height: 1.75;
}

.translation-result {
  color: var(--app-text-primary);
}

.translation-placeholder {
  color: var(--app-text-secondary);
}

.translation-panel-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 16px;
}

.translation-panel-actions-output {
  justify-content: flex-end;
}

.translation-primary-button,
.translation-secondary-button {
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--app-border-color);
  padding: 0 18px;
  font-size: 14px;
  font-weight: 600;
  transition: background-color 150ms ease, border-color 150ms ease, color 150ms ease, box-shadow 150ms ease;
}

.translation-primary-button::after,
.translation-secondary-button::after {
  border: none;
}

.translation-primary-button {
  background: var(--app-accent);
  border-color: var(--app-accent);
  color: var(--app-neutral-strong-contrast);
  min-width: 152px;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.24);
}

.translation-secondary-button {
  background: var(--app-surface);
  color: var(--app-text-primary);
  min-width: 116px;
}

.translation-clear-button {
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
}

.translation-retry-button {
  background: var(--app-accent-soft);
  border-color: var(--app-accent-soft);
  color: var(--app-accent-contrast);
}

.translation-copy-button {
  background: var(--app-surface);
  border-color: var(--app-border-color);
}

.translation-primary-button[disabled] {
  background: var(--app-border-color-soft);
  border-color: var(--app-border-color);
  color: var(--app-text-secondary);
  box-shadow: none;
}

.translation-secondary-button[disabled] {
  color: var(--app-text-secondary);
  border-color: var(--app-border-color);
  background: var(--app-surface-muted);
}

.translation-error {
  display: block;
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--app-danger);
}

@media (max-width: 960px) {
  .translation-toolbar,
  .translation-grid {
    grid-template-columns: 1fr;
  }

  .translation-swap-button {
    margin: 0 auto;
  }

  .translation-panel {
    min-height: 480px;
  }
}

@media (max-width: 640px) {
  .translation-page {
    padding: 14px 10px 24px;
  }

  .translation-shell {
    border-radius: 22px;
    padding: 12px;
  }

  .translation-toolbar {
    gap: 10px;
  }

  .translation-panel {
    min-height: 420px;
    padding: 16px 14px;
  }

  .translation-panel-title {
    font-size: 22px;
  }

  .translation-textarea,
  .translation-result,
  .translation-placeholder {
    font-size: 15px;
  }

  .translation-panel-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .translation-primary-button,
  .translation-secondary-button {
    width: 100%;
  }
}
</style>
