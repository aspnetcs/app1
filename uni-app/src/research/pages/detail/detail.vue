<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import AppLayout from '@/layouts/AppLayout.vue'
import ResearchBranchRestartDialog from '@/components/research/ResearchBranchRestartDialog.vue'
import ResearchPauseConfigPanel from '@/components/research/ResearchPauseConfigPanel.vue'
import {
  getResearchProject,
  startResearchRun,
  getResearchRunStatus,
  approveResearchGate,
  exportResearchProject,
  type ResearchProjectDetail,
  type ResearchRun,
  type ResearchStageLog,
} from '@/api/research'
import { getCurrentPageRouteOptions } from '@/utils/pageRoute'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { parseStageOutput, type ParsedStageOutput } from './stageOutput'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const props = defineProps<{ id?: string }>()
const projectId = ref('')
const loading = ref(true)
const project = ref<ResearchProjectDetail | null>(null)
const activeRun = ref<(ResearchRun & { stageLogs: ResearchStageLog[] }) | null>(null)
const polling = ref<ReturnType<typeof setInterval> | null>(null)
const runStatusError = ref('')
const runCompatAction = createCompatActionRunner()

const showBranchDialog = ref(false)
const branchStageNumber = ref(0)
const branchStageLabel = ref('')
const showPauseConfig = ref(false)
const pauseStages = ref<number[]>([])
const exportMode = ref<'result' | 'full'>('result')
const fallbackStageOutputs = ref<Map<number, ParsedStageOutput>>(new Map())

// 23-stage pipeline 定义
const stageDefinitions = [
  { number: 1, key: 'topic_init', label: '课题分析', phase: 'discovery' },
  { number: 2, key: 'problem_decompose', label: '问题拆解', phase: 'discovery' },
  { number: 3, key: 'search_strategy', label: '检索策略', phase: 'discovery' },
  { number: 4, key: 'literature_collect', label: '文献收集', phase: 'discovery' },
  { number: 5, key: 'literature_screen', label: '文献筛选', phase: 'discovery', isGate: true },
  { number: 6, key: 'knowledge_extract', label: '知识抽取', phase: 'discovery' },
  { number: 7, key: 'synthesis', label: '综合分析', phase: 'discovery' },
  { number: 8, key: 'hypothesis_gen', label: '假设生成', phase: 'experimentation' },
  { number: 9, key: 'experiment_design', label: '实验设计', phase: 'experimentation', isGate: true },
  { number: 10, key: 'code_generation', label: '代码生成', phase: 'experimentation' },
  { number: 11, key: 'resource_planning', label: '资源规划', phase: 'experimentation' },
  { number: 12, key: 'experiment_run', label: '运行实验', phase: 'experimentation' },
  { number: 13, key: 'iterative_refine', label: '迭代优化', phase: 'experimentation' },
  { number: 14, key: 'result_analysis', label: '结果分析', phase: 'analysis' },
  { number: 15, key: 'research_decision', label: '研究决策', phase: 'analysis', isGate: true },
  { number: 16, key: 'paper_outline', label: '论文大纲', phase: 'publication' },
  { number: 17, key: 'paper_draft', label: '论文初稿', phase: 'publication' },
  { number: 18, key: 'peer_review', label: '同行评审', phase: 'publication' },
  { number: 19, key: 'paper_revision', label: '论文修订', phase: 'publication' },
  { number: 20, key: 'quality_gate', label: '质量门禁', phase: 'publication', isGate: true },
  { number: 21, key: 'knowledge_archive', label: '知识归档', phase: 'publication' },
  { number: 22, key: 'export_publish', label: '发布导出', phase: 'publication' },
  { number: 23, key: 'citation_verify', label: '引用验证', phase: 'verification' },
]

const phaseLabels: Record<string, string> = {
  discovery: '探索发现',
  experimentation: '实验验证',
  analysis: '结果分析',
  publication: '论文发布',
  verification: '最终验证',
}

