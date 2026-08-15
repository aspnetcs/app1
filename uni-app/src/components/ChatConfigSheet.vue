<script setup lang="ts">
import { ref, computed } from 'vue'
import { createCompatActionRunner } from '@/utils/h5EventCompat'
import type { ChatMode } from '@/composables/useChatMode'
import type { LayoutModelOption } from '@/layouts/appLayoutModelOptions'
import { getModelCapabilityTags } from '@/components/modelSelectorState'
import ModelSelector from '@/components/ModelSelector.vue'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif
// #ifndef MP-WEIXIN
import IconPlus from '@/components/icons/IconPlus.vue'
// #endif

const props = defineProps<{
  visible: boolean
  chatMode: ChatMode
  modelValue: string
  multiModelIds: string[]
  captainMode: 'auto' | 'fixed_first'
  models: LayoutModelOption[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'update:chatMode', val: ChatMode): void
  (e: 'update:modelValue', val: string): void
  (e: 'update:multiModelIds', val: string[]): void
  (e: 'update:captainMode', val: 'auto' | 'fixed_first'): void
}>()

const runCompatAction = createCompatActionRunner()

const isMultiMode = computed(() => props.chatMode === 'compare' || props.chatMode === 'team')

function close() {
  emit('update:visible', false)
}

function handleClose(event?: Event) {
  runCompatAction('config-sheet-close', event, close)
}

function selectMode(mode: ChatMode) {
  emit('update:chatMode', mode)
}

function handleModeSelect(mode: ChatMode, event?: Event) {
  runCompatAction(`config-sheet-mode-${mode}`, event, () => selectMode(mode))
}

function updateMultiModel(index: number, id: string) {
  const nextList = [...props.multiModelIds]
  nextList[index] = id
  emit('update:multiModelIds', nextList)
}

function addModel() {
  const list = [...props.multiModelIds]
  const used = new Set(list)
  const next = props.models.find((model) => !used.has(model.id))
  if (!next?.id) return
  list.push(next.id)
  emit('update:multiModelIds', list)
}

function handleAddModel(event?: Event) {
  runCompatAction('config-sheet-add-model', event, addModel)
}

function removeModel(index: number) {
  const list = [...props.multiModelIds]
  if (list.length <= 2) return // min 2
  list.splice(index, 1)
  emit('update:multiModelIds', list)
}

function handleRemoveModel(index: number, event?: Event) {
  runCompatAction(`config-sheet-remove-model-${index}`, event, () => removeModel(index))
}

function toggleCaptain() {
  emit('update:captainMode', props.captainMode === 'auto' ? 'fixed_first' : 'auto')
}

function handleToggleCaptain(event?: Event) {
  runCompatAction('config-sheet-captain-toggle', event, toggleCaptain)
}
</script>

