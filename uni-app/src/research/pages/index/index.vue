<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppLayout from '@/layouts/AppLayout.vue'
import ResearchCreateDialog from '@/components/research/ResearchCreateDialog.vue'
import {
  createResearchProject,
  deleteResearchProject,
  getResearchFeatureConfig,
  listResearchProjects,
  type ResearchProject,
} from '@/api/research'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import { inferResearchFeatureStatusFromError, type ResearchFeatureStatus as FeatureStatus } from './researchFeatureState'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const PAGE_TEXT = {
  title: '\u79d1\u7814\u52a9\u7406',
  subtitle: 'AI \u9a71\u52a8\u7684\u5168\u6d41\u7a0b\u79d1\u7814\u8f85\u52a9\uff0c\u4ece\u6587\u732e\u8c03\u7814\u5230\u8bba\u6587\u64b0\u5199',
  toolbarLabel: '\u7814\u7a76\u9879\u76ee',
  toolbarActiveHint: '\u5728\u8fd9\u91cc\u7edf\u4e00\u7ba1\u7406\u9879\u76ee\u8fdb\u5ea6\u548c\u6587\u732e\u5165\u53e3',
  toolbarEmptyHint: '\u8fd8\u6ca1\u6709\u7814\u7a76\u9879\u76ee\uff0c\u53ef\u4ee5\u4ece\u65b0\u5efa\u9879\u76ee\u5f00\u59cb',
  projectCountSuffix: '\u4e2a\u9879\u76ee',
  literatureSearch: '\u6587\u732e\u641c\u7d22',
  createProject: '\u65b0\u5efa\u9879\u76ee',
  featureDisabledTitle: '\u79d1\u7814\u52a9\u7406\u672a\u5f00\u542f',
  featureUnavailableTitle: '\u79d1\u7814\u52a9\u7406\u6682\u4e0d\u53ef\u7528',
  featureDisabledMessage: '\u79d1\u7814\u52a9\u7406\u5f53\u524d\u672a\u5f00\u542f\uff0c\u8bf7\u8054\u7cfb\u7ba1\u7406\u5458\u5728\u540e\u53f0\u542f\u7528\u540e\u518d\u4f7f\u7528\u3002',
  featureUnavailableMessage: '\u79d1\u7814\u52a9\u7406\u914d\u7f6e\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u767b\u5f55\u72b6\u6001\u6216\u7a0d\u540e\u91cd\u8bd5\u3002',
  loadFailedMessage: '\u79d1\u7814\u9879\u76ee\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u3002',
  researchDisabledToast: '\u79d1\u7814\u52a9\u7406\u672a\u5f00\u542f',
  literatureDisabledToast: '\u6587\u732e\u641c\u7d22\u672a\u5f00\u542f',
  projectNameRequired: '\u8bf7\u8f93\u5165\u9879\u76ee\u540d\u79f0\u548c\u7814\u7a76\u8bfe\u9898',
  createSuccess: '\u521b\u5efa\u6210\u529f',
  archiveSuccess: '\u5df2\u5f52\u6863',
  createDialogTitle: '\u65b0\u5efa\u7814\u7a76\u9879\u76ee',
  projectName: '\u9879\u76ee\u540d\u79f0',
  projectNamePlaceholder: '\u4f8b\u5982\uff1aLLM \u77e5\u8bc6\u84b8\u998f\u65b9\u6cd5\u5bf9\u6bd4\u7814\u7a76',
  projectTopic: '\u7814\u7a76\u8bfe\u9898',
  projectTopicPlaceholder: '\u8be6\u7ec6\u63cf\u8ff0\u4f60\u7684\u7814\u7a76\u65b9\u5411\u548c\u76ee\u6807',
  cancel: '\u53d6\u6d88',
  create: '\u521b\u5efa',
  loading: '\u52a0\u8f7d\u4e2d...',
  emptyTitle: '\u8fd8\u6ca1\u6709\u7814\u7a76\u9879\u76ee',
  emptyHint: '\u70b9\u51fb\u53f3\u4e0a\u89d2\u201c\u65b0\u5efa\u9879\u76ee\u201d\u5f00\u59cb\u4f60\u7684\u7b2c\u4e00\u4e2a\u7814\u7a76',
  archive: '\u5f52\u6863',
  modeLabel: '\u79d1\u7814\u6a21\u5f0f',
  modeSingle: '\u5355\u6838\u5904\u7406',
  modeSingleDesc: '\u5355\u6a21\u578b\u9010\u6b65\u63a8\u8fdb\uff0c\u901f\u5ea6\u5feb',
  modeTeam: '\u4e13\u5bb6\u8fa9\u8bba',
  modeTeamDesc: '\u591a\u6a21\u578b\u4ea4\u53c9\u9a8c\u8bc1\uff0c\u66f4\u7cbe\u51c6',
  modeTeamBadge: '\u4e13\u5bb6\u8fa9\u8bba',
}

