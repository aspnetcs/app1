<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import { useAgentRunsStore } from '@/stores/agentRuns'
import AgentRunStatusBadge from '@/components/agent-runs/AgentRunStatusBadge.vue'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'

const store = useAgentRunsStore()
const runCompatAction = createCompatActionRunner()

const form = reactive({
  agentId: '',
  requestedChannelIdText: '',
})

const createError = ref('')
const createRunning = ref(false)

const page = ref(0)
const size = 20

const list = computed(() => store.list?.items || [])
const total = computed(() => store.list?.total ?? 0)

function parseOptionalLong(text: string): number | null {
  const trimmed = (text || '').trim()
  if (!trimmed) return null
  const n = Number.parseInt(trimmed, 10)
  return Number.isFinite(n) ? n : null
}

async function reload() {
  await store.fetchList({ page: page.value, size })
}

function goPrevPage() {
  page.value = Math.max(0, page.value - 1)
  void reload()
}

function goNextPage() {
  page.value = page.value + 1
  void reload()
}

async function handleCreate() {
  createRunning.value = true
  createError.value = ''
  try {
    const agentId = form.agentId.trim()
    if (!agentId) throw new Error('请输入 agentId')
    const requestedChannelId = parseOptionalLong(form.requestedChannelIdText)
    await store.create({ agentId, requestedChannelId })
    form.agentId = ''
    form.requestedChannelIdText = ''
    uni.showToast({ title: '已创建任务', icon: 'none' })
  } catch (err) {
    createError.value = err instanceof Error ? err.message : String(err)
  } finally {
    createRunning.value = false
  }
}

const handleCreateActivate = (event?: CompatEventLike) => {
  runCompatAction('agent-runs-create', event, () => {
    void handleCreate()
  })
}

const openDetail = (id: string, event?: CompatEventLike) => {
  runCompatAction(`agent-runs-open:${id}`, event, () => {
    uni.navigateTo({ url: `/pages/agent-runs/detail?id=${encodeURIComponent(id)}` })
  })
}

function toLocalTime(value?: string | null) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <AppLayout>
    <view class="p-4 max-w-3xl mx-auto">
      <view class="mb-4">
        <text class="text-xl font-bold text-gray-900 block">任务运行</text>
        <text class="text-sm text-gray-500 block mt-1">查看任务审批状态与执行记录，也可以手动创建测试任务。</text>
      </view>

      <view class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden mb-6">
        <view class="px-4 py-3 border-b border-gray-100 font-medium text-gray-900">
          创建任务
        </view>
        <view class="px-4 py-4">
          <view class="text-sm text-gray-600">
            该入口用于验证调度链路，生产场景建议从业务入口创建任务。
          </view>

          <view class="mt-4 space-y-3">
            <view>
              <text class="text-xs text-gray-500 block mb-1">agentId</text>
              <input
                v-model="form.agentId"
                class="w-full px-3 py-2 border border-gray-200 rounded-lg bg-white text-gray-900"
                placeholder="例如：a1b2c3..."
              />
            </view>
            <view>
              <text class="text-xs text-gray-500 block mb-1">requestedChannelId (可选)</text>
              <input
                v-model="form.requestedChannelIdText"
                type="number"
                class="w-full px-3 py-2 border border-gray-200 rounded-lg bg-white text-gray-900"
                placeholder="例如：1"
              />
            </view>
          </view>

          <view class="mt-4">
            <button
              @click="handleCreateActivate"
              @tap="handleCreateActivate"
              class="w-full bg-gray-900 text-white py-2.5 rounded-lg font-medium border-none"
              :disabled="createRunning"
            >
              {{ createRunning ? '创建中...' : '创建任务' }}
            </button>
          </view>

          <view v-if="createError" class="mt-3 text-sm text-red-600">
            {{ createError }}
          </view>
        </view>
      </view>

      <view class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
        <view class="px-4 py-3 border-b border-gray-100 font-medium text-gray-900 flex items-center justify-between">
          <text>我的任务</text>
          <view class="flex items-center gap-2">
            <button
              class="px-3 py-1.5 rounded-lg text-xs font-medium bg-gray-50 text-gray-700 border border-gray-200"
              @click="reload"
              @tap="reload"
              :disabled="store.loadingList"
            >
              {{ store.loadingList ? '刷新中...' : '刷新' }}
            </button>
          </view>
        </view>

        <view v-if="store.loadingList && list.length === 0" class="p-4 text-sm text-gray-500">
          正在加载...
        </view>

        <view v-else-if="list.length === 0" class="p-6 text-sm text-gray-500">
          暂无任务记录。
        </view>

        <view v-else class="p-4">
          <view class="text-xs text-gray-500 mb-3">共 {{ total }} 条</view>
          <view class="space-y-3">
            <view
              v-for="item in list"
              :key="item.id"
              class="p-3 rounded-xl border border-gray-100 bg-white"
              @click="openDetail(item.id, $event)"
              @tap="openDetail(item.id, $event)"
            >
              <view class="flex items-center justify-between gap-3">
                <view class="min-w-0">
                  <text class="text-sm font-semibold text-gray-900 block truncate">{{ item.id }}</text>
                  <text class="text-xs text-gray-500 block mt-1">agentId: {{ item.agentId || '-' }}</text>
                </view>
                <AgentRunStatusBadge :status="item.status" />
              </view>

              <view class="mt-2 text-xs text-gray-500">
                创建时间：{{ toLocalTime(item.createdAt) }}
              </view>
              <view v-if="item.approval?.status" class="mt-1 text-xs text-gray-500">
                审批：{{ item.approval.status }}{{ item.approval.note ? `，${item.approval.note}` : '' }}
              </view>
            </view>
          </view>

          <view class="mt-4 flex items-center justify-between">
            <button
              class="px-3 py-2 rounded-lg text-xs font-medium bg-gray-50 text-gray-700 border border-gray-200"
              :disabled="page <= 0 || store.loadingList"
              @click="goPrevPage"
              @tap="goPrevPage"
            >
              上一页
            </button>
            <text class="text-xs text-gray-500">第 {{ page + 1 }} 页</text>
            <button
              class="px-3 py-2 rounded-lg text-xs font-medium bg-gray-50 text-gray-700 border border-gray-200"
              :disabled="(page + 1) * size >= total || store.loadingList"
              @click="goNextPage"
              @tap="goNextPage"
            >
              下一页
            </button>
          </view>
        </view>
      </view>
    </view>
  </AppLayout>
</template>
