<script setup lang="ts">
import { computed, ref } from 'vue'
import { createCompatActionRunner } from '@/utils/h5EventCompat'
import { getModelCapabilityTags, resolveModelSelectorState, type ModelSelectorOption } from './modelSelectorState'
// #ifndef MP-WEIXIN
import IconChevronDown from './icons/IconChevronDown.vue'
// #endif
// #ifdef MP-WEIXIN
import MpShapeIcon from './icons/MpShapeIcon.vue'
// #endif

const props = withDefaults(
  defineProps<{
    modelValue: string
    models?: ModelSelectorOption[]
  }>(),
  {
    models: () => [],
  },
)

const emit = defineEmits<{ (e: 'update:modelValue', val: string): void }>()

const showDropdown = ref(false)
const searchQuery = ref('')
const runCompatAction = createCompatActionRunner()

const selectorState = computed(() => resolveModelSelectorState(props.modelValue, props.models))
const currentModel = computed(() => selectorState.value.currentModel)
const triggerLabel = computed(() => selectorState.value.triggerLabel)

const filteredModels = computed(() => {
  if (!searchQuery.value) return props.models
  const keyword = searchQuery.value.toLowerCase()
  return props.models.filter((model) =>
    model.name.toLowerCase().includes(keyword)
    || model.id.toLowerCase().includes(keyword)
    || (model.provider || '').toLowerCase().includes(keyword),
  )
})

function closeDropdown() {
  showDropdown.value = false
  searchQuery.value = ''
}

function handleTriggerActivate(event?: Event) {
  runCompatAction('model-selector-trigger', event, () => {
    showDropdown.value = !showDropdown.value
    if (!showDropdown.value) {
      searchQuery.value = ''
    }
  })
}

function handleModelSelect(id: string, event?: Event) {
  runCompatAction(`model-selector-item:${id}`, event, () => {
    emit('update:modelValue', id)
    closeDropdown()
  })
}

function handleOverlayClose(event?: Event) {
  runCompatAction('model-selector-overlay', event, () => {
    closeDropdown()
  })
}
</script>

<template>
  <view class="model-selector" @click.stop @tap.stop>
    <view class="model-trigger" @click.stop="handleTriggerActivate" @tap.stop="handleTriggerActivate">
      <text class="model-trigger-name">{{ triggerLabel }}</text>
      <!-- #ifdef MP-WEIXIN -->
      <MpShapeIcon name="chevron-down" :size="12" class="model-trigger-chevron" :class="{ 'rotate-180': showDropdown }" />
      <!-- #endif -->
      <!-- #ifndef MP-WEIXIN -->
      <IconChevronDown :size="12" class="model-trigger-chevron" :class="{ 'rotate-180': showDropdown }" />
      <!-- #endif -->
    </view>

    <view v-if="showDropdown" class="model-dropdown">
      <view class="dropdown-card">
        <view class="model-search-wrap">
          <view class="model-search-icon">
            <text style="font-size:14px;color:var(--app-text-secondary);">Q</text>
          </view>
          <input
            v-model="searchQuery"
            class="model-search-input"
            placeholder="搜索模型"
            @click.stop
            @tap.stop
          />
        </view>

        <scroll-view scroll-y class="model-list">
          <view class="model-list-inner">
            <view
              v-for="m in filteredModels"
              :key="m.id"
              class="model-item"
              :class="{ 'model-item-active': m.id === modelValue }"
              @click="handleModelSelect(m.id, $event)"
              @tap="handleModelSelect(m.id, $event)"
            >
              <view class="model-item-left">
                <view class="model-item-icon">
                  <image v-if="m.avatarPath" :src="m.avatarPath" class="model-item-icon-img" mode="aspectFit" />
                  <text v-else class="model-item-icon-text">{{ (m.provider === 'default' ? '\u9ed8\u8ba4' : (m.provider || 'AI')).charAt(0) }}</text>
                </view>
                <view class="model-item-info">
                  <text class="model-item-id">{{ m.id }}</text>
                  <view class="model-item-meta">
                    <text v-if="m.provider" class="model-item-provider">{{ m.provider === 'default' ? '\u9ed8\u8ba4' : m.provider }}</text>
                    <view class="model-capability-tags">
                      <text
                        v-for="tag in getModelCapabilityTags(m)"
                        :key="tag.key"
                        class="model-capability-tag"
                        :class="`model-capability-tag-${tag.tone}`"
                      >{{ tag.label }}</text>
                    </view>
                  </view>
                </view>
              </view>
              <view v-if="m.id === modelValue" class="model-item-check">
                <text style="color:var(--app-success-contrast);font-weight:600;">&#x2713;</text>
              </view>
            </view>
            <view v-if="filteredModels.length === 0" class="model-empty">
              <text v-if="models.length === 0">加载中...</text>
              <text v-else>未找到匹配模型</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <view v-if="showDropdown" class="model-overlay" @click="handleOverlayClose" @tap="handleOverlayClose"></view>
  </view>
