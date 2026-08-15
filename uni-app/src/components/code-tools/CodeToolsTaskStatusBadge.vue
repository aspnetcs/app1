<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status?: string | null
  approvalStatus?: string | null
}>()

const normalized = computed(() => String(props.status || '').trim().toLowerCase())
const approval = computed(() => String(props.approvalStatus || '').trim().toLowerCase())

const label = computed(() => {
  if (normalized.value) return normalized.value
  if (approval.value) return `approval:${approval.value}`
  return 'unknown'
})

const colorClass = computed(() => {
  const status = normalized.value
  if (status === 'completed') return 'bg-green-50 text-green-700'
  if (status === 'failed') return 'bg-red-50 text-red-700'
  if (status === 'running') return 'bg-blue-50 text-blue-700'
  if (status === 'approved') return 'bg-emerald-50 text-emerald-700'
  if (status === 'rejected') return 'bg-rose-50 text-rose-700'
  if (status === 'pending') return 'bg-gray-50 text-gray-700'
  return 'bg-gray-50 text-gray-700'
})
</script>

<template>
  <view class="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium" :class="colorClass">
    {{ label }}
  </view>
</template>

