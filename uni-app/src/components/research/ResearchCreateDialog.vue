<template>
  <block v-if="visible">
    <!-- 蒙层 -->
    <view class="rcd-overlay" @click="handleCloseActivate" @tap="handleCloseActivate" @touchmove.stop.prevent></view>

    <!-- 弹窗 -->
    <view class="rcd-dialog">
      <view class="rcd-header">
        <text class="rcd-title">{{ TEXT.title }}</text>
        <view class="rcd-close" @click="handleCloseActivate" @tap="handleCloseActivate">x</view>
      </view>

      <view class="rcd-body">
        <view class="rcd-body-inner">
          
          <!-- 项目名称 -->
          <view class="rcd-form-item">
            <text class="rcd-label">{{ TEXT.name }} <text class="rcd-required">*</text></text>
            <input
              class="rcd-input"
              v-model="name"
              type="text"
              :placeholder="TEXT.namePlaceholder"
              placeholder-class="rcd-placeholder"
            />
          </view>

          <!-- 研究课题 -->
          <view class="rcd-form-item">
            <text class="rcd-label">{{ TEXT.topic }} <text class="rcd-required">*</text></text>
            <textarea
              class="rcd-textarea"
              v-model="topic"
              :placeholder="TEXT.topicPlaceholder"
              placeholder-class="rcd-placeholder"
              :show-confirm-bar="false"
              :cursor-spacing="20"
            />
          </view>

          <!-- 模板格式 -->
          <view class="rcd-form-item">
            <text class="rcd-label">
              {{ TEXT.template }} 
              <text class="rcd-optional">可选</text>
            </text>
            <textarea
              class="rcd-textarea rcd-textarea-sm"
              v-model="templateText"
              :placeholder="TEXT.templatePlaceholder"
              placeholder-class="rcd-placeholder"
              :show-confirm-bar="false"
              :cursor-spacing="20"
            />
          </view>

          <!-- 科研模式 -->
          <view class="rcd-form-item">
            <text class="rcd-label">{{ TEXT.mode }}</text>
            <view class="rcd-cards">
              <view 
                class="rcd-card" 
                :class="{ 'is-active': mode === 'single' }"
                @click="mode = 'single'"
                @tap="mode = 'single'"
              >
                <text class="rcd-card-title">{{ TEXT.modeSingle }}</text>
                <text class="rcd-card-desc">{{ TEXT.modeSingleDesc }}</text>
                <view v-if="mode === 'single'" class="rcd-badge">
                  <text class="rcd-badge-icon">选</text>
                </view>
              </view>
              <view 
                class="rcd-card" 
                :class="{ 'is-active-team': mode === 'team' }"
                @click="mode = 'team'"
                @tap="mode = 'team'"
              >
                <text class="rcd-card-title">{{ TEXT.modeTeam }}</text>
                <text class="rcd-card-desc">{{ TEXT.modeTeamDesc }}</text>
                <view v-if="mode === 'team'" class="rcd-badge rcd-badge-team">
                  <text class="rcd-badge-icon">选</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 执行策略 -->
          <view class="rcd-form-item">
            <text class="rcd-label">{{ TEXT.executionMode }}</text>
            <view class="rcd-cards">
              <view 
                class="rcd-btn-card" 
                :class="{ 'is-active-btn': executionMode === 'auto' }"
                @click="executionMode = 'auto'"
                @tap="executionMode = 'auto'"
              >
                <text>{{ TEXT.execAuto }}</text>
              </view>
              <view 
                class="rcd-btn-card" 
                :class="{ 'is-active-btn': executionMode === 'manual' }"
                @click="executionMode = 'manual'"
                @tap="executionMode = 'manual'"
              >
                <text>{{ TEXT.execManual }}</text>
              </view>
            </view>
          </view>

        </view>
      </view>

      <view class="rcd-footer">
        <button class="rcd-btn rcd-btn-cancel" @click="handleCloseActivate" @tap="handleCloseActivate">{{ TEXT.cancel }}</button>
        <button class="rcd-btn rcd-btn-submit" @click="handleSubmitActivate" @tap="handleSubmitActivate">{{ TEXT.create }}</button>
      </view>
    </view>
  </block>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

