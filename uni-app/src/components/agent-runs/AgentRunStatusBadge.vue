<script setup lang="ts">
import type { AgentRunStatus } from '@/api/agentRuns'

const props = defineProps<{
  status: AgentRunStatus
}>()

const STATUS_LABELS: Record<string, string> = {
  pending: '待审批',
  approved: '已批准',
  rejected: '已拒绝',
  running: '运行中',
  completed: '已完成',
  failed: '失败',
}

const label = (STATUS_LABELS[String(props.status)] || String(props.status || '-')).trim()
</script>

<template>
  <view
    class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border"
    :class="
      status === 'running'
        ? 'bg-blue-50 text-blue-700 border-blue-100'
        : status === 'completed'
          ? 'bg-green-50 text-green-700 border-green-100'
          : status === 'failed'
            ? 'bg-red-50 text-red-700 border-red-100'
            : status === 'rejected'
              ? 'bg-amber-50 text-amber-800 border-amber-100'
              : 'bg-gray-50 text-gray-700 border-gray-200'
    "
  >
    <text>{{ label }}</text>
  </view>
</template>