const STATUS_LABELS: Record<string, string> = {
  draft: '\u8349\u7a3f',
  active: '\u8fd0\u884c\u4e2d',
  completed: '\u5df2\u5b8c\u6210',
  archived: '\u5df2\u5f52\u6863',
}

const STATUS_COLORS: Record<string, string> = {
  draft: 'bg-gray-100 text-gray-600',
  active: 'bg-blue-100 text-blue-700',
  completed: 'bg-green-100 text-green-700',
  archived: 'bg-gray-100 text-gray-400',
}

type ProjectFilter = 'all' | 'active' | 'completed' | 'archived'

const loading = ref(false)
const projects = ref<ResearchProject[]>([])
const featureEnabled = ref(true)
const literatureEnabled = ref(true)
const featureMessage = ref('')
const loadErrorMessage = ref('')
const showCreateDialog = ref(false)
const runCompatAction = createCompatActionRunner()
const featureStatus = ref<FeatureStatus>('enabled')
const statusFilter = ref<ProjectFilter>('all')

const featureBannerTitle = computed(() => (
  featureStatus.value === 'unknown'
    ? PAGE_TEXT.featureUnavailableTitle
    : PAGE_TEXT.featureDisabledTitle
))

const nonArchivedProjects = computed(() => projects.value.filter(project => project.status !== 'archived'))
const totalProjects = computed(() => nonArchivedProjects.value.length)
const activeProjectCount = computed(() => projects.value.filter(project => project.status === 'active').length)
const completedProjectCount = computed(() => projects.value.filter(project => project.status === 'completed').length)
const archivedProjectCount = computed(() => projects.value.filter(project => project.status === 'archived').length)
const filteredProjects = computed(() => {
  if (statusFilter.value === 'all') return nonArchivedProjects.value
  return projects.value.filter(project => project.status === statusFilter.value)
})
const desktopSummaryCards = computed(() => [
  {
    key: 'all',
    label: '\u5168\u90e8\u9879\u76ee',
    value: totalProjects.value,
    tone: 'neutral',
  },
  {
    key: 'active',
    label: '\u8fd0\u884c\u4e2d',
    value: activeProjectCount.value,
    tone: 'active',
  },
  {
    key: 'completed',
    label: '\u5df2\u5b8c\u6210',
    value: completedProjectCount.value,
    tone: 'completed',
  },
  {
    key: 'archived',
    label: '\u5df2\u5f52\u6863',
    value: archivedProjectCount.value,
    tone: 'archived',
  },
])

const stageProgress = (project: ResearchProject) => {
  const current = project.currentStage ?? 0
  const total = project.totalStages ?? 23
  if (total === 0) return 0
  return Math.round((current / total) * 100)
}

