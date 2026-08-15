import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getConversationKnowledgeBinding,
  listKnowledgeBaseOptions,
  updateConversationKnowledgeBinding,
  type KnowledgeBaseOption,
} from '@/api/knowledge'
import {
  deleteMemoryEntry,
  getMemoryRuntimeConfig,
  listMemoryEntries,
  updateMemoryConsent,
  type MemoryEntry,
  type MemoryRuntimeConfig,
} from '@/api/memory'

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

function isKnowledgeBindingConversationId(conversationId: string | null | undefined) {
  if (!conversationId) return false
  return UUID_PATTERN.test(conversationId.trim())
}

export const useAiContextStore = defineStore('aiContext', () => {
  const knowledgeBaseOptions = ref<KnowledgeBaseOption[]>([])
  const selectedKnowledgeBaseIds = ref<string[]>([])
  const knowledgeLoading = ref(false)
  const knowledgeSyncError = ref('')
  const memoryRuntime = ref<MemoryRuntimeConfig>({ enabled: false })
  const memoryRuntimeError = ref('')
  const memoryLoading = ref(false)
  const memoryEntries = ref<MemoryEntry[]>([])
  const memoryEntriesLoading = ref(false)
  const memoryEntriesError = ref('')

  async function loadKnowledgeOptions() {
    knowledgeLoading.value = true
    try {
      const response = await listKnowledgeBaseOptions()
      knowledgeBaseOptions.value = Array.isArray(response.data) ? response.data : []
    } catch {
      knowledgeBaseOptions.value = []
    } finally {
      knowledgeLoading.value = false
    }
  }

  async function loadConversationKnowledgeSelection(conversationId: string | null) {
    if (!isKnowledgeBindingConversationId(conversationId)) {
      selectedKnowledgeBaseIds.value = []
      knowledgeSyncError.value = ''
      return
    }
    const normalizedConversationId = conversationId?.trim() ?? ''
    try {
      const response = await getConversationKnowledgeBinding(normalizedConversationId)
      selectedKnowledgeBaseIds.value = Array.isArray(response.data?.knowledgeBaseIds) ? response.data.knowledgeBaseIds : []
      knowledgeSyncError.value = ''
    } catch {
      selectedKnowledgeBaseIds.value = []
      knowledgeSyncError.value = '知识绑定加载失败'
    }
  }

  async function syncConversationKnowledgeSelection(conversationId: string | null) {
    if (!isKnowledgeBindingConversationId(conversationId)) {
      knowledgeSyncError.value = ''
      return
    }
    const normalizedConversationId = conversationId?.trim() ?? ''
    try {
      await updateConversationKnowledgeBinding(normalizedConversationId, selectedKnowledgeBaseIds.value)
      knowledgeSyncError.value = ''
    } catch {
      knowledgeSyncError.value = '知识绑定保存失败'
    }
  }

  async function toggleKnowledgeSelection(id: string, conversationId: string | null) {
    if (!id || !conversationId) return
    selectedKnowledgeBaseIds.value = selectedKnowledgeBaseIds.value.includes(id)
      ? selectedKnowledgeBaseIds.value.filter((item) => item !== id)
      : [...selectedKnowledgeBaseIds.value, id]
    await syncConversationKnowledgeSelection(conversationId)
  }

  async function loadMemoryRuntime() {
    memoryLoading.value = true
    try {
      const response = await getMemoryRuntimeConfig()
      memoryRuntime.value = response.data || { enabled: false }
      memoryRuntimeError.value = ''
    } catch {
      memoryRuntime.value = { enabled: false, summary: '记忆设置不可用。' }
      memoryRuntimeError.value = '记忆设置加载失败'
    } finally {
      memoryLoading.value = false
    }
  }

  async function loadMemoryEntries(limit = 6) {
    memoryEntriesLoading.value = true
    try {
      const response = await listMemoryEntries(limit)
      memoryEntries.value = Array.isArray(response.data) ? response.data : []
      memoryEntriesError.value = ''
    } catch {
      memoryEntries.value = []
      memoryEntriesError.value = '记忆列表加载失败'
    } finally {
      memoryEntriesLoading.value = false
    }
  }

  async function updateMemoryRuntimeConsent(next: boolean) {
    memoryLoading.value = true
    const previousRuntime = { ...memoryRuntime.value }
    try {
      const response = await updateMemoryConsent(next)
      memoryRuntime.value = response.data || { enabled: next }
      memoryRuntimeError.value = ''
    } catch {
      memoryRuntime.value = previousRuntime
      memoryRuntimeError.value = '记忆开关同步失败，请稍后重试'
    } finally {
      memoryLoading.value = false
    }
  }

  async function deleteMemoryEntryById(entryId: string) {
    if (!entryId) return
    try {
      await deleteMemoryEntry(entryId)
      memoryEntries.value = memoryEntries.value.filter((entry) => entry.id !== entryId)
      memoryEntriesError.value = ''
    } catch (error) {
      memoryEntriesError.value = '记忆删除失败'
      throw error
    }
  }

  return {
    knowledgeBaseOptions,
    selectedKnowledgeBaseIds,
    knowledgeLoading,
    knowledgeSyncError,
    memoryRuntime,
    memoryRuntimeError,
    memoryLoading,
    memoryEntries,
    memoryEntriesLoading,
    memoryEntriesError,
    loadKnowledgeOptions,
    loadConversationKnowledgeSelection,
    syncConversationKnowledgeSelection,
    toggleKnowledgeSelection,
    loadMemoryRuntime,
    loadMemoryEntries,
    updateMemoryRuntimeConsent,
    deleteMemoryEntryById,
  }
})
