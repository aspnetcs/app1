import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ConversationScope } from '@/api/types/chat'
import {
  createConversationData,
  deleteConversationData,
  fetchArchivedConversationHistoryData,
  fetchConversationHistoryData,
  fetchConversationMessagesData,
  fetchMessageVersionData,
  persistConversationMessageData,
  pinConversationData,
  restoreConversationData,
  updateConversationData,
} from './chatPersistence'
import { NEW_CONVERSATION_TITLE, sortConversationHistory } from './chatState'
import type { Conversation, Message, MessageVersionItem } from './chatState'
import { clearActiveChatSession, readActiveChatSession } from './chatSessionStorage'

export type { Conversation, Message, MessageVersionItem } from './chatState'

export const useChatStore = defineStore('chat', () => {
  const conversationScope = ref<ConversationScope>('persistent')
  const currentConversationId = ref<string | null>(null)
  const messages = ref<Message[]>([])
  const history = ref<Conversation[]>([])
  const archivedHistory = ref<Conversation[]>([])
  const isGenerating = ref(false)
  const selectedModel = ref('')
  const loadingHistory = ref(false)
  const loadingMessages = ref(false)
  const historyLoadError = ref('')
  const pendingAnchorMessageId = ref<string | null>(null)
  const archiveConfirmVisible = ref(false)
  const archiveConfirmBusy = ref(false)
  const archiveConfirmConversationId = ref<string | null>(null)
  const archiveConfirmConversationTitle = ref('')

  const addMessage = (message: Message) => {
    messages.value.push(message)
  }

  const updateLastAssistantMessage = (chunk: string) => {
    if (messages.value.length === 0) return
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage.role === 'assistant') {
      lastMessage.content += chunk
      // Auto-finish reasoning when content starts flowing
      if (lastMessage.reasoningStatus === 'thinking') {
        lastMessage.reasoningStatus = 'done'
      }
    }
  }

  const updateLastAssistantReasoning = (chunk: string) => {
    if (messages.value.length === 0) return
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage.role === 'assistant') {
      if (!lastMessage.reasoningContent) {
        lastMessage.reasoningContent = ''
        lastMessage.reasoningStatus = 'thinking'
        lastMessage.reasoningStartTime = Date.now()
      }
      lastMessage.reasoningContent += chunk
    }
  }

  const finishLastAssistantReasoning = () => {
    if (messages.value.length === 0) return
    const lastMessage = messages.value[messages.value.length - 1]
    if (lastMessage.role === 'assistant' && lastMessage.reasoningStatus === 'thinking') {
      lastMessage.reasoningStatus = 'done'
    }
  }

  const clearMessages = () => {
    messages.value = []
  }

  const applySortedHistory = (items: Conversation[]) => {
    history.value = [...items]
    sortConversationHistory(history.value)
  }

  const applyArchivedHistory = (items: Conversation[]) => {
    archivedHistory.value = [...items].sort((a, b) => {
      const left = a.deletedAt ?? a.updatedAt
      const right = b.deletedAt ?? b.updatedAt
      return right - left
    })
  }

  const upsertHistoryConversation = (conversation: Conversation) => {
    const index = history.value.findIndex((item) => item.id === conversation.id)
    if (index >= 0) {
      history.value[index] = { ...history.value[index], ...conversation }
    } else {
      history.value.unshift(conversation)
    }
    sortConversationHistory(history.value)
  }

  const touchConversation = (
    conversationId: string,
    fallbackTitle = NEW_CONVERSATION_TITLE,
    options?: {
      bumpUpdatedAt?: boolean
      mode?: Conversation['mode'] | null
      compareModelIds?: string[] | null
      captainMode?: Conversation['captainMode'] | null
    },
  ) => {
    if (conversationScope.value === 'temporary') return

    const bumpUpdatedAt = options?.bumpUpdatedAt !== false
    const hasMode = Boolean(options && Object.prototype.hasOwnProperty.call(options, 'mode'))
    const hasCompareModelIds = Boolean(options && Object.prototype.hasOwnProperty.call(options, 'compareModelIds'))
    const hasCaptainMode = Boolean(options && Object.prototype.hasOwnProperty.call(options, 'captainMode'))
    const normalizedTitle = fallbackTitle.trim()
    const existing = history.value.find((item) => item.id === conversationId)
    if (existing) {
      if (normalizedTitle && normalizedTitle !== NEW_CONVERSATION_TITLE) {
        existing.title = normalizedTitle
      }
      if (hasMode) {
        existing.mode = options?.mode ?? undefined
      }
      if (hasCompareModelIds) {
        existing.compareModelIds = options?.compareModelIds ? [...options.compareModelIds] : undefined
      }
      if (hasCaptainMode) {
        existing.captainMode = options?.captainMode ?? undefined
      }
      if (bumpUpdatedAt) {
        existing.updatedAt = Date.now()
        sortConversationHistory(history.value)
      }
      return
    }

    history.value.unshift({
      id: conversationId,
      title: normalizedTitle || NEW_CONVERSATION_TITLE,
      updatedAt: Date.now(),
      pinnedAt: null,
      model: selectedModel.value,
      mode: hasMode ? options?.mode ?? undefined : undefined,
      compareModelIds: hasCompareModelIds && options?.compareModelIds ? [...options.compareModelIds] : undefined,
      captainMode: hasCaptainMode ? options?.captainMode ?? undefined : undefined,
      pinned: false,
      starred: false,
      messageCount: 0,
    })
    sortConversationHistory(history.value)
  }

  const fetchHistory = async () => {
    loadingHistory.value = true
    historyLoadError.value = ''
    try {
      const data = await fetchConversationHistoryData()
      applySortedHistory(data.history)
      const activeSession = readActiveChatSession()
      if (activeSession?.mode === 'team' && activeSession.conversationId) {
        touchConversation(activeSession.conversationId, activeSession.title || '[\u56e2\u961f]', {
          bumpUpdatedAt: false,
          mode: 'team',
          compareModelIds: activeSession.multiModelIds,
          captainMode: activeSession.captainMode,
        })
      }

      if (currentConversationId.value && conversationScope.value === 'persistent') {
        const exists = history.value.some((item) => item.id === currentConversationId.value)
        if (!exists) {
          currentConversationId.value = null
          messages.value = []
        }
      }
    } catch (error) {
      historyLoadError.value = error instanceof Error ? error.message : 'history load failed'
      if (history.value.length === 0) {
        uni.showToast({ title: '历史对话加载失败，请稍后重试', icon: 'none' })
      }
    } finally {
      loadingHistory.value = false
    }
  }

  const fetchArchivedHistory = async (options?: { notifyOnError?: boolean }) => {
    const notifyOnError = options?.notifyOnError !== false
    try {
      const archived = await fetchArchivedConversationHistoryData()
      applyArchivedHistory(archived)
      return archived
    } catch (error) {
      if (notifyOnError) {
        uni.showToast({
          title: error instanceof Error ? error.message : '归档对话加载失败，请稍后重试',
          icon: 'none',
        })
      }
      return archivedHistory.value
    }
  }

  const loadConversation = async (conversationId: string) => {
    loadingMessages.value = true
    try {
      const loadedMessages = await fetchConversationMessagesData(conversationId)
      conversationScope.value = 'persistent'
      messages.value = loadedMessages
      currentConversationId.value = conversationId
      touchConversation(conversationId, NEW_CONVERSATION_TITLE, { bumpUpdatedAt: false })
      return true
    } catch {
      return false
    } finally {
      loadingMessages.value = false
    }
  }

  const setPendingAnchorMessageId = (messageId: string | null | undefined) => {
    pendingAnchorMessageId.value = messageId?.trim() || null
  }

  const consumePendingAnchorMessageId = () => {
    const value = pendingAnchorMessageId.value
    pendingAnchorMessageId.value = null
    return value
  }

  const ensureConversation = async (
    title?: string,
    options?: { compareModelIds?: string[] },
  ) => {
    if (currentConversationId.value) return currentConversationId.value

    const isTemporaryConversation = conversationScope.value === 'temporary'
    const created = await createConversationData({
      title: title || NEW_CONVERSATION_TITLE,
      model: selectedModel.value,
      compareModels: options?.compareModelIds,
      isTemporary: isTemporaryConversation,
    })

    currentConversationId.value = created.id
    if (!isTemporaryConversation) {
      history.value = [created, ...history.value.filter((item) => item.id !== created.id)]
      sortConversationHistory(history.value)
    }
    return created.id
  }

  const persistMessage = async (message: Message) => {
    if (!currentConversationId.value) return null
    if (conversationScope.value === 'temporary') return null

    const saved = await persistConversationMessageData(
      currentConversationId.value,
      message,
      selectedModel.value,
    )

    if (saved?.id) {
      message.serverId = String(saved.id)
    }
    if (saved?.parent_message_id) {
      message.parentMessageId = String(saved.parent_message_id)
    }
    if (typeof saved?.version === 'number') {
      message.version = saved.version
    }

    touchConversation(currentConversationId.value)
    return saved
  }

  const startConversationDraft = (scope: ConversationScope = 'persistent') => {
    conversationScope.value = scope
    currentConversationId.value = null
    clearActiveChatSession()
    clearMessages()
  }

  const loadMessageVersions = async (message: Message) => {
    const messageId = message.serverId || message.id
    if (!messageId) return [] as MessageVersionItem[]

    const versions = await fetchMessageVersionData(messageId)
    message.versionList = versions

    if (versions.length === 0) {
      message.currentVersionIndex = undefined
      return versions
    }

    let index = versions.findIndex((item) => item.id === (message.serverId || message.id))
    if (index < 0 && typeof message.version === 'number') {
      index = versions.findIndex((item) => item.version === message.version)
    }
    if (index < 0) index = versions.length - 1
    message.currentVersionIndex = index
    return versions
  }

  const cycleMessageVersion = async (message: Message) => {
    const versions = message.versionList?.length ? message.versionList : await loadMessageVersions(message)
    if (!versions || versions.length === 0) return 'unavailable' as const
    if (versions.length === 1) return 'single' as const

    const next = ((message.currentVersionIndex ?? 0) + 1) % versions.length
    const target = versions[next]
    message.currentVersionIndex = next
    message.content = target.content
    message.version = target.version
    message.model = target.model
    message.serverId = target.id
    return 'switched' as const
  }

  const switchMessageVersion = async (message: Message, index: number) => {
    const versions = message.versionList?.length ? message.versionList : await loadMessageVersions(message)
    if (!versions || versions.length === 0) return 'unavailable' as const
    if (index < 0 || index >= versions.length) return 'unavailable' as const
    if (index === message.currentVersionIndex) return 'single' as const

    const target = versions[index]
    message.currentVersionIndex = index
    message.content = target.content
    message.version = target.version
    message.model = target.model
    message.serverId = target.id
    return 'switched' as const
  }

  const deleteConversation = async (conversationId: string) => {
    await deleteConversationData(conversationId)
    history.value = history.value.filter((item) => item.id !== conversationId)
    if (readActiveChatSession()?.conversationId === conversationId) {
      clearActiveChatSession()
    }

    if (currentConversationId.value === conversationId) {
      currentConversationId.value = null
      clearMessages()
    }

    await fetchArchivedHistory({ notifyOnError: false })
  }

  const renameConversation = async (conversationId: string, title: string) => {
    const normalizedTitle = title.trim()
    if (!normalizedTitle) {
      throw new Error('对话名称不能为空')
    }
    const updated = await updateConversationData(conversationId, { title: normalizedTitle })
    upsertHistoryConversation(updated)
    return updated
  }

  const toggleConversationPinned = async (conversationId: string, pinned?: boolean) => {
    const current = history.value.find((item) => item.id === conversationId)
    const targetPinned = typeof pinned === 'boolean' ? pinned : !Boolean(current?.pinned)
    const updated = await pinConversationData(conversationId, targetPinned)
    upsertHistoryConversation(updated)
    return updated
  }

  const restoreConversation = async (conversationId: string) => {
    const restored = await restoreConversationData(conversationId)
    archivedHistory.value = archivedHistory.value.filter((item) => item.id !== conversationId)
    upsertHistoryConversation(restored)
    return restored
  }

  const openArchiveConfirm = (conversationId: string, title?: string | null) => {
    archiveConfirmConversationId.value = conversationId
    archiveConfirmConversationTitle.value = title?.trim() || '未命名对话'
    archiveConfirmVisible.value = true
  }

  const closeArchiveConfirm = () => {
    if (archiveConfirmBusy.value) return
    archiveConfirmVisible.value = false
    archiveConfirmConversationId.value = null
    archiveConfirmConversationTitle.value = ''
  }

  const confirmArchiveFromDialog = async () => {
    const conversationId = archiveConfirmConversationId.value
    if (!conversationId) return false
    archiveConfirmBusy.value = true
    try {
      await deleteConversation(conversationId)
      archiveConfirmVisible.value = false
      archiveConfirmConversationId.value = null
      archiveConfirmConversationTitle.value = ''
      return true
    } finally {
      archiveConfirmBusy.value = false
    }
  }

  return {
    conversationScope,
    currentConversationId,
    messages,
    history,
    archivedHistory,
    isGenerating,
    selectedModel,
    loadingHistory,
    loadingMessages,
    historyLoadError,
    pendingAnchorMessageId,
    archiveConfirmVisible,
    archiveConfirmBusy,
    archiveConfirmConversationId,
    archiveConfirmConversationTitle,
    addMessage,
    updateLastAssistantMessage,
    updateLastAssistantReasoning,
    finishLastAssistantReasoning,
    clearMessages,
    startConversationDraft,
    fetchHistory,
    fetchArchivedHistory,
    loadConversation,
    setPendingAnchorMessageId,
    consumePendingAnchorMessageId,
    ensureConversation,
    persistMessage,
    loadMessageVersions,
    cycleMessageVersion,
    switchMessageVersion,
    touchConversation,
    deleteConversation,
    renameConversation,
    toggleConversationPinned,
    restoreConversation,
    openArchiveConfirm,
    closeArchiveConfirm,
    confirmArchiveFromDialog,
  }
})