const phaseColors: Record<string, string> = {
  discovery: 'var(--app-text-secondary)',
  experimentation: 'var(--app-text-primary)',
  analysis: 'var(--app-warning-contrast)',
  publication: 'var(--app-success-contrast)',
  verification: 'var(--app-text-secondary)',
}

const currentStage = computed(() => activeRun.value?.currentStage ?? 0)
const runStatus = computed(() => activeRun.value?.status ?? project.value?.runStatus ?? 'idle')

const isGateWaiting = computed(() => runStatus.value === 'waiting_approval')
const isRunning = computed(() => runStatus.value === 'running')
const isCompleted = computed(() => runStatus.value === 'completed')
const isFailed = computed(() => runStatus.value === 'failed')

const statusLabel = computed(() => {
  const m: Record<string, string> = {
    idle: '等待开始',
    running: '运行中',
    waiting_approval: '等待审批',
    completed: '已完成',
    failed: '已失败',
    cancelled: '已取消',
  }
  return m[runStatus.value] || runStatus.value
})

const statusColor = computed(() => {
  const m: Record<string, string> = {
    idle: 'var(--app-text-secondary)',
    running: 'var(--app-text-primary)',
    waiting_approval: 'var(--app-warning-contrast)',
    completed: 'var(--app-success-contrast)',
    failed: 'var(--app-danger)',
    cancelled: 'var(--app-text-secondary)',
  }
  return m[runStatus.value] || 'var(--app-text-secondary)'
})

const stageLogMap = computed(() => {
  const map = new Map<number, ResearchStageLog>()
  if (activeRun.value?.stageLogs) {
    for (const log of activeRun.value.stageLogs) {
      map.set(log.stageNumber, log)
    }
  }
  return map
})

const getStageStatus = (stageNumber: number) => {
  const log = stageLogMap.value.get(stageNumber)
  if (!log) {
    if (isCompleted.value && stageNumber <= currentStage.value) return 'completed'
    if (stageNumber === currentStage.value && isRunning.value) return 'running'
    return 'pending'
  }
  return log.status
}

const getStageStatusColor = (status: string) => {
  const m: Record<string, string> = {
    pending: 'var(--app-fill-soft)',
    running: 'var(--app-text-primary)',
    completed: 'var(--app-success-contrast)',
    failed: 'var(--app-danger)',
    waiting_approval: 'var(--app-warning-contrast)',
  }
  return m[status] || 'var(--app-fill-soft)'
}

const expandedStage = ref<number | null>(null)
const toggleStage = (n: number) => {
  expandedStage.value = expandedStage.value === n ? null : n
}

