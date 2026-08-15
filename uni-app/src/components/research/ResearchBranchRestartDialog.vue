<template>
  <view v-if="visible" class="rrt-backdrop" @click="emit('close')" @tap="emit('close')"></view>
  <view v-if="visible" class="rrt-wrap">
      <view class="rrt-dialog" @click.stop @tap.stop>
        <text class="rrt-title">从此阶段重开</text>

        <view class="rrt-field">
          <text class="rrt-label">来源运行</text>
          <text class="rrt-value">Run #{{ sourceRunNumber }}</text>
        </view>

        <view class="rrt-field">
          <text class="rrt-label">重开阶段</text>
          <text class="rrt-value">{{ stageNumber }}. {{ stageLabel }}</text>
        </view>

        <view class="rrt-field">
          <text class="rrt-label">纠偏提示词</text>
          <textarea
            v-model="branchPrompt"
            placeholder="可选：指出上次运行需要修正的方向"
            class="rrt-textarea"
          />
        </view>

        <view class="rrt-actions">
          <view class="rrt-btn rrt-btn-cancel" @click="emit('close')" @tap="emit('close')">
            <text>取消</text>
          </view>
          <view class="rrt-btn rrt-btn-submit" @click="handleSubmit" @tap="handleSubmit">
            <text>确认重开</text>
          </view>
        </view>
      </view>
    </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  visible: boolean
  sourceRunId: string
  sourceRunNumber: number
  stageNumber: number
  stageLabel: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'restart', data: { sourceRunId: string; restartFromStage: number; branchPrompt: string }): void
}>()

const branchPrompt = ref('')

watch(() => props.visible, (v) => {
  if (v) branchPrompt.value = ''
})

function handleSubmit() {
  emit('restart', {
    sourceRunId: props.sourceRunId,
    restartFromStage: props.stageNumber,
    branchPrompt: branchPrompt.value.trim(),
  })
}
</script>

<style scoped>
.rrt-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: var(--z-modal-backdrop);
}
.rrt-wrap {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  pointer-events: none;
}
.rrt-dialog {
  pointer-events: auto;
  position: relative;
  z-index: calc(var(--z-modal) + 1);
  background: #fff;
  border-radius: 20px;
  padding: 24px;
  width: 400px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}
.rrt-title {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 16px;
}
.rrt-field { margin-bottom: 14px; }
.rrt-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 4px;
}
.rrt-value {
  display: block;
  font-size: 14px;
  color: #6b7280;
}
.rrt-textarea {
  width: 100%;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 14px;
  min-height: 80px;
  box-sizing: border-box;
}
.rrt-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
}
.rrt-btn {
  padding: 8px 20px;
  border-radius: 10px;
  font-size: 14px;
  cursor: pointer;
}
.rrt-btn-cancel {
  border: 1px solid #e5e7eb;
  background: #fff;
  color: #6b7280;
}
.rrt-btn-submit {
  background: #111827;
  color: #fff;
}
</style>