const TEXT = {
  title: '新建研究项目',
  name: '项目名称',
  namePlaceholder: '例如：LLM 知识蒸馏方法对比研究',
  topic: '研究课题',
  topicPlaceholder: '详细描述你的研究方向和目标...',
  mode: '科研模式',
  modeSingle: '单核处理',
  modeSingleDesc: '单模型逐步推进，速度快',
  modeTeam: '专家辩论',
  modeTeamDesc: '多模型交叉验证，更精准',
  template: '模板格式',
  templatePlaceholder: '可选：粘贴研究模板文本',
  executionMode: '执行策略',
  execAuto: '全自动执行',
  execManual: '关键点暂停',
  cancel: '取消',
  create: '创建项目',
  nameRequired: '请输入项目名称和研究课题',
}

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'create', data: {
    name: string
    topic: string
    mode: string
    templateText: string
    executionMode: string
    pauseStages: string[]
  }): void
}>()

const name = ref('')
const topic = ref('')
const mode = ref<'single' | 'team'>('single')
const templateText = ref('')
const executionMode = ref<'auto' | 'manual'>('auto')
const runCompatAction = createCompatActionRunner()

watch(() => props.visible, (v) => {
  if (v) {
    name.value = ''
    topic.value = ''
    mode.value = 'single'
    templateText.value = ''
    executionMode.value = 'auto'
  }
})

function handleSubmit() {
  if (!name.value.trim() || !topic.value.trim()) {
    uni.showToast({ title: TEXT.nameRequired, icon: 'none' })
    return
  }
  emit('create', {
    name: name.value.trim(),
    topic: topic.value.trim(),
    mode: mode.value,
    templateText: templateText.value.trim(),
    executionMode: executionMode.value,
    pauseStages: [],
  })
}

function handleCloseActivate(event?: CompatEventLike) {
  runCompatAction('research-create-dialog-close', event, () => {
    emit('close')
  })
}

function handleSubmitActivate(event?: CompatEventLike) {
  runCompatAction('research-create-dialog-submit', event, () => {
    handleSubmit()
  })
}
</script>

<style scoped>
.rcd-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.4);
  z-index: 99998;
}

.rcd-dialog {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: calc(100vw - 48px);
  max-width: 680px;
  max-height: calc(100vh - 48px);
  background-color: #ffffff;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  z-index: 99999;
  animation: rcd-fade-in 0.2s ease-out;
  overflow: hidden;
  min-height: 0;
}

@keyframes rcd-fade-in {
  from {
    opacity: 0;
    transform: translate(-50%, -48%);
  }
  to {
    opacity: 1;
    transform: translate(-50%, -50%);
  }
}

.rcd-header {
  padding: 18px 24px;
  border-bottom: 1px solid #f3f4f6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #ffffff;
  flex-shrink: 0;
}

.rcd-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.rcd-close {
  font-size: 26px;
  color: #9ca3af;
  line-height: 1;
  cursor: pointer;
  padding: 0 4px;
}

.rcd-close:hover {
  color: #4b5563;
}