const fetchProject = async () => {
  try {
    const res = await getResearchProject(projectId.value)
    project.value = res.data
    const latestRun = project.value?.latestRun ?? project.value?.runs?.[0] ?? null
    if (latestRun) {
      activeRun.value = { ...latestRun, stageLogs: [] }
      await fetchRunStatus(latestRun.id)
    } else {
      activeRun.value = null
    }
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const fetchRunStatus = async (runId: string) => {
  try {
    const res = await getResearchRunStatus(projectId.value, runId)
    activeRun.value = res.data
    await syncFallbackStageOutputs()
    runStatusError.value = ''
    return true
  } catch {
    runStatusError.value = '\u8fd0\u884c\u72b6\u6001\u5237\u65b0\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5'
    uni.showToast({ title: runStatusError.value, icon: 'none' })
    return false
  }
}

const handleStartRun = async (options?: { sourceRunId?: string; restartFromStage?: number; branchPrompt?: string }) => {
  try {
    const res = await startResearchRun(projectId.value, options)
    activeRun.value = { ...res.data, stageLogs: [] }
    await fetchRunStatus(res.data.id)
    uni.showToast({ title: '流水线已启动', icon: 'success' })
    startPolling()
  } catch {
    uni.showToast({ title: '启动失败', icon: 'none' })
  }
}

const restartFailedRun = async () => {
  if (!activeRun.value) {
    await handleStartRun()
    return
  }
  const failedStageNumber =
    activeRun.value.stageLogs.find((log) => log.status === 'failed')?.stageNumber
    ?? activeRun.value.currentStage
    ?? 1
  await handleStartRun({
    sourceRunId: activeRun.value.id,
    restartFromStage: Math.max(1, failedStageNumber),
  })
}

const handleApproveGate = async () => {
  if (!activeRun.value) return
  const runId = activeRun.value.id
  try {
    await approveResearchGate(projectId.value, runId, 'approve')
    await fetchRunStatus(runId)
    uni.showToast({ title: '已批准，继续执行', icon: 'success' })
    startPolling()
  } catch {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

const handleExport = async (mode: 'result' | 'full' = 'result') => {
  try {
    const res = await exportResearchProject(projectId.value, mode)
    const payload = res.data
    if (!payload?.contentBase64) {
      uni.showToast({ title: '暂无可导出的内容', icon: 'none' })
      return
    }

    // #ifdef H5
    const binary = window.atob(payload.contentBase64)
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index)
    }
    const blob = new Blob([bytes], {
      type: payload.mimeType || 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    const url = window.URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = payload.filename || 'research-report.docx'
    document.body.appendChild(anchor)
    anchor.click()
    document.body.removeChild(anchor)
    window.URL.revokeObjectURL(url)
    uni.showToast({ title: '报告已下载', icon: 'success' })
    // #endif

    // #ifndef H5
    uni.showToast({ title: '请在 H5 端导出 Word', icon: 'none' })
    // #endif
  } catch {
    uni.showToast({ title: '导出失败', icon: 'none' })
  }
}

const startPolling = () => {
  stopPolling()
  polling.value = setInterval(async () => {
    if (!activeRun.value) return
    const ok = await fetchRunStatus(activeRun.value.id)
    if (!ok) {
      stopPolling()
      return
    }
    // 停止轮询条件
    const st = activeRun.value?.status
    if (st === 'completed' || st === 'failed' || st === 'cancelled' || st === 'waiting_approval') {
      stopPolling()
    }
  }, 3000)
}

const stopPolling = () => {
  if (polling.value) {
    clearInterval(polling.value)
    polling.value = null
  }
}

const openBranchRestart = (stageNumber: number, stageLabel: string) => {
  branchStageNumber.value = stageNumber
  branchStageLabel.value = stageLabel
  showBranchDialog.value = true
}

const handleBranchRestart = async (data: { sourceRunId: string; restartFromStage: number; branchPrompt: string }) => {
  showBranchDialog.value = false
  await handleStartRun(data)
}

const goBack = () => {
  uni.navigateBack()
}

const handleGoBackActivate = (event?: CompatEventLike) => {
  runCompatAction('research-detail-back', event, () => {
    goBack()
  })
}

const handleExportActivate = (event?: CompatEventLike) => {
  runCompatAction('research-detail-export', event, () => {
    void handleExport(exportMode.value)
  })
}

const handleStartRunActivate = (event?: CompatEventLike) => {
  runCompatAction('research-detail-start-run', event, () => {
    if (isFailed.value) {
      void restartFailedRun()
      return
    }
    void handleStartRun()
  })
}

const handleApproveGateActivate = (event?: CompatEventLike) => {
  runCompatAction('research-detail-approve-gate', event, () => {
    void handleApproveGate()
  })
}

const handleStageToggleActivate = (stageNumber: number, event?: CompatEventLike) => {
  runCompatAction(`research-detail-stage:${stageNumber}`, event, () => {
    toggleStage(stageNumber)
  })
}

const formatElapsed = (ms: number | null) => {
  if (!ms) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(1)}s`
}

const syncFallbackStageOutputs = async () => {
  if (!activeRun.value || activeRun.value.status !== 'completed') {
    fallbackStageOutputs.value = new Map()
    return
  }
  if ((activeRun.value.stageLogs?.length ?? 0) > 0) {
    fallbackStageOutputs.value = new Map()
    return
  }

  try {
    const res = await exportResearchProject(projectId.value, 'full')
    const outputs = res.data?.stageOutputs ?? {}
    fallbackStageOutputs.value = new Map(
      Object.entries(outputs)
        .map(([stageNumber, output]) => [Number(stageNumber), output] as const)
        .filter(([stageNumber, output]) => Number.isFinite(stageNumber) && !!output?.content),
    )
  } catch {
    fallbackStageOutputs.value = new Map()
  }
}

const hasStageDetail = (stageNumber: number) =>
  stageLogMap.value.has(stageNumber)
  || fallbackStageOutputs.value.has(stageNumber)
  || getStageStatus(stageNumber) === 'completed'

const getParsedStageOutput = (stageNumber: number) =>
  parseStageOutput(stageLogMap.value.get(stageNumber)?.outputJson) ?? fallbackStageOutputs.value.get(stageNumber) ?? null

onMounted(() => {
  // @ts-ignore -- uni-app onLoad 传参
  const options = getCurrentPageRouteOptions()
  projectId.value = props.id || options.id || ''

  if (!projectId.value) {
    uni.showToast({ title: '缺少项目ID', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 1000)
    return
  }
  void fetchProject().then(() => {
    if (isRunning.value) startPolling()
  })
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <AppLayout>
    <view class="research-detail-root">
      <!-- Header -->
      <view class="bg-white border-b border-gray-200 px-4 py-3 shrink-0" style="box-sizing: border-box;">
        <view class="flex items-center gap-3 mb-2">
          <view @click="handleGoBackActivate" @tap="handleGoBackActivate" class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-gray-100 cursor-pointer shrink-0">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="chevron-left" :size="20" color="currentColor" :stroke-width="2" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
            <!-- #endif -->
          </view>
          <view class="flex-1 min-w-0" style="overflow: hidden;">
            <text class="text-lg font-bold text-gray-900 block truncate">{{ project?.name || '...' }}</text>
            <text class="text-xs text-gray-500 block truncate">{{ project?.topic || '' }}</text>
          </view>
          <view
            class="px-2.5 py-1 rounded-full text-xs font-medium shrink-0"
            :style="{ backgroundColor: statusColor + '18', color: statusColor }"
          >
            {{ statusLabel }}
          </view>
        </view>
        <!-- Progress bar -->
        <view class="flex items-center gap-2">
          <view class="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
            <view
              class="h-full rounded-full transition-all duration-500"
              :style="{
                width: (currentStage / 23 * 100) + '%',
                backgroundColor: statusColor
              }"
            ></view>
          </view>
          <text class="text-xs text-gray-400 shrink-0">{{ currentStage }}/23</text>
        </view>
      </view>

      <!-- Action Bar -->
      <view class="px-4 py-3 shrink-0 flex gap-3" style="box-sizing: border-box;">
        <button
          @click="exportMode = 'result'; handleExportActivate($event)"
          @tap="exportMode = 'result'; handleExportActivate($event)"
          class="m-0 px-4 py-2.5 rounded-xl border border-gray-200 bg-white text-gray-700 text-sm font-medium shadow-sm"
        >
          导出结果
        </button>
        <button
          @click="exportMode = 'full'; handleExportActivate($event)"
          @tap="exportMode = 'full'; handleExportActivate($event)"
          class="m-0 px-4 py-2.5 rounded-xl border border-gray-200 bg-white text-gray-700 text-sm font-medium shadow-sm"
        >
          全部导出
        </button>
        <button
          v-if="!activeRun || isFailed"
          @click="handleStartRunActivate"
          @tap="handleStartRunActivate"
          class="m-0 flex-1 py-2.5 rounded-xl bg-gray-900 text-white text-sm font-medium shadow-sm"
        >
          {{ activeRun ? '从中断处继续' : '开始研究' }}
        </button>
        <button
          v-if="isGateWaiting"
          @click="handleApproveGateActivate"
          @tap="handleApproveGateActivate"
          class="m-0 flex-1 py-2.5 rounded-xl bg-amber-500 text-white text-sm font-medium shadow-sm"
        >
          批准继续
        </button>
        <view v-if="isRunning" class="flex-1 py-2.5 rounded-xl bg-blue-50 text-blue-600 text-sm text-center font-medium flex items-center justify-center gap-2">
          <view class="w-2 h-2 rounded-full bg-blue-500 animate-pulse"></view>
          <text>执行中...</text>
        </view>
        <view v-if="isCompleted" class="flex-1 py-2.5 rounded-xl bg-green-50 text-green-600 text-sm text-center font-medium">
          研究已完成
        </view>
      </view>

      <!-- Pipeline Visualization -->
      <scroll-view scroll-y class="research-detail-scroll" :show-scrollbar="false">
        <view class="px-4 pb-6">
          <view v-if="loading" class="h-40 flex items-center justify-center text-gray-400">
            <text>加载中...</text>
          </view>
          <view v-else>
            <!-- Phase Groups -->
            <view
              v-for="(phaseKey, phaseIdx) in ['discovery', 'experimentation', 'analysis', 'publication', 'verification']"
              :key="phaseKey"
              class="mb-4"
            >
              <view class="flex items-center gap-2 mb-2">
                <view
                  class="w-2 h-2 rounded-full shrink-0"
                  :style="{ backgroundColor: phaseColors[phaseKey] }"
                ></view>
                <text class="text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {{ phaseLabels[phaseKey] }}
                </text>
              </view>

              <view class="flex flex-col gap-2.5">
                <view
                  v-for="stage in stageDefinitions.filter(s => s.phase === phaseKey)"
                  :key="stage.number"
                  @click="handleStageToggleActivate(stage.number, $event)"
                  @tap="handleStageToggleActivate(stage.number, $event)"
                  class="bg-white border rounded-xl overflow-hidden transition-all duration-300"
                  :class="{
                    'border-blue-300 shadow-md ring-1 ring-blue-100': getStageStatus(stage.number) === 'running',
                    'border-amber-300 shadow-md ring-1 ring-amber-100': getStageStatus(stage.number) === 'waiting_approval',
                    'border-red-300 shadow-sm bg-red-50/10': getStageStatus(stage.number) === 'failed',
                    'border-gray-200': getStageStatus(stage.number) === 'pending' || getStageStatus(stage.number) === 'completed',
                  }"
                >
                  <!-- Stage Row -->
                  <view 
                    class="flex items-center px-4 py-3.5 gap-3"
                    :class="{ 'bg-blue-50/50': getStageStatus(stage.number) === 'running' }"
                  >
                    <!-- Status Indicator -->
                    <view class="relative w-6 h-6 shrink-0 flex items-center justify-center">
                      <!-- #ifdef MP-WEIXIN -->
                      <MpShapeIcon v-if="getStageStatus(stage.number) === 'completed'" name="check" :size="20" :color="getStageStatusColor('completed')" :stroke-width="2.5" />
                      <!-- #endif -->
                      <!-- #ifndef MP-WEIXIN -->
                      <svg v-if="getStageStatus(stage.number) === 'completed'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" :stroke="getStageStatusColor('completed')" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
                      <!-- #endif -->
                      <view v-else-if="getStageStatus(stage.number) === 'running'" class="w-5 h-5 rounded-full border-2 border-blue-200 border-t-blue-500 animate-spin"></view>
                      <!-- #ifdef MP-WEIXIN -->
                      <MpShapeIcon v-else-if="getStageStatus(stage.number) === 'waiting_approval'" name="clock" :size="20" :color="getStageStatusColor('waiting_approval')" :stroke-width="2" />
                      <MpShapeIcon v-else-if="getStageStatus(stage.number) === 'failed'" name="close" :size="20" :color="getStageStatusColor('failed')" :stroke-width="2" />
                      <!-- #endif -->
                      <!-- #ifndef MP-WEIXIN -->
                      <svg v-else-if="getStageStatus(stage.number) === 'waiting_approval'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" :stroke="getStageStatusColor('waiting_approval')" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                      <svg v-else-if="getStageStatus(stage.number) === 'failed'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" :stroke="getStageStatusColor('failed')" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
                      <!-- #endif -->
                      <view v-else class="w-4 h-4 rounded-full border-2 border-gray-200"></view>
                    </view>

                    <!-- Stage Info -->
                    <view class="flex-1 min-w-0">
                      <view class="flex items-center gap-2">
                        <text class="text-[15px] font-semibold" :class="{
                          'text-gray-900': getStageStatus(stage.number) !== 'pending',
                          'text-blue-700': getStageStatus(stage.number) === 'running',
                          'text-amber-700': getStageStatus(stage.number) === 'waiting_approval',
                          'text-gray-400': getStageStatus(stage.number) === 'pending',
                        }">
                          {{ stage.number }}. {{ stage.label }}
                        </text>
                        <view v-if="stage.isGate" class="px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 text-[10px] uppercase font-bold tracking-wider">
                          审批节点
                        </view>
                      </view>
                    </view>

                    <!-- Elapsed time -->
                    <text v-if="stageLogMap.get(stage.number)?.elapsedMs" class="text-xs font-medium text-gray-500 bg-gray-100 px-2 py-1 rounded-md shrink-0">
                      {{ formatElapsed(stageLogMap.get(stage.number)?.elapsedMs ?? null) }}
                    </text>

                    <!-- Expand arrow -->
                    <view 
                      v-if="hasStageDetail(stage.number)"
                      class="w-8 h-8 rounded-full flex items-center justify-center shrink-0 transition-colors"
                      :class="expandedStage === stage.number ? 'bg-gray-100' : 'hover:bg-gray-50'"
                    >
                      <!-- #ifdef MP-WEIXIN -->
                      <MpShapeIcon
                        name="chevron-down"
                        :size="16"
                        color="currentColor"
                        :stroke-width="2"
                        class="transition-transform duration-300"
                        :class="{ 'rotate-180': expandedStage === stage.number }"
                      />
                      <!-- #endif -->
                      <!-- #ifndef MP-WEIXIN -->
                      <svg
                        xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="transition-transform duration-300"
                        :class="{ 'rotate-180': expandedStage === stage.number }"
                      >
                        <polyline points="6 9 12 15 18 9"/>
                      </svg>
                      <!-- #endif -->
                    </view>
                  </view>

                  <!-- Expanded Log Detail -->
                  <view
                    v-if="expandedStage === stage.number && hasStageDetail(stage.number)"
                    class="px-4 pb-4 pt-1 border-t border-gray-100 bg-gray-50/30"
                  >
                    <view class="mt-2 flex items-center gap-x-4 gap-y-1 text-xs text-gray-400 mb-3 font-mono">
                      <text v-if="stageLogMap.get(stage.number)?.startedAt">
                        开始: {{ stageLogMap.get(stage.number)?.startedAt?.replace('T', ' ').substring(0, 19) }}
                      </text>
                      <text v-if="stageLogMap.get(stage.number)?.tokensUsed">
                        tokens: {{ stageLogMap.get(stage.number)?.tokensUsed }}
                      </text>
                    </view>
                    
                    <view v-if="getParsedStageOutput(stage.number)" class="research-output-box">
                      <MarkdownRenderer
                        v-if="getParsedStageOutput(stage.number)?.format === 'markdown'"
                        :content="getParsedStageOutput(stage.number)?.content || ''"
                      />
                      <text
                        v-else
                        class="research-output-text"
                        selectable
                      >
                        {{ getParsedStageOutput(stage.number)?.content || '' }}
                      </text>
                    </view>

                    <view
                      v-else-if="getStageStatus(stage.number) === 'completed'"
                      class="bg-amber-50 border border-amber-100 rounded-lg p-3 text-sm text-amber-700 mt-2"
                    >
                      该阶段已完成，但当前运行记录里没有可展示的单独明细内容。
                    </view>

                    <view v-if="stageLogMap.get(stage.number)?.errorMessage" class="bg-red-50 border border-red-100 rounded-lg p-3 text-sm text-red-600 mt-2 font-mono">
                      {{ stageLogMap.get(stage.number)?.errorMessage }}
                    </view>

                    <!-- Branch restart for completed stages -->
                    <view
                      v-if="getStageStatus(stage.number) === 'completed' && activeRun"
                      class="mt-3 flex justify-end"
                    >
                      <view
                        class="px-3 py-1.5 rounded-lg border border-gray-200 bg-white text-xs text-gray-500 cursor-pointer hover:text-blue-600 hover:border-blue-200"
                        @click.stop="openBranchRestart(stage.number, stage.label)"
                        @tap.stop="openBranchRestart(stage.number, stage.label)"
                      >
                        <text>从此步重开</text>
                      </view>
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- Pause Config Panel -->
      <view v-if="showPauseConfig" class="px-4 pb-3 shrink-0">
        <ResearchPauseConfigPanel
          :stages="stageDefinitions"
          v-model="pauseStages"
        />
      </view>
    </view>

    <!-- Branch Restart Dialog -->
    <ResearchBranchRestartDialog
      :visible="showBranchDialog"
      :source-run-id="activeRun?.id ?? ''"
      :source-run-number="activeRun?.runNumber ?? 0"
      :stage-number="branchStageNumber"
      :stage-label="branchStageLabel"
      @close="showBranchDialog = false"
      @restart="handleBranchRestart"
    />
  </AppLayout>
</template>

<style scoped>
.research-detail-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  max-width: 100%;
  overflow: hidden;
  background-color: var(--app-page-bg);
  box-sizing: border-box;
}

.research-detail-scroll {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  box-sizing: border-box;
}

.research-output-box {
  background-color: var(--app-surface);
  border-radius: 8px;
  border: 1px solid var(--app-border-color-soft);
  padding: 12px 16px;
  margin-bottom: 8px;
  overflow-y: hidden;
  overflow-x: hidden;
  box-shadow: none;
}

.research-output-text {
  display: block;
  font-size: 14px;
  line-height: 1.75;
  color: var(--app-text-primary);
  white-space: pre-wrap;
  word-break: break-word;
}

.research-detail-root .bg-white {
  background-color: var(--app-surface) !important;
}

.research-detail-root .bg-gray-900 {
  background-color: var(--app-surface-raised) !important;
  border: 1px solid var(--app-border-color) !important;
}

.research-detail-root .bg-gray-100,
.research-detail-root .bg-gray-50\/30 {
  background-color: var(--app-surface-muted) !important;
}

.research-detail-root .bg-blue-50,
.research-detail-root .bg-blue-50\/50,
.research-detail-root .bg-green-50,
.research-detail-root .bg-amber-50,
.research-detail-root .bg-red-50\/10,
.research-detail-root .bg-amber-100 {
  background-color: var(--app-surface-muted) !important;
}

.research-detail-root .border-gray-200,
.research-detail-root .border-gray-100,
.research-detail-root .border-blue-300,
.research-detail-root .border-amber-300,
.research-detail-root .border-red-300,
.research-detail-root .border-amber-100,
.research-detail-root .border-red-100 {
  border-color: var(--app-border-color) !important;
}

.research-detail-root .ring-1,
.research-detail-root .ring-blue-100,
.research-detail-root .ring-amber-100 {
  --tw-ring-color: rgba(255, 255, 255, 0.06) !important;
}

.research-detail-root .text-gray-900,
.research-detail-root .text-gray-700,
.research-detail-root .text-gray-500,
.research-detail-root .text-white {
  color: var(--app-text-primary) !important;
}

.research-detail-root .text-gray-400,
.research-detail-root .text-blue-600,
.research-detail-root .text-blue-700,
.research-detail-root .text-green-600,
.research-detail-root .text-amber-700 {
  color: var(--app-text-secondary) !important;
}

.research-detail-root .text-red-600 {
  color: var(--app-danger) !important;
}

.research-detail-root .hover\:bg-gray-100:hover,
.research-detail-root .hover\:bg-gray-50:hover {
  background-color: var(--app-fill-hover) !important;
}

.research-detail-root .hover\:text-blue-600:hover {
  color: var(--app-text-primary) !important;
}

.research-detail-root .hover\:border-blue-200:hover {
  border-color: var(--app-border-color) !important;
}
</style>