const fetchFeatureConfig = async (): Promise<FeatureStatus> => {
  try {
    const res = await getResearchFeatureConfig()
    const runtimeConfig = res.data
    featureStatus.value = runtimeConfig?.enabled ? 'enabled' : 'disabled'
    featureEnabled.value = Boolean(runtimeConfig?.enabled)
    literatureEnabled.value = Boolean(runtimeConfig?.literatureEnabled)
    featureMessage.value = runtimeConfig?.enabled ? '' : PAGE_TEXT.featureDisabledMessage
    return runtimeConfig?.enabled ? 'enabled' : 'disabled'
  } catch (error) {
    featureStatus.value = inferResearchFeatureStatusFromError(error)
    featureEnabled.value = false
    literatureEnabled.value = false
    featureMessage.value = featureStatus.value === 'disabled'
      ? PAGE_TEXT.featureDisabledMessage
      : PAGE_TEXT.featureUnavailableMessage
    return featureStatus.value
  }
}

const fetchProjects = async () => {
  loading.value = true
  loadErrorMessage.value = ''
  try {
    const featureStatus = await fetchFeatureConfig()
    if (featureStatus !== 'enabled') {
      projects.value = []
      return
    }

    const res = await listResearchProjects()
    projects.value = Array.isArray(res.data) ? res.data : []
  } catch (error) {
    projects.value = []
    if (inferResearchFeatureStatusFromError(error) === 'disabled') {
      featureStatus.value = 'disabled'
      featureEnabled.value = false
      literatureEnabled.value = false
      featureMessage.value = PAGE_TEXT.featureDisabledMessage
      loadErrorMessage.value = ''
      return
    }

    loadErrorMessage.value = PAGE_TEXT.loadFailedMessage
  } finally {
    loading.value = false
  }
}

const openProject = (project: ResearchProject) => {
  uni.navigateTo({ url: `/research/pages/detail/detail?id=${project.id}` })
}

const openLiteraturePage = () => {
  if (!featureEnabled.value) {
    uni.showToast({ title: PAGE_TEXT.researchDisabledToast, icon: 'none' })
    return
  }
  if (!literatureEnabled.value) {
    uni.showToast({ title: PAGE_TEXT.literatureDisabledToast, icon: 'none' })
    return
  }

  uni.navigateTo({ url: '/research/pages/literature/literature' })
}

const toggleCreateDialog = () => {
  if (!featureEnabled.value) return
  showCreateDialog.value = !showCreateDialog.value
}

const handleCreate = async (data: {
  name: string
  topic: string
  mode: string
  templateText: string
  executionMode: string
  pauseStages: string[]
}) => {
  try {
    await createResearchProject(data.name, data.topic, data.mode, {
      templateText: data.templateText,
      executionMode: data.executionMode,
      pauseStages: data.pauseStages,
    })
    showCreateDialog.value = false
    uni.showToast({ title: PAGE_TEXT.createSuccess, icon: 'success' })
    await fetchProjects()
  } catch {
    // error toast handled by api/http
  }
}

const handleDelete = async (project: ResearchProject) => {
  try {
    await deleteResearchProject(project.id)
    uni.showToast({ title: PAGE_TEXT.archiveSuccess, icon: 'success' })
    await fetchProjects()
  } catch {
    // error toast handled by api/http
  }
}

const handleOpenLiteratureActivate = (event?: CompatEventLike) => {
  runCompatAction('research-open-literature', event, () => {
    openLiteraturePage()
  })
}

const handleToggleCreateDialogActivate = (event?: CompatEventLike) => {
  runCompatAction('research-toggle-create', event, () => {
    toggleCreateDialog()
  })
}

const handleCreateActivate = (event?: CompatEventLike) => {
  runCompatAction('research-create-project', event, () => {
    // handled by dialog emit
  })
}

const handleProjectOpenActivate = (project: ResearchProject, event?: CompatEventLike) => {
  runCompatAction(`research-open-project:${project.id}`, event, () => {
    openProject(project)
  })
}

const handleDeleteActivate = (project: ResearchProject, event?: CompatEventLike) => {
  runCompatAction(`research-archive-project:${project.id}`, event, () => {
    void handleDelete(project)
  })
}

const setStatusFilter = (filter: ProjectFilter) => {
  statusFilter.value = filter
}

const handleFilterActivate = (filter: ProjectFilter, event?: CompatEventLike) => {
  runCompatAction(`research-filter:${filter}`, event, () => {
    setStatusFilter(filter)
  })
}