.rcd-body {
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.rcd-body-inner {
  padding: 24px 24px 28px;
}

.rcd-form-item {
  margin-bottom: 22px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rcd-label {
  font-size: 15px;
  font-weight: 600;
  color: #374151;
  display: flex;
  align-items: center;
}

.rcd-required {
  color: #ef4444;
  margin-left: 4px;
}

.rcd-optional {
  font-size: 12px;
  font-weight: 400;
  color: #9ca3af;
  background-color: #f3f4f6;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 8px;
}

.rcd-input,
.rcd-textarea {
  width: 100%;
  background-color: #f9fafb !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 15px;
  color: #111827;
  box-sizing: border-box;
  transition: all 0.2s;
}

.rcd-input {
  height: 48px;
  line-height: 24px;
}

.rcd-input:focus,
.rcd-textarea:focus {
  background-color: #ffffff !important;
  border-color: #3b82f6 !important;
}

.rcd-textarea {
  height: 100px;
}

.rcd-textarea-sm {
  height: 76px;
}

.rcd-placeholder {
  color: #9ca3af;
}

.rcd-cards {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.rcd-card {
  flex: 1;
  min-width: 0;
  padding: 12px;
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  background-color: #ffffff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
  transition: all 0.2s;
}

.rcd-card.is-active {
  border-color: #111827;
  background-color: #f9fafb;
}

.rcd-card.is-active-team {
  border-color: #2563eb;
  background-color: #eff6ff;
}

.rcd-card-title {
  font-size: 14px;
  font-weight: 700;
  color: #4b5563;
  margin-bottom: 4px;
}

.rcd-card.is-active .rcd-card-title {
  color: #111827;
}

.rcd-card.is-active-team .rcd-card-title {
  color: #1d4ed8;
}

.rcd-card-desc {
  font-size: 12px;
  color: #9ca3af;
}

.rcd-btn-card {
  flex: 1;
  min-width: 0;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background-color: #ffffff;
  color: #4b5563;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  box-sizing: border-box;
  transition: all 0.2s;
}

.rcd-btn-card.is-active-btn {
  background-color: #111827;
  border-color: #111827;
  color: #ffffff;
}

.rcd-footer {
  padding: 16px 24px;
  border-top: 1px solid #f3f4f6;
  background-color: #f9fafb;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  flex-shrink: 0;
}

.rcd-btn {
  margin: 0;
  padding: 0 24px;
  height: 40px;
  line-height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  box-sizing: border-box;
  cursor: pointer;
  min-width: 132px;
}

.rcd-btn::after {
  display: none;
}

.rcd-btn-cancel {
  background-color: #ffffff;
  border: 1px solid #d1d5db;
  color: #4b5563;
}

.rcd-btn-submit {
  background-color: #111827;
  border: 1px solid #111827;
  color: #ffffff;
}

.rcd-btn-cancel:active {
  background-color: #f3f4f6;
}

.rcd-btn-submit:active {
  background-color: #1f2937;
}

.rcd-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 0;
  height: 0;
  border-top: 28px solid #111827;
  border-left: 28px solid transparent;
}
.rcd-badge-team {
  border-top-color: #2563eb;
}
.rcd-badge-icon {
  position: absolute;
  top: -26px;
  right: 2px;
  color: #ffffff;
  font-size: 12px;
  font-weight: bold;
}

/* 响应式适配移动端 */
@media screen and (max-width: 600px) {
  .rcd-dialog {
    width: calc(100vw - 24px);
    max-width: calc(100vw - 24px);
    max-height: calc(100vh - 24px);
    border-radius: 16px;
  }
  .rcd-header {
    padding: 14px 20px;
  }
  .rcd-title {
    font-size: 16px;
  }
  .rcd-body-inner {
    padding: 16px 20px;
  }
  .rcd-form-item {
    margin-bottom: 18px;
  }
  .rcd-label {
    font-size: 14px;
  }
  .rcd-input,
  .rcd-textarea {
    padding: 10px 14px;
    font-size: 14px;
  }
  .rcd-input {
    height: 44px;
  }
  .rcd-textarea {
    height: 80px;
  }
  .rcd-textarea-sm {
    height: 60px;
  }
  .rcd-cards {
    flex-direction: column;
    gap: 10px;
  }
  .rcd-card {
    padding: 12px;
  }
  .rcd-btn-card {
    padding: 8px;
    font-size: 13px;
  }
  .rcd-footer {
    padding: 12px 20px;
    flex-direction: column-reverse;
    align-items: stretch;
  }
  .rcd-btn {
    width: 100%;
    min-width: 0;
    padding: 0 20px;
    height: 38px;
    line-height: 38px;
    font-size: 13px;
  }
}
</style>
