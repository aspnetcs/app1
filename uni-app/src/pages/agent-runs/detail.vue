<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AppLayout from '@/layouts/AppLayout.vue'
import AgentRunStatusBadge from '@/components/agent-runs/AgentRunStatusBadge.vue'
import { useAgentRunsStore } from '@/stores/agentRuns'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

const store = useAgentRunsStore()
const runCompatAction = createCompatActionRunner()

const runIdValue = ref('')
const runId = computed(() => runIdValue.value.trim())

const error = ref('')
const running = ref(false)
const failMessage = ref('')

const run = computed(() => store.current)

function toLocalTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}

async function reload() {
  error.value = ''
  if (!runId.value) {
    error.value = '缺少任务 id'
    return
  }
  try {
    await store.fetchRun(runId.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  }
}

async function handleStart() {
  if (!runId.value) return
  running.value = true
  error.value = ''
  try {
    await store.start(runId.value)
    uni.showToast({ title: '已开始运行', icon: 'none' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    running.value = false
  }
}

async function handleComplete() {
  if (!runId.value) return
  running.value = true
  error.value = ''
  try {
    await store.complete(runId.value)
    uni.showToast({ title: '已标记完成', icon: 'none' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    running.value = false
  }
}

async function handleFail() {
  if (!runId.value) return
  running.value = true
  error.value = ''
  try {
    await store.fail(runId.value, failMessage.value.trim() || undefined)
    uni.showToast({ title: '已标记失败', icon: 'none' })
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
  } finally {
    running.value = false
  }
}

const handleStartActivate = (event?: CompatEventLike) => {
  runCompatAction('agent-run-start', event, () => {
    void handleStart()
  })
}
const handleCompleteActivate = (event?: CompatEventLike) => {
  runCompatAction('agent-run-complete', event, () => {
    void handleComplete()
  })
}
const handleFailActivate = (event?: CompatEventLike) => {
  runCompatAction('agent-run-fail', event, () => {
    void handleFail()
  })
}

onLoad((options) => {
  runIdValue.value = typeof options?.id === 'string' ? options.id : ''
  void reload()
})
</script>

<template>
  <AppLayout>
    <view class="p-4 max-w-3xl mx-auto">
      <view class="mb-4 flex items-center justify-between gap-3">
        <view class="min-w-0">
          <text class="text-xl font-bold text-gray-900 block truncate">任务详情</text>
          <text class="text-xs text-gray-500 block mt-1">id: {{ runId || '-' }}</text>
        </view>
        <button
          class="px-3 py-2 rounded-lg text-xs font-medium bg-gray-50 text-gray-700 border border-gray-200"
          :disabled="store.loadingCurrent || running"
          @click="reload"
          @tap="reload"
        >
          {{ store.loadingCurrent ? '刷新中...' : '刷新' }}
        </button>
      </view>

      <view v-if="error" class="mb-4 text-sm text-red-600">{{ error }}</view>

      <view v-if="!run && store.loadingCurrent" class="p-4 text-sm text-gray-500">正在加载...</view>

      <view v-else-if="!run" class="p-4 text-sm text-gray-500">未找到任务。</view>

      <view v-else class="space-y-4">
        <view class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <view class="px-4 py-3 border-b border-gray-100 font-medium text-gray-900 flex items-center justify-between">
            <text>状态</text>
            <AgentRunStatusBadge :status="run.status" />
          </view>
          <view class="px-4 py-4 text-sm text-gray-700 space-y-2">
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">agentId</text>
              <text class="text-right">{{ run.agentId || '-' }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">requestedChannelId</text>
              <text class="text-right">{{ run.requestedChannelId ?? '-' }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">boundChannelId</text>
              <text class="text-right">{{ run.boundChannelId ?? '-' }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">createdAt</text>
              <text class="text-right">{{ toLocalTime(run.createdAt) }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">startedAt</text>
              <text class="text-right">{{ toLocalTime(run.startedAt) }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">completedAt</text>
              <text class="text-right">{{ toLocalTime(run.completedAt) }}</text>
            </view>
            <view v-if="run.errorMessage" class="pt-2 text-sm text-red-600">
              errorMessage: {{ run.errorMessage }}
            </view>
          </view>
        </view>

        <view class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <view class="px-4 py-3 border-b border-gray-100 font-medium text-gray-900">审批信息</view>
          <view class="px-4 py-4 text-sm text-gray-700 space-y-2">
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">status</text>
              <text class="text-right">{{ run.approval?.status || '-' }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">note</text>
              <text class="text-right">{{ run.approval?.note || '-' }}</text>
            </view>
            <view class="flex justify-between gap-3">
              <text class="text-gray-500">decidedAt</text>
              <text class="text-right">{{ toLocalTime(run.approval?.decidedAt) }}</text>
            </view>
          </view>
        </view>

        <view class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <view class="px-4 py-3 border-b border-gray-100 font-medium text-gray-900">操作</view>
          <view class="px-4 py-4 space-y-3">
            <view class="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <button
                class="bg-blue-50 text-blue-700 py-2 rounded-lg font-medium border-none"
                :disabled="running"
                @click="handleStartActivate"
                @tap="handleStartActivate"
              >
                开始
              </button>
              <button
                class="bg-green-50 text-green-700 py-2 rounded-lg font-medium border-none"
                :disabled="running"
                @click="handleCompleteActivate"
                @tap="handleCompleteActivate"
              >
                完成
              </button>
              <button
                class="bg-red-50 text-red-700 py-2 rounded-lg font-medium border-none"
                :disabled="running"
                @click="handleFailActivate"
                @tap="handleFailActivate"
              >
                失败
              </button>
            </view>

            <view>
              <text class="text-xs text-gray-500 block mb-1">失败原因（可选）</text>
              <input
                v-model="failMessage"
                class="w-full px-3 py-2 border border-gray-200 rounded-lg bg-white text-gray-900"
                placeholder="例如：网络错误"
              />
            </view>
          </view>
        </view>
      </view>
    </view>
  </AppLayout>
</template>