<template>
  <view v-if="visible" class="sheet-backdrop" @click="handleClose" @tap="handleClose"></view>
  <view class="sheet-container" :class="{ 'sheet-visible': visible }" @click.stop @tap.stop>
    <!-- Handle bar -->
    <view class="sheet-drag-handle-wrap" @click="handleClose" @tap="handleClose">
      <view class="sheet-drag-handle"></view>
    </view>
    
    <view class="sheet-header">
      <view class="sheet-tabs">
        <view 
          class="sheet-tab" 
          :class="{ 'sheet-tab-active': chatMode === 'single' }"
          @click="handleModeSelect('single', $event)"
          @tap="handleModeSelect('single', $event)"
        >单模型</view>
        <view 
          class="sheet-tab" 
          :class="{ 'sheet-tab-active': chatMode === 'compare' }"
          @click="handleModeSelect('compare', $event)"
          @tap="handleModeSelect('compare', $event)"
        >多模型</view>
        <view 
          class="sheet-tab" 
          :class="{ 'sheet-tab-active': chatMode === 'team' }"
          @click="handleModeSelect('team', $event)"
          @tap="handleModeSelect('team', $event)"
        >团队模式</view>
      </view>
    </view>

    <scroll-view class="sheet-content" scroll-y>
      <!-- Mode context hints -->
      <view class="sheet-mode-hint" v-if="chatMode === 'team'">
        队长向后方模型分配任务，合并最终结果。
      </view>
      <view class="sheet-mode-hint" v-if="chatMode === 'compare'">
        各模型独立输出，方便优劣对比。
      </view>

      <!-- Single Model Mode -->
      <view v-if="chatMode === 'single'" class="sheet-section">
        <text class="sheet-section-title">选择当前模型</text>
        <view class="sheet-model-grid">
          <view 
            v-for="m in models" :key="m.id"
            class="sheet-model-item"
            :class="{ 'sheet-model-item-active': modelValue === m.id }"
            @click="emit('update:modelValue', m.id); close()"
            @tap="emit('update:modelValue', m.id); close()"
          >
            <image v-if="m.avatarPath" :src="m.avatarPath" class="sheet-model-avatar" />
            <view v-else class="sheet-model-avatar-fallback">{{ m.name.charAt(0) }}</view>
            <view class="sheet-model-main">
              <text class="sheet-model-name">{{ m.name }}</text>
              <view class="sheet-model-tags">
                <text
                  v-for="tag in getModelCapabilityTags(m)"
                  :key="tag.key"
                  class="sheet-model-tag"
                  :class="`sheet-model-tag-${tag.tone}`"
                >{{ tag.label }}</text>
              </view>
            </view>
            <view v-if="modelValue === m.id" class="sheet-model-check">
              <!-- #ifdef MP-WEIXIN -->
              <MpShapeIcon name="check" :size="14" color="#2563eb" :stroke-width="2.5" />
              <!-- #endif -->
              <!-- #ifndef MP-WEIXIN -->
              <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
                <path d="M2 7.5L5.5 11L12 3" stroke="#2563eb" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
              <!-- #endif -->
            </view>
          </view>
        </view>
      </view>

      <!-- Multi/Team Mode -->
      <view v-if="isMultiMode" class="sheet-section">
        <view class="sheet-section-header">
          <text class="sheet-section-title">搭配模型，{{ chatMode === 'team' ? '协同出击' : '大乱斗' }}</text>
        </view>
        
        <view class="sheet-multi-list">
          <view v-for="(mid, idx) in multiModelIds" :key="idx" class="sheet-multi-row">
            <view class="sheet-multi-rank" v-if="chatMode === 'team' && idx === 0 && captainMode === 'fixed_first'">
              <text>队长</text>
            </view>
            <view class="sheet-multi-rank" v-else>
              <text>{{ idx + 1 }}</text>
            </view>
            <view class="sheet-multi-selector">
              <ModelSelector
                :model-value="mid"
                :models="models"
                @update:model-value="updateMultiModel(idx, $event)"
              />
            </view>
            <view class="sheet-multi-remove" v-if="multiModelIds.length > 2" @click="handleRemoveModel(idx, $event)" @tap="handleRemoveModel(idx, $event)">
               <!-- #ifdef MP-WEIXIN -->
               <MpShapeIcon name="close" :size="16" color="#ef4444" :stroke-width="2" />
               <!-- #endif -->
               <!-- #ifndef MP-WEIXIN -->
               <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                 <path d="M4 4L12 12M12 4L4 12" stroke="#ef4444" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
               </svg>
               <!-- #endif -->
            </view>
          </view>
          
          <view class="sheet-add-btn" @click="handleAddModel" @tap="handleAddModel">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="plus" :size="16" color="#4b5563" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <IconPlus :size="16" color="#4b5563" />
            <!-- #endif -->
            <text class="sheet-add-text">添加模型</text>
          </view>
        </view>

        <view class="sheet-captain-toggle" v-if="chatMode === 'team'">
           <text class="sheet-captain-label">自动选举队长</text>
           <view 
             class="toggle-switch" 
             :class="{ 'toggle-switch-on': captainMode === 'auto' }"
             @click="handleToggleCaptain"
             @tap="handleToggleCaptain"
           >
             <view class="toggle-knob"></view>
           </view>
        </view>
      </view>
      <!-- extra padding for Safe Area -->
      <view class="sheet-safe-area"></view>
    </scroll-view>
  </view>