onShow(() => {
  void fetchProjects()
})
</script>

<template>
  <AppLayout>
    <view class="research-page">
      <view class="research-header">
        <view class="research-header-copy">
          <view class="research-header-label-row">
            <text class="research-header-label">{{ PAGE_TEXT.toolbarLabel }}</text>
            <text v-if="featureEnabled" class="research-header-count">{{ totalProjects }}{{ PAGE_TEXT.projectCountSuffix }}</text>
          </view>
          <text class="research-header-hint">
            {{ totalProjects > 0 ? PAGE_TEXT.toolbarActiveHint : PAGE_TEXT.toolbarEmptyHint }}
          </text>
        </view>
        <view class="research-header-actions">
          <button
            @click="handleOpenLiteratureActivate"
            @tap="handleOpenLiteratureActivate"
            :disabled="!featureEnabled || !literatureEnabled"
            class="research-btn-outline m-0 px-4 py-2.5 rounded-xl border border-gray-200 bg-white text-gray-700 text-sm font-medium shadow-sm hover:bg-gray-50 hover:text-blue-600 transition-colors"
          >
            <!-- Search icon -->
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="search" :size="16" color="currentColor" :stroke-width="2" class="inline-block mr-1.5 align-text-bottom" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="inline-block mr-1.5 align-text-bottom"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
            <!-- #endif -->
            {{ PAGE_TEXT.literatureSearch }}
          </button>
          <button
            @click="handleToggleCreateDialogActivate"
            @tap="handleToggleCreateDialogActivate"
            :disabled="!featureEnabled"
            class="research-btn-primary m-0 px-4 py-2.5 rounded-xl bg-gray-900 text-white text-sm font-medium shadow-sm hover:bg-gray-800 transition-colors"
          >
            <!-- Plus icon -->
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="plus" :size="16" color="currentColor" :stroke-width="2" class="inline-block mr-1 align-text-bottom" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="inline-block mr-1 align-text-bottom"><path d="M5 12h14"/><path d="M12 5v14"/></svg>
            <!-- #endif -->
            {{ PAGE_TEXT.createProject }}
          </button>
        </view>
      </view>

      <view v-if="featureEnabled" class="research-summary-grid">
        <view
          v-for="card in desktopSummaryCards"
          :key="card.key"
          class="research-summary-card"
          :class="[
            `research-summary-card-${card.tone}`,
            statusFilter === card.key ? 'research-summary-card-selected' : '',
          ]"
          @click="handleFilterActivate(card.key as ProjectFilter, $event)"
          @tap="handleFilterActivate(card.key as ProjectFilter, $event)"
        >
          <text class="research-summary-label">{{ card.label }}</text>
          <text class="research-summary-value">{{ card.value }}</text>
        </view>
      </view>

      <view
        v-if="!featureEnabled"
        class="mb-6 shrink-0 bg-white border border-amber-200 rounded-2xl p-5 shadow-sm"
      >
        <text class="font-bold text-amber-700 text-base block mb-2">{{ featureBannerTitle }}</text>
        <text class="text-sm text-amber-700 leading-relaxed">{{ featureMessage }}</text>
      </view>

      <ResearchCreateDialog
        :visible="showCreateDialog"
        @close="showCreateDialog = false"
        @create="handleCreate"
      />

      <scroll-view scroll-y class="research-scroll">
        <view
          v-if="loading && projects.length === 0"
          class="research-state-panel h-40 flex items-center justify-center text-gray-500"
        >
          <text>{{ PAGE_TEXT.loading }}</text>
        </view>
        <view
          v-else-if="!featureEnabled"
          class="research-state-panel h-40 flex items-center justify-center text-amber-600"
        >
          <text>{{ featureMessage }}</text>
        </view>
        <view
          v-else-if="loadErrorMessage"
          class="research-state-panel h-40 flex items-center justify-center text-amber-600"
        >
          <text>{{ loadErrorMessage }}</text>
        </view>
        <view
          v-else-if="filteredProjects.length === 0"
          class="research-state-panel h-72 flex flex-col items-center justify-center text-gray-400 bg-white border border-gray-100 border-dashed rounded-3xl"
        >
          <view class="w-16 h-16 bg-blue-50 rounded-2xl flex items-center justify-center mb-4">
            <!-- #ifdef MP-WEIXIN -->
            <MpShapeIcon name="book" :size="32" color="currentColor" :stroke-width="1.5" />
            <!-- #endif -->
            <!-- #ifndef MP-WEIXIN -->
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="32"
              height="32"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"></path>
              <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"></path>
            </svg>
            <!-- #endif -->
          </view>
          <text class="text-lg font-bold text-gray-800 mb-1.5">{{ totalProjects === 0 ? PAGE_TEXT.emptyTitle : '该筛选下暂无项目' }}</text>
          <text class="text-sm text-gray-500">{{ totalProjects === 0 ? PAGE_TEXT.emptyHint : '点击上方其他状态按钮可切换查看' }}</text>
        </view>
        <view v-else class="research-project-list">
          <view
            v-for="project in filteredProjects"
            :key="project.id"
            @click="handleProjectOpenActivate(project, $event)"
            @tap="handleProjectOpenActivate(project, $event)"
            class="research-project-card group bg-white border border-gray-100 rounded-[20px] p-6 shadow-sm hover:shadow-md hover:border-blue-100 transition-all cursor-pointer relative overflow-hidden"
          >
            <!-- Decorative gradient top border -->
            <view class="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-400 to-indigo-500 opacity-0 group-hover:opacity-100 transition-opacity"></view>
             
            <view class="research-project-card-header flex items-start justify-between mb-4">
              <text class="research-project-title font-bold text-gray-900 text-lg flex-1 pr-4">{{ project.name }}</text>
              <view class="research-project-actions flex items-center gap-2.5 shrink-0">
                <view
                  class="px-3 py-1 rounded-full text-xs font-semibold"
                  :class="STATUS_COLORS[project.status]"
                >
                  {{ STATUS_LABELS[project.status] }}
                </view>
                <view
                  v-if="project.mode === 'team'"
                  class="px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-600"
                >
                  {{ PAGE_TEXT.modeTeamBadge }}
                </view>
                <button
                  @click.stop="handleDeleteActivate(project, $event)"
                  @tap.stop="handleDeleteActivate(project, $event)"
                  class="m-0 px-2.5 py-1 rounded-lg border border-gray-200 bg-white text-gray-400 text-xs hover:text-red-600 hover:border-red-200 hover:bg-red-50 transition-colors"
                >
                  {{ PAGE_TEXT.archive }}
                </button>
              </view>
            </view>
            <text class="research-project-topic text-[15px] text-gray-600 leading-relaxed line-clamp-2 mb-5 block">
              {{ project.topic }}
            </text>
            <view class="research-project-footer flex items-center justify-between pt-1">
              <view class="research-progress-wrap flex items-center gap-3">
                <view class="research-progress-bar w-32 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                  <view
                    class="h-full bg-gradient-to-r from-blue-500 to-indigo-500 rounded-full transition-all duration-700 ease-out"
                    :style="{ width: stageProgress(project) + '%' }"
                  ></view>
                </view>
                <text class="text-xs font-medium text-gray-400">
                  {{ project.currentStage ?? 0 }}/{{ project.totalStages ?? 23 }} 阶段
                </text>
              </view>
              <view class="flex items-center gap-1.5 text-xs font-medium text-gray-400">
                <!-- #ifdef MP-WEIXIN -->
                <MpShapeIcon name="clock" :size="12" color="currentColor" :stroke-width="2" />
                <!-- #endif -->
                <!-- #ifndef MP-WEIXIN -->
                <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
                <!-- #endif -->
                <text>{{ project.createdAt?.replace('T', ' ').substring(0, 16) ?? '' }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </AppLayout>
</template>

<style scoped>
.research-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  width: 100%;
  max-width: 1440px;
  margin: 0 auto;
  padding: 24px 24px 0;
  box-sizing: border-box;
}

