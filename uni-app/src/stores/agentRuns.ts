import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import {
  completeAgentRun,
  createAgentRun,
  failAgentRun,
  getAgentRun,
  listAgentRuns,
  startAgentRun,
  type AgentRun,
  type AgentRunListResponse,
} from '@/api/agentRuns'

export const useAgentRunsStore = defineStore('agentRuns', () => {
  const list = ref<AgentRunListResponse | null>(null)
  const current = ref<AgentRun | null>(null)
  const loadingList = ref(false)
  const loadingCurrent = ref(false)

  const hasRuns = computed(() => Boolean(list.value?.items?.length))

  async function fetchList(input: { page?: number; size?: number } = {}) {
    loadingList.value = true
    try {
      const response = await listAgentRuns(input)
      list.value = response.data
      return list.value
    } finally {
      loadingList.value = false
    }
  }

  async function fetchRun(id: string) {
    loadingCurrent.value = true
    try {
      const response = await getAgentRun(id)
      current.value = response.data as AgentRun
      return current.value
    } finally {
      loadingCurrent.value = false
    }
  }

  async function create(input: { agentId: string; requestedChannelId?: number | null }) {
    const response = await createAgentRun(input)
    const run = response.data as AgentRun
    current.value = run
    await fetchList({ page: 0, size: list.value?.size ?? 20 })
    return run
  }

  async function start(id: string) {
    const response = await startAgentRun(id)
    current.value = response.data as AgentRun
    await fetchList({ page: list.value?.page ?? 0, size: list.value?.size ?? 20 })
    return current.value
  }

  async function complete(id: string) {
    const response = await completeAgentRun(id)
    current.value = response.data as AgentRun
    await fetchList({ page: list.value?.page ?? 0, size: list.value?.size ?? 20 })
    return current.value
  }

  async function fail(id: string, errorMessage?: string) {
    const response = await failAgentRun(id, errorMessage)
    current.value = response.data as AgentRun
    await fetchList({ page: list.value?.page ?? 0, size: list.value?.size ?? 20 })
    return current.value
  }

  function clear() {
    list.value = null
    current.value = null
    loadingList.value = false
    loadingCurrent.value = false
  }

  return {
    list,
    current,
    loadingList,
    loadingCurrent,
    hasRuns,
    fetchList,
    fetchRun,
    create,
    start,
    complete,
    fail,
    clear,
  }
})