</template>

<style scoped>
.sheet-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: var(--z-sheet-backdrop);
  animation: fadeIn 0.3s ease;
}

.sheet-container {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 65vh;
  max-height: calc(100vh - var(--app-safe-top, 0px) - 8px);
  background: #f8fafc;
  border-top-left-radius: 20px;
  border-top-right-radius: 20px;
  z-index: var(--z-sheet);
  transform: translateY(100%);
  transition: transform 0.35s cubic-bezier(0.32, 0.72, 0, 1);
  box-shadow: 0 -4px 24px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
}

.sheet-container.sheet-visible {
  transform: translateY(0);
}

.sheet-drag-handle-wrap {
  height: 24px;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-shrink: 0;
}

.sheet-drag-handle {
  width: 36px;
  height: 4px;
  background: #cbd5e1;
  border-radius: 4px;
}

.sheet-header {
  padding: 0 16px 12px;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.sheet-tabs {
  display: flex;
  background: #e2e8f0;
  border-radius: 12px;
  padding: 4px;
}

.sheet-tab {
  flex: 1;
  text-align: center;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
  padding: 8px 0;
  border-radius: 8px;
  transition: all 0.2s;
}

.sheet-tab-active {
  background: #ffffff;
  color: #0f172a;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}

.sheet-content {
  flex: 1;
  min-height: 0;
  padding: 16px;
  box-sizing: border-box;
}

.sheet-mode-hint {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: #f1f5f9;
  border-radius: 8px;
}

.sheet-section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
  display: block;
}

/* Grid for single */
.sheet-model-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sheet-model-item {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  gap: 12px;
}

.sheet-model-item-active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.sheet-model-avatar, .sheet-model-avatar-fallback {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex-shrink: 0;
}

.sheet-model-avatar-fallback {
  background: #e2e8f0;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #475569;
}

.sheet-model-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sheet-model-name {
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
  line-height: 20px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sheet-model-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  min-width: 0;
}

.sheet-model-tag {
  height: 20px;
  line-height: 18px;
  box-sizing: border-box;
  padding: 0 7px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.sheet-model-tag-text {
  color: #475569;
  background: #f8fafc;
}

.sheet-model-tag-image {
  color: #0369a1;
  background: #e0f2fe;
  border-color: #bae6fd;
}

.sheet-model-tag-document {
  color: #047857;
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.sheet-model-check {
  flex-shrink: 0;
}

/* List for multi */
.sheet-multi-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.sheet-multi-row {
  display: flex;
  align-items: center;
  background: #ffffff;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  gap: 10px;
}

.sheet-multi-rank {
  width: 28px;
  height: 28px;
  background: #f1f5f9;
  border-radius: 6px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  flex-shrink: 0;
}

.sheet-multi-selector {
  flex: 1;
  min-width: 0;
}

.sheet-multi-remove {
  width: 32px;
  height: 32px;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #fee2e2;
  border-radius: 8px;
}

.sheet-add-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  background: #f1f5f9;
  border-radius: 12px;
  border: 1.5px dashed #cbd5e1;
}

.sheet-add-text {
  font-size: 15px;
  font-weight: 500;
  color: #475569;
}

.sheet-captain-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.sheet-captain-label {
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
}

/* Toggle Switch (reused pattern) */
.toggle-switch {
  width: 44px;
  height: 24px;
  background: #cbd5e1;
  border-radius: 12px;
  padding: 2px;
  box-sizing: border-box;
  transition: background 0.3s;
  display: flex;
  align-items: center;
}

.toggle-switch-on {
  background: #10b981;
}

.toggle-knob {
  width: 20px;
  height: 20px;
  background: #ffffff;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  transform: translateX(0);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toggle-switch-on .toggle-knob {
  transform: translateX(20px);
}

.sheet-safe-area {
  height: var(--app-safe-bottom, env(safe-area-inset-bottom, 0px));
  min-height: 16px;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
</style>