.research-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
  padding: 24px 28px;
  border: 1px solid var(--app-border-color);
  border-radius: 24px;
  background: linear-gradient(180deg, var(--app-surface-raised) 0%, var(--app-surface-muted) 100%);
  box-shadow: var(--app-shadow-elevated);
}

.research-header-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.research-header-label-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.research-header-label {
  font-size: 16px;
  line-height: 1.4;
  font-weight: 700;
  color: var(--app-text-primary);
}

.research-header-count {
  padding: 4px 10px;
  border-radius: 999px;
  background: var(--app-accent-soft);
  color: var(--app-accent-contrast);
  font-size: 12px;
  line-height: 1;
  font-weight: 600;
}

.research-header-hint {
  font-size: 13px;
  line-height: 1.7;
  color: var(--app-text-secondary);
}

.research-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.research-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}

.research-summary-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 20px;
  border-radius: 20px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.research-summary-card:active {
  transform: scale(0.99);
}

.research-summary-card-selected {
  border-color: var(--app-accent);
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.12);
}

.research-summary-card-neutral {
  background: linear-gradient(180deg, var(--app-surface-raised) 0%, var(--app-surface-muted) 100%);
}

.research-summary-card-active {
  background: linear-gradient(180deg, var(--app-surface-raised) 0%, var(--app-accent-soft) 100%);
}