</template>

<style scoped>
.model-selector {
  position: relative;
  z-index: 100;
  height: 34px;
  display: flex;
  align-items: center;
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}

.model-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  box-sizing: border-box;
  padding: 0 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 150ms;
  font-weight: 600;
  font-size: 20px;
  color: var(--app-text-primary);
  white-space: nowrap;
  line-height: 34px;
  flex: 0 0 auto;
}

.model-trigger:hover {
  background: var(--app-fill-hover);
}

.model-trigger-name {
  line-height: 34px;
}

.model-trigger-chevron {
  transition: transform 200ms;
}

.rotate-180 {
  transform: rotate(180deg);
}

.model-dropdown {
  position: fixed;
  top: 56px;
  left: 50%;
  transform: translateX(-50%);
  width: 520px;
  max-width: calc(100vw - 32px);
  z-index: 1001;
}

.dropdown-card {
  background: var(--app-surface-raised);
  border: 1px solid var(--app-border-color);
  border-radius: 16px;
  box-shadow: var(--app-shadow-elevated);
  overflow: hidden;
}

.model-search-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.model-search-icon {
  flex-shrink: 0;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.model-search-input {
  flex: 1;
  height: 28px;
  border: none;
  outline: none;
  font-size: 15px;
  background: transparent;
  color: var(--app-text-primary);
}

.model-list {
  max-height: 400px;
  overflow-x: hidden;
}

.model-list-inner {
  padding: 6px 10px 8px;
}

.model-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 120ms;
  gap: 12px;
}

.model-item:hover {
  background: var(--app-fill-hover);
}

.model-item-active {
  background: var(--app-accent-soft);
}

.model-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.model-item-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--app-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid var(--app-border-color);
}

.model-item-icon-text {
  font-size: 13px;
  font-weight: 700;
  color: var(--app-text-secondary);
}

.model-item-icon-img {
  width: 20px;
  height: 20px;
  border-radius: 4px;
}

.model-item-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 4px;
}

.model-item-id {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-family: 'SF Mono', 'Menlo', 'Monaco', 'Courier New', monospace;
}

.model-item-provider {
  font-size: 11px;
  color: var(--app-text-secondary);
  line-height: 18px;
  flex-shrink: 0;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.model-capability-tags {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  min-width: 0;
}

.model-capability-tag {
  height: 18px;
  line-height: 16px;
  box-sizing: border-box;
  padding: 0 6px;
  border-radius: 5px;
  border: 1px solid var(--app-border-color-soft);
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.model-capability-tag-text {
  color: #475569;
  background: #f8fafc;
}

.model-capability-tag-image {
  color: #0369a1;
  background: #e0f2fe;
  border-color: #bae6fd;
}

.model-capability-tag-document {
  color: #047857;
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.model-item-check {
  flex-shrink: 0;
}

.model-empty {
  padding: 20px 16px;
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.model-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
}
</style>
