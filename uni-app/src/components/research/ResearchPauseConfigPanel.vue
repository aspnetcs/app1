<template>
  <view class="rpc-root">
    <view class="rpc-header">
      <text class="rpc-title">暂停阶段配置</text>
      <text class="rpc-hint">选中的阶段将在到达时暂停，等待人工确认后继续</text>
    </view>
    <view class="rpc-list">
      <view
        v-for="stage in gateStages"
        :key="stage.number"
        class="rpc-item"
        @click="toggle(stage.number)"
        @tap="toggle(stage.number)"
      >
        <view
          class="rpc-check"
          :class="{ 'rpc-checked': selected.has(stage.number) }"
        >
          <text v-if="selected.has(stage.number)" class="rpc-check-mark">&#10003;</text>
        </view>
        <text class="rpc-stage-label">{{ stage.number }}. {{ stage.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface StageDefinition {
  number: number
  key: string
  label: string
  phase: string
  isGate?: boolean
}

const props = defineProps<{
  stages: StageDefinition[]
  modelValue: number[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number[]): void
}>()

const selected = ref(new Set<number>(props.modelValue))

watch(() => props.modelValue, (v) => {
  selected.value = new Set(v)
})

const gateStages = computed(() => props.stages.filter(s => s.isGate))

function toggle(n: number) {
  const s = new Set(selected.value)
  if (s.has(n)) s.delete(n)
  else s.add(n)
  selected.value = s
  emit('update:modelValue', Array.from(s).sort((a, b) => a - b))
}
</script>

<style scoped>
.rpc-root {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  padding: 16px;
}
.rpc-header { margin-bottom: 12px; }
.rpc-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #111827;
  margin-bottom: 4px;
}
.rpc-hint {
  display: block;
  font-size: 12px;
  color: #9ca3af;
}
.rpc-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rpc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 150ms;
}
.rpc-item:hover { background: #f9fafb; }
.rpc-check {
  width: 20px;
  height: 20px;
  border: 2px solid #d1d5db;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 150ms;
}
.rpc-checked {
  background: #111827;
  border-color: #111827;
}
.rpc-check-mark {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
.rpc-stage-label {
  font-size: 13px;
  color: #374151;
}
</style>
