import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  createCodeToolsTask,
  getCodeToolsTask,
  listCodeToolsTaskArtifacts,
  listCodeToolsTaskLogs,
  listCodeToolsTasks,
  type CodeToolsTaskArtifactItem,
  type CodeToolsTaskItem,
  type CodeToolsTaskListResponse,
  type CodeToolsTaskLogItem,
} from '@/api/codeTools'

export const useCodeToolsStore = defineStore('code-tools', () => {
  const list = ref<CodeToolsTaskListResponse | null>(null)
  const loadingList = ref(false)
  const loadingCreate = ref(false)

  const current = ref<CodeToolsTaskItem | null>(null)
  const currentLogs = ref<CodeToolsTaskLogItem[] | null>(null)
  const currentArtifacts = ref<CodeToolsTaskArtifactItem[] | null>(null)
  const loadingCurrent = ref(false)

  const items = computed(() => list.value?.items || [])

  async function fetchList(payload: { page?: number; size?: number } = {}) {
    const page = payload.page ?? 0
    const size = payload.size ?? 20
    loadingList.value = true
    try {
      const response = await listCodeToolsTasks(page, size)
      list.value = response.data
      return list.value
    } finally {
      loadingList.value = false
    }
  }

  async function create(payload: { kind: string; input?: Record<string, unknown> }) {
    loadingCreate.value = true
    try {
      const response = await createCodeToolsTask(payload.kind, payload.input)
      await fetchList({ page: 0, size: list.value?.size ?? 20 })
      current.value = response.data
      return response.data
    } finally {
      loadingCreate.value = false
    }
  }

  async function fetchTask(id: string) {
    loadingCurrent.value = true
    try {
      const [taskRes, logsRes, artifactsRes] = await Promise.all([
        getCodeToolsTask(id),
        listCodeToolsTaskLogs(id),
        listCodeToolsTaskArtifacts(id),
      ])
      current.value = taskRes.data
      currentLogs.value = logsRes.data
      currentArtifacts.value = artifactsRes.data
      return current.value
    } finally {
      loadingCurrent.value = false
    }
  }

  function clearCurrent() {
    current.value = null
    currentLogs.value = null
    currentArtifacts.value = null
    loadingCurrent.value = false
  }

  return {
    list,
    items,
    loadingList,
    loadingCreate,
    current,
    currentLogs,
    currentArtifacts,
    loadingCurrent,
    fetchList,
    create,
    fetchTask,
    clearCurrent,
  }
})