.research-summary-card-completed {
  background: linear-gradient(180deg, var(--app-surface-raised) 0%, var(--app-success-soft) 100%);
}

.research-summary-card-archived {
  background: linear-gradient(180deg, var(--app-surface-raised) 0%, var(--app-surface-muted) 100%);
}

.research-summary-label {
  font-size: 13px;
  line-height: 1.4;
  color: var(--app-text-secondary);
}

.research-summary-value {
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: var(--app-text-primary);
}

.research-scroll {
  flex: 1;
  min-height: 0;
}

.research-state-panel {
  width: 100%;
  box-sizing: border-box;
  border-radius: 24px;
  border: 1px dashed var(--app-border-color);
  background: var(--app-surface-muted);
  color: var(--app-text-secondary);
}

.research-project-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding-bottom: 40px;
}

.research-project-card {
  display: flex;
  flex-direction: column;
  min-height: 240px;
  background: var(--app-surface-raised);
  border-color: var(--app-border-color);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.12);
}

.research-project-card-header {
  gap: 16px;
}

.research-project-title {
  line-height: 1.35;
  color: var(--app-text-primary);
}

.research-project-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.research-project-topic {
  min-height: 52px;
  color: var(--app-text-secondary);
}

.research-project-footer {
  margin-top: auto;
  gap: 16px;
}

.research-progress-wrap {
  min-width: 0;
  flex-wrap: wrap;
}

.research-progress-bar {
  flex-shrink: 0;
  background: var(--app-fill-soft);
}

.research-btn-outline {
  white-space: nowrap !important;
  line-height: 1.5 !important;
  border-color: var(--app-border-color) !important;
  background: var(--app-surface) !important;
  color: var(--app-text-primary) !important;
}
.research-btn-outline::after {
  display: none !important;
}
.research-btn-primary {
  background-color: var(--app-neutral-strong) !important;
  color: var(--app-neutral-strong-contrast) !important;
  border: none !important;
  white-space: nowrap !important;
  line-height: 1.5 !important;
}
.research-btn-primary::after {
  display: none !important;
}

@media (max-width: 1180px) {
  .research-page {
    padding: 18px 18px 0;
  }

  .research-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .research-project-list {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .research-page {
    padding: 14px 14px 0;
  }

  .research-header {
    flex-direction: column;
    align-items: stretch;
    padding: 18px;
  }

  .research-header-actions {
    justify-content: flex-start;
  }

  .research-summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .research-project-card {
    min-height: unset;
  }

  .research-project-card-header,
  .research-project-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .research-project-actions {
    justify-content: flex-start;
  }
}
</style>
