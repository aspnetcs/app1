<script setup lang="ts">
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { getCurrentPageRouteOptions } from '@/utils/pageRoute'
import { useChatStore } from '@/stores/chat'
import type { Message } from '@/stores/chat'
import {
  applyStreamingAssistantDelta,
  applyStreamingAssistantThinking,
  upsertStreamingAssistantMessage,
} from '@/stores/chatState'
import { useAppStore } from '@/stores/app'
import { useModelStore } from '@/stores/models'
import { useDebateStore } from '@/stores/debate'
import { useWebSocket } from '@/composables/useWebSocket'
import { provideChatMode, provideChatConfigSheet, provideChatInputBlurSignal } from '@/composables/useChatMode'
import AppLayout from '@/layouts/AppLayout.vue'
import { fetchSse } from '@/utils/sse'
import { chatCompletions, chatCompletionsMulti, chatCompat, getChatCompletionsSseUrl } from '@/api/chat'
import { buildAppLayoutModelOptions } from '@/layouts/appLayoutModelOptions'
import { useAiContextStore } from '@/stores/aiContext'
import { useUserPreferencesStore } from '@/stores/userPreferences'
import { useChatViewport } from './useChatViewport'
import { useTeamDebate } from '@/composables/useTeamDebate'
import ChatInputBar from './ChatInputBar.vue'
import ChatLandingPrompts from './ChatLandingPrompts.vue'
import ChatMessageList from './ChatMessageList.vue'
import DebatePanel from './components/DebatePanel.vue'
import ChatConfigSheet from '@/components/ChatConfigSheet.vue'
import AttachmentList from '@/components/attachments/AttachmentList.vue'
import { SUGGESTED_PROMPTS, type ChatPrompt } from './chatPageShared'
import { buildChatRequestPayload } from './chatRequestPayload'
import { normalizeMultiModelIds, resolveChatSendAttempt } from './chatSendState'
import {
  buildCompareRoundMessages,
  buildComparePayloadForModel,
  buildCompareRoundsFromMessages,
  type CompareResponse,
  type CompareRound,
} from './compareRoundsState'
import { extractErrorMessage } from '@/utils/errorMessage'
import { createCompatActionRunner, type CompatEventLike } from '@/utils/h5EventCompat'
import { readTraceIdFromPayload } from '@/utils/traceId'
import { config } from '@/config'
import { fetchWebRead } from '@/api/web-read'
import { getMarketAssetSummary, getMarketAssetTitle, resolveAgentAssetRuntimeId, type MarketAsset } from '@/api/market'
import {
  clearActiveChatSession,
  readActiveChatSession,
  writeActiveChatSession,
} from '@/stores/chatSessionStorage'
import { useChatAttachments } from '@/composables/useChatAttachments'
// #ifdef MP-WEIXIN
import MpShapeIcon from '@/components/icons/MpShapeIcon.vue'
// #endif

const chatStore = useChatStore()
const appStore = useAppStore()
const modelStore = useModelStore()
const debateStore = useDebateStore()
const aiContextStore = useAiContextStore()
const userPreferencesStore = useUserPreferencesStore()
const inputContent = ref('')
const chatInputBarRef = ref<InstanceType<typeof ChatInputBar> | null>(null)
const pageInstance = getCurrentInstance()
const streamAbort = ref<null | (() => void)>(null)
const stoppedByUser = ref(false)
const showMobileConfigSheet = provideChatConfigSheet()
provideChatInputBlurSignal()
const runCompatAction = createCompatActionRunner()
const sessionRestoreResolved = ref(false)
const agentContextSuppressed = ref(false)
const activeAgentOrigin = ref<'route' | 'default' | 'manual' | null>(null)

// -- Web Read context (Task 1B) --
const webReadContext = ref('')
const webReadStatus = ref<'idle' | 'loading' | 'done'>('idle')

// -- Multimodal attachments --
const attachments = useChatAttachments()
const ATTACHMENT_ONLY_PROMPT = '请识别并总结附件内容。'

type OutputFormatId = 'auto' | 'markdown' | 'table' | 'json' | 'summary' | 'outline'

const outputFormatOptions: Array<{ id: OutputFormatId; label: string; instruction: string }> = [
  { id: 'auto', label: '自动', instruction: '' },
  {
    id: 'markdown',
    label: 'Markdown',
    instruction: '请用结构清晰的 Markdown 输出，保留必要标题、列表和重点信息。',
  },
  {
    id: 'table',
    label: '表格',
    instruction: '请优先使用 Markdown 表格输出可对比的信息；无法表格化的内容放在表格下方补充。',
  },
  {
    id: 'json',
    label: 'JSON',
    instruction: '请只输出合法 JSON，不要包裹 Markdown 代码块；字段命名清晰，无法确定的值使用 null。',
  },
  {
    id: 'summary',
    label: '摘要',
    instruction: '请输出简明摘要，先给结论，再列出关键事实和待确认事项。',
  },
  {
    id: 'outline',
    label: '提纲',
    instruction: '请按层级提纲输出，使用简短标题组织主要观点、细节和行动项。',
  },
]

const selectedOutputFormat = ref<OutputFormatId>('auto')
const selectedOutputFormatOption = computed(() =>
  outputFormatOptions.find((item) => item.id === selectedOutputFormat.value) ?? outputFormatOptions[0],
)
const outputFormatInstruction = computed(() => selectedOutputFormatOption.value.instruction)
const canSendCurrentInput = computed(() =>
  Boolean(inputContent.value.trim() || attachments.hasAttachments.value) && !attachments.uploading.value,
)

function selectOutputFormat(formatId: OutputFormatId) {
  selectedOutputFormat.value = formatId
}

async function handleWebReadRequest(url: string) {
  if (!url || webReadStatus.value === 'loading') return
  webReadStatus.value = 'loading'
  try {
    const resp = await fetchWebRead({ url })
    if (resp.data?.content) {
      webReadContext.value = `[网页内容 - ${resp.data.title || url}]\n${resp.data.content}`
      webReadStatus.value = 'done'
    } else {
      throw new Error('未获取到网页内容')
    }
  } catch (error) {
    const msg = error instanceof Error ? error.message : '网页抓取失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none', duration: 2000 })
    webReadStatus.value = 'idle'
  }
}

function handleWebReadClear() {
  webReadContext.value = ''
  webReadStatus.value = 'idle'
}


// -- MP keyboard + safe-area metrics (single source of truth for chat layout) --
const keyboardHeightPx = ref(0)
const inputBarHeightPx = ref(180) // fallback until the input bar reports its measured height
const safeAreaBottomPx = computed(() => appStore.safeAreaBottom)

const inputBarBottomOffsetPx = computed(() => Math.max(0, keyboardHeightPx.value))
const inputBarSafeBottomPx = computed(() => (inputBarBottomOffsetPx.value > 0 ? 0 : safeAreaBottomPx.value))
const contentBottomPaddingPx = computed(() => Math.max(0, inputBarHeightPx.value + inputBarBottomOffsetPx.value))
const chatViewportHeightPx = ref(0)
const TEAM_RAIL_DESKTOP_BREAKPOINT = 768
const TEAM_RAIL_DESKTOP_WIDTH_PX = 320
const TEAM_RAIL_DESKTOP_GAP_PX = 20
const desktopTeamRailOpen = ref(false)
const isDesktopTeamRailLayout = computed(() => appStore.screenWidth >= TEAM_RAIL_DESKTOP_BREAKPOINT)
const desktopTeamRailInsetPx = computed(() => {
  if (!isTeamMode.value || !isDesktopTeamRailLayout.value || !desktopTeamRailOpen.value) {
    return 0
  }
  return TEAM_RAIL_DESKTOP_WIDTH_PX + TEAM_RAIL_DESKTOP_GAP_PX
})
const chatContainerStyle = computed(() => ({
  '--team-rail-desktop-width': `${TEAM_RAIL_DESKTOP_WIDTH_PX}px`,
  '--chat-input-desktop-right-inset': `${desktopTeamRailInsetPx.value}px`,
}))
const chatScrollViewportStyle = computed(() => {
  const height = Math.max(0, Math.round(Number(chatViewportHeightPx.value || 0)))
  return height > 0
    ? {
        height: `${height}px`,
        minHeight: `${height}px`,
      }
    : {}
})

function measureChatViewportHeight() {
  if (!pageInstance) return
  uni
    .createSelectorQuery()
    .in(pageInstance)
    .select('.chat-container')
    .boundingClientRect((rect) => {
      const node = Array.isArray(rect) ? rect[0] : rect
      const rawHeight =
        node && typeof node === 'object' && 'height' in node
          ? (node as { height?: unknown }).height
          : 0
      const height = Math.max(0, Math.round(Number(rawHeight ?? 0)))
      if (height > 0 && height !== chatViewportHeightPx.value) {
        chatViewportHeightPx.value = height
      }
    })
    .exec()
}

function scheduleChatViewportMeasure() {
  setTimeout(measureChatViewportHeight, 0)
  setTimeout(measureChatViewportHeight, 120)
}

function openDesktopTeamRail() {
  if (!isDesktopTeamRailLayout.value) return
  desktopTeamRailOpen.value = true
}

const IS_MP_WEIXIN = (() => {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  return false
})()

type KeyboardHeightChangeEvent = { height?: number }
let mpKeyboardHeightHandler: ((event: KeyboardHeightChangeEvent) => void) | null = null
let mpKeyboardHeightOff: ((handler: (event: KeyboardHeightChangeEvent) => void) => void) | null = null

onMounted(() => {
  scheduleChatViewportMeasure()
  if (!IS_MP_WEIXIN) return
  // #ifdef MP-WEIXIN
  const wxApi = (globalThis as any).wx as any
  const uniApi = (globalThis as any).uni as any

  mpKeyboardHeightHandler = (event: KeyboardHeightChangeEvent) => {
    const height = Math.max(0, Math.round(Number(event?.height ?? 0)))
    keyboardHeightPx.value = height
  }

  if (wxApi && typeof wxApi.onKeyboardHeightChange === 'function') {
    wxApi.onKeyboardHeightChange(mpKeyboardHeightHandler)
    if (typeof wxApi.offKeyboardHeightChange === 'function') {
      mpKeyboardHeightOff = (handler) => wxApi.offKeyboardHeightChange(handler)
    }
    return
  }

  if (uniApi && typeof uniApi.onKeyboardHeightChange === 'function') {
    uniApi.onKeyboardHeightChange(mpKeyboardHeightHandler)
    if (typeof uniApi.offKeyboardHeightChange === 'function') {
      mpKeyboardHeightOff = (handler) => uniApi.offKeyboardHeightChange(handler)
    }
  }
  // #endif
})

watch(
  () => [appStore.screenWidth, (appStore as any).screenHeight, keyboardHeightPx.value, inputBarHeightPx.value] as const,
  () => {
    scheduleChatViewportMeasure()
  },
)

onBeforeUnmount(() => {
  if (!IS_MP_WEIXIN) return
  // #ifdef MP-WEIXIN
  if (mpKeyboardHeightHandler && mpKeyboardHeightOff) {
    try {
      mpKeyboardHeightOff(mpKeyboardHeightHandler)
    } catch {
      // ignore
    }
  }
  mpKeyboardHeightHandler = null
  mpKeyboardHeightOff = null
  // #endif
})

const ws = useWebSocket()
const wsReady = ref(false)
ws.on('ready', () => {
  wsReady.value = true
})
watch(
  () => ws.connected.value,
  (connected) => {
    if (!connected) {
      wsReady.value = false
    }
  },
  { immediate: true },
)

type WsChatEventType = 'chat.delta' | 'chat.thinking' | 'chat.done' | 'chat.error' | 'chat.notice'
type WsChatHandlers = {
  onDelta?: (delta: string) => void
  onThinking?: (delta: string) => void
  onDone?: () => void
  onError?: (message: string) => void
  onNotice?: (message: string) => void
  onTrace?: (traceId: string) => void
}

const WS_CHAT_BUFFER_LIMIT = 80
const WS_CHAT_CLOSED_TTL_MS = 10 * 60 * 1000
const wsChatHandlers = new Map<string, WsChatHandlers>()
const wsChatBuffer = new Map<string, Array<{ event: WsChatEventType; payload: Record<string, unknown> }>>()
const wsChatClosedUntil = new Map<string, number>()
const wsChatTraceId = new Map<string, string>()

function readRequestId(payload: unknown): string | null {
  if (!payload || typeof payload !== 'object') return null
  const value = (payload as Record<string, unknown>).requestId
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function readTraceId(payload: unknown): string | null {
  // Backward-compat: keep local helper name to reduce diff; delegate to shared util.
  return readTraceIdFromPayload(payload)
}

function readDelta(payload: unknown): string {
  if (!payload || typeof payload !== 'object') return ''
  const value = (payload as Record<string, unknown>).delta
  return typeof value === 'string' ? value : ''
}

function readMessage(payload: unknown): string {
  if (!payload || typeof payload !== 'object') return ''
  const value = (payload as Record<string, unknown>).message
  return typeof value === 'string' ? value : ''
}

function isChatRequestClosed(requestId: string) {
  const until = wsChatClosedUntil.get(requestId)
  if (!until) return false
  if (until <= Date.now()) {
    wsChatClosedUntil.delete(requestId)
    return false
  }
  return true
}

function closeChatRequest(requestId: string) {
  wsChatHandlers.delete(requestId)
  wsChatBuffer.delete(requestId)
  wsChatTraceId.delete(requestId)
  wsChatClosedUntil.set(requestId, Date.now() + WS_CHAT_CLOSED_TTL_MS)
}

function dispatchChatWsEvent(event: WsChatEventType, payload: unknown, handler: WsChatHandlers) {
  if (event === 'chat.delta') {
    const delta = readDelta(payload)
    if (delta) {
      handler.onDelta?.(delta)
    }
    return
  }
  if (event === 'chat.thinking') {
    const delta = readDelta(payload)
    if (delta) {
      handler.onThinking?.(delta)
    }
    return
  }
  if (event === 'chat.done') {
    handler.onDone?.()
    return
  }
  if (event === 'chat.error') {
    handler.onError?.(readMessage(payload) || 'chat error')
    return
  }
  if (event === 'chat.notice') {
    const message = readMessage(payload)
    if (message) {
      handler.onNotice?.(message)
    }
  }
}

function bufferChatWsEvent(requestId: string, event: WsChatEventType, payload: Record<string, unknown>) {
  const existing = wsChatBuffer.get(requestId) ?? []
  if (existing.length >= WS_CHAT_BUFFER_LIMIT) return
  existing.push({ event, payload })
  wsChatBuffer.set(requestId, existing)
}

function handleChatWsEvent(event: WsChatEventType) {
  return (payload: unknown) => {
    const requestId = readRequestId(payload)
    if (!requestId) return
    if (isChatRequestClosed(requestId)) return

    const handler = wsChatHandlers.get(requestId)
    if (!handler) {
      // No handler yet (race: delta arrives before we register). Create a placeholder so the UI has
      // exactly one assistant message instance per requestId; still buffer the payload for replay.
      if (event === 'chat.delta' || event === 'chat.thinking') {
        const traceId = readTraceId(payload)
        upsertStreamingAssistantMessage(chatStore.messages, requestId, traceId ? { traceId } : undefined)
      }
      bufferChatWsEvent(requestId, event, (payload as Record<string, unknown>) || {})
      return
    }

    const traceId = readTraceId(payload)
    if (traceId) {
      const existing = wsChatTraceId.get(requestId)
      if (!existing || existing !== traceId) {
        wsChatTraceId.set(requestId, traceId)
        handler.onTrace?.(traceId)
      }
    }
    dispatchChatWsEvent(event, payload, handler)
  }
}

ws.on('chat.delta', handleChatWsEvent('chat.delta'))
ws.on('chat.thinking', handleChatWsEvent('chat.thinking'))
ws.on('chat.done', handleChatWsEvent('chat.done'))
ws.on('chat.error', handleChatWsEvent('chat.error'))
ws.on('chat.notice', handleChatWsEvent('chat.notice'))

function registerChatWsHandlers(requestId: string, handler: WsChatHandlers) {
  wsChatHandlers.set(requestId, handler)
  const buffered = wsChatBuffer.get(requestId)
  if (buffered && buffered.length) {
    for (const item of buffered) {
      const traceId = readTraceId(item.payload)
      if (traceId) {
        const existing = wsChatTraceId.get(requestId)
        if (!existing || existing !== traceId) {
          wsChatTraceId.set(requestId, traceId)
          handler.onTrace?.(traceId)
        }
      }
      dispatchChatWsEvent(item.event, item.payload, handler)
    }
    wsChatBuffer.delete(requestId)
  }
  return () => closeChatRequest(requestId)
}

async function ensureChatWsReady(timeoutMs = 5000) {
  if (!IS_MP_WEIXIN) return false
  if (wsReady.value) return true
  void ws.connect()
  return await new Promise<boolean>((resolve) => {
    const stop = watch(
      () => wsReady.value,
      (ready) => {
        if (ready) {
          clearTimeout(timer)
          stop()
          resolve(true)
        }
      },
      { immediate: true },
    )
    const timer = setTimeout(() => {
      stop()
      resolve(false)
    }, timeoutMs)
  })
}

// Agent context from market navigation
const activeAgentId = ref<string | null>(null)
const activeAgentName = ref<string | null>(null)

const CHAT_SSE_URL = getChatCompletionsSseUrl()

// index.vue is the top-level page (parent of AppLayout), so it must PROVIDE
const { chatMode, multiModelIds, captainMode, isTeamMode, isCompareMode, isSingleMode } = provideChatMode()

// Team debate composable
const { startDebate, continueDebate, recoverDebate, endDebate } = useTeamDebate()

// -- Compare mode: Arena.ai round-based layout --
// Each round = 1 user message + N model responses side-by-side
const compareRounds = reactive<CompareRound[]>([])
const isComparingGeneration = ref(false)
const compareAborts = ref<Array<(() => void) | null>>([])

function buildConversationTitle(text: string) {
  return text.length > 30 ? text.slice(0, 30) : text
}

function resolveConversationTitle(conversationId: string | null | undefined) {
  if (!conversationId) return undefined
  return chatStore.history.find((item) => item.id === conversationId)?.title
}

function syncActiveTeamConversation(conversationId: string) {
  chatStore.conversationScope = 'persistent'
  chatStore.currentConversationId = conversationId
}

const isLanding = computed(() => {
  if (isTeamMode.value) return !debateStore.isActive
  if (isCompareMode.value) return compareRounds.length === 0 && chatStore.messages.length === 0
  return chatStore.messages.length === 0
})
const showCompareMessageFallback = computed(() => {
  return isCompareMode.value && compareRounds.length === 0 && chatStore.messages.length > 0
})
const isTemporaryConversation = computed(() => chatStore.conversationScope === 'temporary')
const isAgentSelectionLocked = computed(() => {
  if (chatStore.messages.length > 0) return true
  if (chatStore.currentConversationId) return true
  if (debateStore.conversationId) return true
  return false
})

const selectedAgentAsset = computed(() => userPreferencesStore.findAgentAssetById(activeAgentId.value))
const effectiveAgentName = computed(() => {
  if (activeAgentName.value) {
    return activeAgentName.value
  }
  if (selectedAgentAsset.value) {
    return getMarketAssetTitle(selectedAgentAsset.value)
  }
  if (activeAgentOrigin.value === 'default' && userPreferencesStore.defaultAgentAsset) {
    return getMarketAssetTitle(userPreferencesStore.defaultAgentAsset)
  }
  return null
})
const agentControlSummary = computed(() => {
  if (userPreferencesStore.loading || userPreferencesStore.savedAssetsLoading.AGENT) {
    return '正在加载智能体配置...'
  }
  if (effectiveAgentName.value) {
    return activeAgentOrigin.value === 'default' ? `默认 · ${effectiveAgentName.value}` : effectiveAgentName.value
  }
  if (userPreferencesStore.defaultAgentAsset) {
    return `默认：${getMarketAssetTitle(userPreferencesStore.defaultAgentAsset)}`
  }
  return '未设置'
})
const agentBannerLabel = computed(() => (activeAgentOrigin.value === 'default' ? '默认智能体' : '当前智能体'))
const agentBannerInitial = computed(() => {
  const value = (effectiveAgentName.value || '智').trim()
  return value ? value.charAt(0) : '智'
})

const agentSelectorOptions = computed(() =>
  userPreferencesStore.savedAgentAssets.map((asset) => {
    const runtimeId = resolveAgentAssetRuntimeId(asset)
    const isSelected = Boolean(activeAgentId.value && runtimeId === activeAgentId.value)
    const isDefault = Boolean(
      userPreferencesStore.defaultAgentAsset && asset.sourceId === userPreferencesStore.defaultAgentAsset.sourceId,
    )

    return {
      id: runtimeId,
      title: getMarketAssetTitle(asset),
      subtitle: getMarketAssetSummary(asset),
      badge: isSelected ? '当前' : isDefault ? '默认' : undefined,
    }
  }),
)

const chatRuntimePayloadOptions = computed(() => ({
  isTemporary: isTemporaryConversation.value,
  maskId: activeAgentId.value,
  knowledgeBaseIds: aiContextStore.selectedKnowledgeBaseIds,
  memoryEnabled: Boolean(aiContextStore.memoryRuntime?.enabled),
  outputFormatInstruction: outputFormatInstruction.value,
}))

function applyAgentAsset(asset: MarketAsset, origin: 'default' | 'manual') {
  activeAgentId.value = resolveAgentAssetRuntimeId(asset)
  activeAgentName.value = getMarketAssetTitle(asset)
  activeAgentOrigin.value = origin
  agentContextSuppressed.value = false
}

function applyRouteAgent(agentId: string, agentName?: string | null) {
  activeAgentId.value = agentId
  activeAgentName.value = agentName?.trim() || null
  activeAgentOrigin.value = 'route'
  agentContextSuppressed.value = false
}

function clearAgentContext(options: { suppressDefault?: boolean } = {}) {
  activeAgentId.value = null
  activeAgentName.value = null
  activeAgentOrigin.value = null
  if (options.suppressDefault) {
    agentContextSuppressed.value = true
  }
}

function applyDefaultAgentIfIdle() {
  if (agentContextSuppressed.value || activeAgentId.value || !userPreferencesStore.defaultAgentAsset) {
    return
  }
  if (chatMode.value !== 'single') return
  if (chatStore.conversationScope === 'temporary') return
  if (chatStore.currentConversationId || chatStore.messages.length > 0) return
  applyAgentAsset(userPreferencesStore.defaultAgentAsset, 'default')
}

function notifyAgentSelectionLocked() {
  showError('当前会话的智能体已锁定，如需切换请新建会话')
}

function handleClearActiveAgent() {
  if (isAgentSelectionLocked.value) {
    notifyAgentSelectionLocked()
    return
  }
  clearAgentContext({ suppressDefault: true })
}

function handleAgentSelect(agentId: string) {
  if (agentId === activeAgentId.value) return
  if (isAgentSelectionLocked.value) {
    notifyAgentSelectionLocked()
    return
  }
  const asset = userPreferencesStore.savedAgentAssets.find(
    (item) => resolveAgentAssetRuntimeId(item) === agentId,
  )
  if (!asset) return
  applyAgentAsset(asset, 'manual')
}

function openAgentMarket() {
  uni.navigateTo({ url: '/market/pages/index/index' })
}


watch(
  () => chatStore.currentConversationId,
  (conversationId) => {
    if (!config.features.knowledgeBase) return
    void aiContextStore.loadConversationKnowledgeSelection(conversationId)
  },
  { immediate: true },
)

onMounted(() => {
  const loaders: Array<Promise<unknown>> = [
    userPreferencesStore.loadPreferences(),
    userPreferencesStore.loadSavedAssets('AGENT'),
    userPreferencesStore.loadSavedAssets('MCP'),
  ]
  if (config.features.knowledgeBase) {
    loaders.push(aiContextStore.loadKnowledgeOptions())
  }
  if (config.features.memory) {
    loaders.push(aiContextStore.loadMemoryRuntime())
  }
  void Promise.all(loaders)
})

watch(
  () => [
    sessionRestoreResolved.value,
    chatMode.value,
    chatStore.currentConversationId,
    chatStore.messages.length,
    chatStore.conversationScope,
    activeAgentId.value,
    agentContextSuppressed.value,
    userPreferencesStore.defaultAgentAsset?.sourceId ?? null,
  ] as const,
  ([restoreResolved, mode, conversationId, messageCount, conversationScope, currentAgent]) => {
    if (!restoreResolved) return
    if (mode !== 'single' || conversationScope === 'temporary' || conversationId || messageCount > 0 || currentAgent) {
      if (agentContextSuppressed.value) {
        agentContextSuppressed.value = false
      }
      return
    }
    applyDefaultAgentIfIdle()
  },
  { immediate: true },
)

const currentModelOption = computed(() => {
  const options = buildAppLayoutModelOptions(modelStore.models)
  return options.find(m => m.id === modelStore.selectedModelId) || null
})
const modelDisplayName = computed(() => currentModelOption.value?.name || 'AI')
const modelAvatarPath = computed(() => currentModelOption.value?.avatarPath || '')
const {
  scrollIntoView,
  scrollTop,
  showScrollToLatest,
  newMessageCount,
  isPinnedToBottom,
  scrollToBottom,
  scrollToMessage,
  jumpToLatest,
  beginUserScroll,
  onMessageScroll,
  onScrollToLower,
} = useChatViewport(computed(() => chatStore.messages))

function handleMessageUserScrollIntent() {
  beginUserScroll()
  chatInputBarRef.value?.closeAgentPopup?.()
}

watch(
  () => [chatStore.currentConversationId, chatStore.messages.length, chatStore.pendingAnchorMessageId] as const,
  ([conversationId, messageCount, pendingAnchorMessageId]) => {
    if (!conversationId || !messageCount || !pendingAnchorMessageId) return
    const targetExists = chatStore.messages.some(
      (message) => message.id === pendingAnchorMessageId || message.serverId === pendingAnchorMessageId,
    )
    if (!targetExists) return
    chatStore.consumePendingAnchorMessageId()
    scrollToMessage(pendingAnchorMessageId)
  },
  { immediate: true },
)

watch(
  () => [keyboardHeightPx.value, inputBarHeightPx.value] as const,
  () => {
    if (!IS_MP_WEIXIN) return
    if (!isPinnedToBottom.value) return
    scrollToBottom()
  },
)

watch(
  () => [isTeamMode.value, debateStore.currentTurnId] as const,
  ([teamMode, turnId], previous) => {
    const previousTeamMode = previous?.[0] ?? false
    const previousTurnId = previous?.[1] ?? null
    if (!teamMode) {
      desktopTeamRailOpen.value = false
      return
    }
    if (turnId && (turnId !== previousTurnId || !previousTeamMode)) {
      openDesktopTeamRail()
    }
  },
  { immediate: true },
)

watch(
  () => debateStore.isActive,
  (active) => {
    if (!active) {
      desktopTeamRailOpen.value = false
    }
  },
)

watch(
  () => appStore.screenWidth,
  (width) => {
    if (width < TEAM_RAIL_DESKTOP_BREAKPOINT) {
      desktopTeamRailOpen.value = false
    }
  },
)

function replaceCompareRounds(rounds: CompareRound[]) {
  compareRounds.splice(0, compareRounds.length, ...rounds)
}

function inferCompareModelIdsFromMessages() {
  const modelIds = new Set<string>()
  for (const round of buildCompareRoundsFromMessages(chatStore.messages)) {
    for (const response of round.responses) {
      modelIds.add(response.modelId)
    }
  }
  return [...modelIds]
}

function persistSingleSession(title?: string) {
  if (!chatStore.currentConversationId || chatStore.conversationScope === 'temporary') return
  chatStore.touchConversation(chatStore.currentConversationId, title, {
    bumpUpdatedAt: false,
    mode: 'chat',
    compareModelIds: null,
    captainMode: null,
  })
  writeActiveChatSession({
    conversationId: chatStore.currentConversationId,
    mode: 'single',
    title,
  })
}

function persistCompareSession(modelIds: string[], title?: string) {
  if (!chatStore.currentConversationId || chatStore.conversationScope === 'temporary') return
  writeActiveChatSession({
    conversationId: chatStore.currentConversationId,
    mode: 'compare',
    multiModelIds: [...modelIds],
    title,
  })
}

function persistTeamSession(conversationId: string, modelIds: string[], title?: string) {
  writeActiveChatSession({
    conversationId,
    mode: 'team',
    multiModelIds: [...modelIds],
    captainMode: captainMode.value,
    title,
  })
}

watch(
  () => [chatMode.value, chatStore.currentConversationId, chatStore.messages.length, isComparingGeneration.value] as const,
  ([mode, conversationId, messageCount, comparing]) => {
    if (mode !== 'compare') {
      if (!conversationId && compareRounds.length > 0) {
        replaceCompareRounds([])
      }
      return
    }
    if (comparing) return
    if (!conversationId || messageCount === 0) {
      replaceCompareRounds([])
      return
    }
    replaceCompareRounds(buildCompareRoundsFromMessages(chatStore.messages))
  },
  { immediate: true },
)

onMounted(async () => {
  // Read agent params from URL (from agent market navigation)
  const routeOptions = getCurrentPageRouteOptions()
  if (routeOptions.agentId) {
    applyRouteAgent(
      routeOptions.agentId,
      routeOptions.agentName ? decodeURIComponent(routeOptions.agentName) : null,
    )
    chatStore.startConversationDraft('persistent')
    clearActiveChatSession()
    sessionRestoreResolved.value = true
    return
  }

  const session = readActiveChatSession()
  if (!session) {
    sessionRestoreResolved.value = true
    return
  }

  if (session.mode === 'team') {
    const restoredModelIds = session.multiModelIds?.length
      ? [...session.multiModelIds]
      : [...debateStore.selectedModelIds]
    const previousDebateState = debateStore.snapshotState()
    try {
      await recoverDebate(session.conversationId)
    } catch {
      debateStore.restoreState(previousDebateState)
      clearActiveChatSession()
      sessionRestoreResolved.value = true
      return
    }
    chatStore.clearMessages()
    if (restoredModelIds.length > 0) {
      multiModelIds.value = restoredModelIds
    }
    captainMode.value = session.captainMode || 'auto'
    chatMode.value = 'team'
    syncActiveTeamConversation(session.conversationId)
    chatStore.touchConversation(session.conversationId, session.title || '[团队]', {
      bumpUpdatedAt: false,
      mode: 'team',
      compareModelIds: restoredModelIds,
      captainMode: session.captainMode,
    })
    sessionRestoreResolved.value = true
    return
  }

  if (
    chatStore.currentConversationId !== session.conversationId
    || chatStore.messages.length === 0
  ) {
    const ok = await chatStore.loadConversation(session.conversationId)
    if (!ok) {
      clearActiveChatSession()
      sessionRestoreResolved.value = true
      return
    }
    loadAssistantVersions()
  }

  if (session.mode === 'compare') {
    const restoredModelIds = session.multiModelIds?.length
      ? [...session.multiModelIds]
      : inferCompareModelIdsFromMessages()
    if (restoredModelIds.length > 1) {
      multiModelIds.value = restoredModelIds
      chatStore.touchConversation(session.conversationId, session.title, {
        bumpUpdatedAt: false,
        mode: 'compare',
        compareModelIds: restoredModelIds,
      })
      chatMode.value = 'compare'
      sessionRestoreResolved.value = true
      return
    }
  }

  chatMode.value = 'single'
  sessionRestoreResolved.value = true
})

watch(
  () => [
    chatMode.value,
    captainMode.value,
    multiModelIds.value.join('|'),
    chatStore.currentConversationId,
    debateStore.conversationId,
    chatStore.conversationScope,
    debateStore.isActive,
  ] as const,
  () => {
    if (chatStore.conversationScope === 'temporary') return
    if (chatMode.value === 'team') {
      if (!debateStore.isActive) return
      const conversationId = debateStore.conversationId || chatStore.currentConversationId
      if (!conversationId) return
      const modelIds = [...multiModelIds.value]
      chatStore.touchConversation(conversationId, resolveConversationTitle(conversationId) || '[团队]', {
        bumpUpdatedAt: false,
        mode: 'team',
        compareModelIds: modelIds,
        captainMode: captainMode.value,
      })
      persistTeamSession(conversationId, modelIds, resolveConversationTitle(conversationId))
      return
    }
    if (!chatStore.currentConversationId) return
    if (chatMode.value === 'compare' && multiModelIds.value.length > 1) {
      const modelIds = [...multiModelIds.value]
      chatStore.touchConversation(
        chatStore.currentConversationId,
        resolveConversationTitle(chatStore.currentConversationId),
        {
          bumpUpdatedAt: false,
          mode: 'compare',
          compareModelIds: modelIds,
        },
      )
      persistCompareSession(modelIds, resolveConversationTitle(chatStore.currentConversationId))
      return
    }
    persistSingleSession(resolveConversationTitle(chatStore.currentConversationId))
  },
)

function showError(msg: string) {
  uni.showToast({ title: msg, icon: 'none', duration: 2500 })
}

function formatCreateConversationError(error: unknown): string {
  try {
    if (!error) return '会话创建失败：未知错误'
    if (typeof error === 'string') return `会话创建失败：${error}`
    const obj = error as Record<string, unknown>
    const code = obj?.code
    const message = obj?.message
    if (typeof code === 'number' && code !== 0) {
      return `会话创建失败(${code})：${message || '服务端错误'}`
    }
    if (typeof message === 'string' && message) {
      return `会话创建失败：${message}`
    }
    const errMsg = (obj as { errMsg?: string })?.errMsg
    if (typeof errMsg === 'string' && errMsg) {
      return `会话创建失败：网络错误`
    }
    return '会话创建失败'
  } catch {
    return '会话创建失败'
  }
}

function createClientTraceId(): string {
  // Simple, collision-resistant-enough client trace id. Backend is still the source of truth.
  const rand = Math.random().toString(36).slice(2, 10)
  return `t_${Date.now().toString(36)}_${rand}`
}

function showMessageErrorDetails(message: Message) {
  const traceId = (message.traceId || '').trim()
  const text = (message.content || '').trim()
  const contentLines: string[] = []
  if (text) contentLines.push(text)
  if (traceId) contentLines.push(`traceId: ${traceId}`)
  const content = contentLines.join('\n') || '生成失败'

  uni.showModal({
    title: '错误详情',
    content,
    showCancel: Boolean(traceId),
    confirmText: traceId ? '复制traceId' : '确定',
    cancelText: '关闭',
    success(res) {
      if (!traceId) return
      if (!res.confirm) return
      uni.setClipboardData({
        data: traceId,
        success: () => uni.showToast({ title: '已复制', icon: 'none', duration: 1500 }),
        fail: () => uni.showToast({ title: '复制失败', icon: 'none', duration: 1500 }),
      })
    },
  })
}

function handleCompareCopy(content: string, event?: CompatEventLike) {
  runCompatAction(`compare-copy:${content.slice(0, 32)}`, event, () => {
    copyContent(content)
  })
}

function editContent(content: string) {
  inputContent.value = content
  uni.showToast({ title: '已填入输入框', icon: 'none', duration: 1500 })
}

function copyContent(content: string) {
  uni.setClipboardData({
    data: content,
    success: () => uni.showToast({ title: '已复制', icon: 'success', duration: 1500 }),
    fail: () => uni.showToast({ title: '复制失败', icon: 'none', duration: 1500 }),
  })
}

function switchMessageVersion(msg: Message, index: number) {
  chatStore.switchMessageVersion(msg, index)
}

function loadAssistantVersions() {
  for (const msg of chatStore.messages) {
    if (msg.role === 'assistant' && !msg.versionList) {
      chatStore.loadMessageVersions(msg)
    }
  }
}

// -- Single mode generation --
async function startGeneration(userMessage: Message, assistantMessage: Message, sendAttachments?: import('@/api/types/chat').ChatAttachment[]) {
  chatStore.isGenerating = true
  stoppedByUser.value = false
  const conversationTitle = buildConversationTitle(userMessage.content)
  try {
    await chatStore.ensureConversation(conversationTitle)
    await aiContextStore.syncConversationKnowledgeSelection(chatStore.currentConversationId)
    persistSingleSession(conversationTitle)
    await chatStore.persistMessage(userMessage)
  } catch (error) {
    chatStore.isGenerating = false
    assistantMessage.status = 'error'
    const errorDetail = formatCreateConversationError(error)
    console.error('[chat] ensureConversation failed:', error, errorDetail)
    showError(errorDetail)
    return
  }

  const payload = chatStore.messages
    .filter(m => m.id !== assistantMessage.id && m.status !== 'error')
    .map(m => {
      const msg: { role: string; content: string; attachments?: import('@/api/types/chat').ChatAttachment[] } = { role: m.role, content: m.content }
      // Attach attachments to the last user message
      if (sendAttachments && sendAttachments.length > 0 && m.id === userMessage.id) {
        msg.attachments = sendAttachments
      }
      return msg
    })

  const requestPayload = buildChatRequestPayload(
    chatStore.selectedModel,
    payload,
    chatRuntimePayloadOptions.value,
  )

  if (IS_MP_WEIXIN) {
    const wsReadyOk = await ensureChatWsReady()
    if (wsReadyOk) {
      let finished = false
      let cleanup: (() => void) | null = null
      let timeoutTimer: ReturnType<typeof setTimeout> | null = null

      const stopTimers = () => {
        if (timeoutTimer) {
          clearTimeout(timeoutTimer)
          timeoutTimer = null
        }
      }

      const finish = () => {
        if (finished) return
        finished = true
        stopTimers()
        cleanup?.()
        cleanup = null
      }

      timeoutTimer = setTimeout(() => {
        if (finished) return
        const hasAny = Boolean(assistantMessage.content.trim() || assistantMessage.reasoningContent?.trim())
        assistantMessage.status = hasAny ? 'success' : 'error'
        chatStore.isGenerating = false
        streamAbort.value = null
        finish()
        if (!hasAny) {
          assistantMessage.content = '请求超时，请稍后重试'
          showError('请求超时，请稍后重试')
        }
      }, 600000)

      try {
        const clientTraceId = createClientTraceId()
        assistantMessage.traceId = clientTraceId
        let httpTraceId: string | null = null

        const started = await chatCompletions(requestPayload, {
          traceId: clientTraceId,
          onTraceId: (traceId) => {
            httpTraceId = traceId
            assistantMessage.traceId = traceId
          },
        })
        const requestId = started.data?.requestId
        if (!requestId) {
          throw new Error('missing requestId')
        }
        assistantMessage.requestId = requestId
        if (httpTraceId) {
          assistantMessage.traceId = httpTraceId
        }

        cleanup = registerChatWsHandlers(requestId, {
          onTrace: (traceId) => {
        assistantMessage.traceId = traceId
      },
      onDelta: (delta) => {
        if (finished) return
        applyStreamingAssistantDelta(
          chatStore.messages,
          { requestId, delta, traceId: assistantMessage.traceId ?? null, model: assistantMessage.model ?? null },
          { isClosedRequestId: isChatRequestClosed },
        )
      },
      onThinking: (delta) => {
        if (finished) return
        applyStreamingAssistantThinking(
          chatStore.messages,
          { requestId, delta, traceId: assistantMessage.traceId ?? null, model: assistantMessage.model ?? null },
          { isClosedRequestId: isChatRequestClosed },
        )
      },
      onError: (message) => {
        if (finished) return
        assistantMessage.status = 'error'
        if (!assistantMessage.content.trim()) {
              assistantMessage.content = message?.trim() ? message.trim() : '生成失败'
            }
            chatStore.isGenerating = false
            streamAbort.value = null
            finish()
            showError(message || '生成失败')
          },
          onNotice: (message) => {
            if (finished) return
            if (message) {
              uni.showToast({ title: message, icon: 'none', duration: 2500 })
            }
          },
          onDone: () => {
            if (finished) return
            chatStore.finishLastAssistantReasoning()
            assistantMessage.status = assistantMessage.content || assistantMessage.reasoningContent ? 'success' : 'error'
            chatStore.isGenerating = false
            streamAbort.value = null
            finish()
            if (!assistantMessage.content.trim()) {
              if (!assistantMessage.reasoningContent?.trim()) {
                assistantMessage.content = '未收到回复，请稍后重试'
                showError('未收到回复，请稍后重试')
              }
              return
            }
            void chatStore.persistMessage(assistantMessage).catch(() => undefined)
          },
        })

        streamAbort.value = () => {
          if (finished) return
          stoppedByUser.value = true
          // Best-effort notify backend; no-op if already sent or socket is down.
          ws.sendChatAbort?.(requestId, 'user_cancel')
          assistantMessage.status = assistantMessage.content || assistantMessage.reasoningContent ? 'success' : 'error'
          chatStore.isGenerating = false
          streamAbort.value = null
          finish()
        }
        return
      } catch (error: unknown) {
        stopTimers()
        cleanup?.()
        cleanup = null
        // Fall through to compat mode fallback.
      }
    }

    try {
      const compat = await chatCompat(requestPayload)
      assistantMessage.content = compat?.message?.content ?? ''
      assistantMessage.status = assistantMessage.content ? 'success' : 'error'
      chatStore.isGenerating = false
      streamAbort.value = null
      if (assistantMessage.content.trim()) {
        void chatStore.persistMessage(assistantMessage).catch(() => undefined)
      }
      return
    } catch (error: unknown) {
      assistantMessage.status = 'error'
      if (!assistantMessage.content.trim()) {
        assistantMessage.content = extractErrorMessage(error, '生成失败')
      }
      chatStore.isGenerating = false
      streamAbort.value = null
      showError(extractErrorMessage(error, '生成失败'))
      return
    }
  }

  const abort = fetchSse({
    url: CHAT_SSE_URL,
    data: requestPayload,
    onEvent: (event, data) => {
      if (event === 'chat.delta') {
        try {
          const parsed = JSON.parse(data)
          const chunk = typeof parsed?.delta === 'string' ? parsed.delta : ''
          if (chunk) chatStore.updateLastAssistantMessage(chunk)
        } catch { /* skip */ }
      }
      if (event === 'chat.thinking') {
        try {
          const parsed = JSON.parse(data)
          const chunk = typeof parsed?.delta === 'string' ? parsed.delta : ''
          if (chunk) chatStore.updateLastAssistantReasoning(chunk)
        } catch { /* skip */ }
      }
      if (event === 'chat.error') { assistantMessage.status = 'error' }
    },
    onError: () => {
      if (!stoppedByUser.value) { assistantMessage.status = 'error' }
      chatStore.isGenerating = false
      streamAbort.value = null
    },
    onDone: () => {
      chatStore.finishLastAssistantReasoning()
      assistantMessage.status = assistantMessage.content ? 'success' : 'error'
      chatStore.isGenerating = false
      streamAbort.value = null
      if (!assistantMessage.content.trim()) {
        return
      }
      void chatStore.persistMessage(assistantMessage).catch(() => undefined)
    },
  })
  streamAbort.value = abort
}

function stopGeneration() {
  stoppedByUser.value = true
  if (isTeamMode.value) {
    clearActiveChatSession()
    endDebate()
    return
  }
  if (isCompareMode.value) {
    const aborts = [...compareAborts.value]
    compareAborts.value = []
    if (aborts.length === 0) {
      isComparingGeneration.value = false
      return
    }
    aborts.forEach(fn => fn?.())
  } else {
    streamAbort.value?.()
    streamAbort.value = null
    const last = chatStore.messages[chatStore.messages.length - 1]
    if (last?.role === 'assistant') last.status = last.content ? 'success' : 'error'
    chatStore.isGenerating = false
  }
}

function onTranscription(text: string) {
  if (isSingleMode.value) {
    void handleSend(text)
  } else {
    const existing = inputContent.value.trim()
    inputContent.value = existing ? existing + ' ' + text : text
  }
}

// -- Compare mode: N parallel SSE streams, Arena-style rounds --
function getModelName(modelId: string) {
  return modelId
}

const handleCompareSend = async (text: string) => {
  if (isComparingGeneration.value) return
  const ids = normalizeMultiModelIds(
    multiModelIds.value,
    modelStore.models.map((model) => model.id),
  )
  if (ids.length !== multiModelIds.value.length) {
    showError('请至少选择两个不同且可用的模型'); return
  }
  if (ids.length < 2 || ids.length !== multiModelIds.value.length) {
    showError('请至少选择两个模型'); return
  }

  isComparingGeneration.value = true
  stoppedByUser.value = false

  // Create a new round with reactive responses
  const roundData: CompareRound = {
    id: Date.now(),
    userContent: text,
    responses: ids.map(id => ({
      modelId: id,
      modelName: getModelName(id),
      content: '',
      status: 'sending' as const,
    })),
  }
  compareRounds.push(roundData)
  // CRITICAL: get the reactive proxy back from the array; the original
  // `roundData` is a plain object whose mutations won't trigger re-renders.
  const round = compareRounds[compareRounds.length - 1]

  const userMessage: Message = {
    id: Date.now().toString(),
    role: 'user',
    content: text,
    createdAt: Date.now(),
    model: ids[0],
    status: 'success',
  }

  const conversationTitle = buildConversationTitle(text)
  try {
    await chatStore.ensureConversation(conversationTitle, { compareModelIds: ids })
    await aiContextStore.syncConversationKnowledgeSelection(chatStore.currentConversationId)
    persistCompareSession(ids, conversationTitle)
    await chatStore.persistMessage(userMessage)
    if (!chatStore.messages.some((existing) => existing.id === userMessage.id)) {
      chatStore.addMessage(userMessage)
    }
  } catch (error) {
    for (const response of round.responses) {
      response.status = 'error'
    }
    isComparingGeneration.value = false
    const errorDetail = formatCreateConversationError(error)
    console.error('[chat] ensureConversation (compare) failed:', error, errorDetail)
    showError(errorDetail)
    return
  }

  let doneCount = 0
  const aborts: Array<(() => void) | null> = []
  let compareRoundCommitted = false
  const persistedAssistantIds = new Set<string>()

  const upsertCompareStoreMessage = (message: Message) => {
    const existing = chatStore.messages.find((item) => item.id === message.id)
    if (existing) {
      existing.content = message.content
      existing.status = message.status
      existing.model = message.model
      existing.createdAt = message.createdAt
      if (message.requestId) {
        existing.requestId = message.requestId
      }
      if (message.traceId) {
        existing.traceId = message.traceId
      }
      return existing
    }
    chatStore.addMessage(message)
    return message
  }

  const persistCompareAssistantMessage = (
    modelId: string,
    response: CompareResponse,
    options: {
      requestId?: string
      traceId?: string
    } = {},
  ) => {
    const assistantMessage = upsertCompareStoreMessage({
      id: `${round.id}-${modelId}`,
      role: 'assistant',
      content: response.content,
      createdAt: Date.now(),
      model: modelId,
      status: response.status === 'error' ? 'error' : 'success',
      requestId: options.requestId,
      traceId: options.traceId,
    })
    if (!assistantMessage.content.trim()) {
      return assistantMessage
    }
    if (persistedAssistantIds.has(assistantMessage.id)) {
      return assistantMessage
    }
    persistedAssistantIds.add(assistantMessage.id)
    void chatStore.persistMessage(assistantMessage).catch(() => {
      persistedAssistantIds.delete(assistantMessage.id)
    })
    return assistantMessage
  }

  const finalizeCompareRound = () => {
    if (compareRoundCommitted || doneCount < ids.length) {
      return
    }
    compareRoundCommitted = true
    for (const message of buildCompareRoundMessages(userMessage, round)) {
      upsertCompareStoreMessage(message)
    }
    isComparingGeneration.value = false
  }

  if (IS_MP_WEIXIN) {
    const wsReadyOk = await ensureChatWsReady()
    if (wsReadyOk) {
       const runCompatCompareModel = async (modelId: string, resp: CompareResponse) => {
          try {
            const payload = buildComparePayloadForModel(compareRounds, modelId, round.id, text)
            const result = await chatCompat(buildChatRequestPayload(modelId, payload, chatRuntimePayloadOptions.value))
            resp.content = result?.message?.content ?? ''
            resp.status = resp.content ? 'success' : 'error'
           persistCompareAssistantMessage(modelId, resp)
        } catch (error: unknown) {
          resp.status = 'error'
          if (!resp.content.trim()) {
            resp.content = extractErrorMessage(error, '生成失败')
          }
        } finally {
          doneCount++
          finalizeCompareRound()
        }
      }

      const wsRuns = ids.map(async (modelId) => {
        const resp = round.responses.find((r) => r.modelId === modelId)
        if (!resp) {
          doneCount++
          finalizeCompareRound()
          return
        }

        let finished = false
        let timeoutTimer: ReturnType<typeof setTimeout> | null = null
        let cleanup: (() => void) | null = null

        const stopTimer = () => {
          if (timeoutTimer) {
            clearTimeout(timeoutTimer)
            timeoutTimer = null
          }
        }

        const finish = () => {
          if (finished) return
          finished = true
          stopTimer()
          cleanup?.()
          cleanup = null
        }

        try {
          const payload = buildComparePayloadForModel(compareRounds, modelId, round.id, text)
          const clientTraceId = createClientTraceId()
          let httpTraceId: string | null = null
          let streamTraceId: string | null = null

          const started = await chatCompletions(
            buildChatRequestPayload(modelId, payload, chatRuntimePayloadOptions.value),
            {
              traceId: clientTraceId,
              onTraceId: (traceId) => {
                httpTraceId = traceId
              },
            },
          )
          const requestId = started.data?.requestId
          if (!requestId) {
            throw new Error('missing requestId')
          }

           timeoutTimer = setTimeout(() => {
             if (finished) return
            const hasContent = Boolean(resp.content.trim())
            if (!hasContent) {
              resp.content = '请求超时，请稍后重试'
            }
            resp.status = hasContent ? 'success' : 'error'
             doneCount++
             finish()
             finalizeCompareRound()
           }, 600000)

           cleanup = registerChatWsHandlers(requestId, {
             onTrace: (traceId) => {
               streamTraceId = traceId
             },
             onDelta: (delta) => {
               if (finished) return
               if (delta) resp.content += delta
             },
            onError: (message) => {
              if (finished) return
              resp.status = 'error'
              if (!resp.content.trim()) {
                resp.content = message?.trim() ? message.trim() : '生成失败'
              }
              doneCount++
              finish()
              finalizeCompareRound()
            },
            onDone: () => {
              if (finished) return
              resp.status = resp.content ? 'success' : 'error'
              persistCompareAssistantMessage(modelId, resp, {
                requestId,
                traceId: streamTraceId || httpTraceId || clientTraceId,
              })
              doneCount++
              finish()
              finalizeCompareRound()
            },
          })

          aborts.push(() => {
            if (finished) return
            resp.status = resp.content ? 'success' : 'error'
            persistCompareAssistantMessage(modelId, resp, {
              requestId,
              traceId: streamTraceId || httpTraceId || clientTraceId,
            })
            doneCount++
            finish()
            finalizeCompareRound()
          })
        } catch {
          stopTimer()
          cleanup?.()
          cleanup = null
          await runCompatCompareModel(modelId, resp)
        }
      })

      await Promise.allSettled(wsRuns)
      compareAborts.value = aborts
      return
    }

    const compatRuns = ids.map(async (modelId) => {
      const resp = round.responses.find((r) => r.modelId === modelId)
      if (!resp) return
      try {
        const payload = buildComparePayloadForModel(compareRounds, modelId, round.id, text)
        const result = await chatCompat(buildChatRequestPayload(modelId, payload, chatRuntimePayloadOptions.value))
        resp.content = result?.message?.content ?? ''
        resp.status = resp.content ? 'success' : 'error'
        persistCompareAssistantMessage(modelId, resp)
      } catch {
        resp.status = 'error'
      } finally {
        doneCount++
        finalizeCompareRound()
      }
    })

    await Promise.allSettled(compatRuns)
    return
  }

  for (let i = 0; i < ids.length; i++) {
    const modelId = ids[i]
    // resp is from the reactive proxy, so mutations trigger re-renders
    const resp = round.responses[i]
    const payload = buildComparePayloadForModel(compareRounds, modelId, round.id, text)
    let finished = false

    const finishCompareRun = () => {
      if (finished) return
      finished = true
      doneCount++
      finalizeCompareRound()
    }

    const abort = fetchSse({
      url: CHAT_SSE_URL,
      data: buildChatRequestPayload(modelId, payload, chatRuntimePayloadOptions.value),
      onEvent: (event, data) => {
        if (finished) return
        if (event === 'chat.delta') {
          try {
            const parsed = JSON.parse(data)
            const chunk = typeof parsed?.delta === 'string' ? parsed.delta : ''
            if (chunk) { resp.content += chunk }
          } catch { /* skip */ }
        }
        if (event === 'chat.error') { resp.status = 'error' }
      },
      onError: () => {
        if (finished) return
        if (!stoppedByUser.value) resp.status = 'error'
        finishCompareRun()
      },
      onDone: () => {
        if (finished) return
        resp.status = resp.content ? 'success' : 'error'
        persistCompareAssistantMessage(modelId, resp)
        finishCompareRun()
      },
    })
    aborts.push(() => {
      if (finished) return
      resp.status = resp.content ? 'success' : 'error'
      persistCompareAssistantMessage(modelId, resp)
      abort?.()
      finishCompareRun()
    })
  }
  compareAborts.value = aborts
}

// -- Team mode send --
const handleTeamSend = async (text: string) => {
  if (debateStore.isLoading || debateStore.isInProgress) return
  if (chatStore.conversationScope === 'temporary') {
    showError('临时对话暂不支持团队模式')
    return
  }
  if (multiModelIds.value.length < 2) { showError('团队模式至少需要 2 个模型'); return }

  try {
    openDesktopTeamRail()
    if (debateStore.hasActiveSession) {
      await continueDebate(text)
    } else {
      await startDebate(text, multiModelIds.value, captainMode.value)
    }
    // Register team conversation in sidebar history
    if (debateStore.conversationId) {
      syncActiveTeamConversation(debateStore.conversationId)
      const title = text.length > 30 ? text.slice(0, 30) + '...' : text
      chatStore.touchConversation(debateStore.conversationId, '[团队] ' + title, {
        mode: 'team',
        compareModelIds: [...multiModelIds.value],
        captainMode: captainMode.value,
      })
      persistTeamSession(debateStore.conversationId, [...multiModelIds.value], `[团队] ${title}`)
    }
  } catch (error: unknown) {
    showError(extractErrorMessage(error, '团队对话启动失败'))
  }
}

const handleValidatedTeamSend = async (text: string) => {
  if (debateStore.isLoading || debateStore.isInProgress) return
  if (chatStore.conversationScope === 'temporary') {
    showError('临时对话暂不支持团队模式')
    return
  }
  const ids = normalizeMultiModelIds(
    multiModelIds.value,
    modelStore.models.map((model) => model.id),
  )
  if (ids.length !== multiModelIds.value.length || ids.length < 2) {
    showError('团队模式至少需要 2 个不同且可用的模型')
    return
  }

  try {
    openDesktopTeamRail()
    if (debateStore.hasActiveSession) {
      await continueDebate(text)
    } else {
      await startDebate(text, ids, captainMode.value)
    }
    if (debateStore.conversationId) {
      syncActiveTeamConversation(debateStore.conversationId)
      const title = text.length > 30 ? text.slice(0, 30) + '...' : text
      chatStore.touchConversation(debateStore.conversationId, '[团队] ' + title, {
        mode: 'team',
        compareModelIds: [...ids],
        captainMode: captainMode.value,
      })
      persistTeamSession(debateStore.conversationId, [...ids], `[团队] ${title}`)
    }
  } catch (error: unknown) {
    showError(extractErrorMessage(error, '团队对话启动失败'))
  }
}

const handleSend = async (customText?: string) => {
  if (attachments.uploading.value) {
    showError('附件仍在上传中，请稍后发送')
    return
  }

  const attempt = resolveChatSendAttempt({
    customText,
    draftText: inputContent.value,
    hasAttachments: attachments.hasAttachments.value,
    attachmentOnlyText: ATTACHMENT_ONLY_PROMPT,
    isTeamMode: isTeamMode.value,
    isCompareMode: isCompareMode.value,
    isGenerating: chatStore.isGenerating,
    isModelLoading: modelStore.loading,
    isComparingGeneration: isComparingGeneration.value,
    isDebateBusy: debateStore.isLoading || debateStore.isInProgress,
    isTemporaryConversation: isTemporaryConversation.value,
    selectedModel: chatStore.selectedModel,
    availableModelIds: modelStore.models.map((model) => model.id),
    multiModelIds: multiModelIds.value,
  })

  if (attempt.kind === 'noop') return
  if (attempt.kind === 'blocked') {
    if (attempt.error) {
      showError(attempt.error)
    }
    return
  }

  if (attachments.hasAttachments.value && attempt.kind !== 'single') {
    showError('附件识别暂支持单模型聊天，请切换到单模型后发送')
    return
  }

  if (attempt.kind === 'team') return handleValidatedTeamSend(attempt.text)
  if (attempt.kind === 'compare') return handleCompareSend(attempt.text)

  const text = attempt.text

  // Check image capability before sending
  if (attachments.hasImageAttachments.value) {
    const currentModel = modelStore.models.find(m => m.id === chatStore.selectedModel)
    if (currentModel && currentModel.supportsImageParsing !== true) {
      showError(`model ${currentModel.name} does not support image parsing, please remove images or switch model`)
      return
    }
  }

  inputContent.value = ''

  const userContent = webReadContext.value
    ? `${webReadContext.value}\n\n${text}`
    : text

  // Capture attachments before clearing
  const sendAttachments = attachments.hasAttachments.value
    ? attachments.toRequestAttachments()
    : undefined

  const userMessage: Message = {
    id: Date.now().toString(), role: 'user', content: userContent,
    createdAt: Date.now(), model: chatStore.selectedModel, status: 'success',
    ...(sendAttachments ? { attachments: sendAttachments } : {}),
  }
  chatStore.addMessage(userMessage)

  // Clear web read context after injecting into message
  if (webReadContext.value) {
    webReadContext.value = ''
    webReadStatus.value = 'idle'
  }

  // Clear attachments after capturing
  if (sendAttachments) {
    attachments.clearAttachments()
  }

  const assistantMessage: Message = {
    id: (Date.now() + 1).toString(), role: 'assistant', content: '',
    createdAt: Date.now(), model: chatStore.selectedModel, status: 'sending',
  }
  chatStore.addMessage(assistantMessage)

  jumpToLatest()
  await startGeneration(userMessage, assistantMessage, sendAttachments)
}

const handlePromptClick = (prompt: ChatPrompt) => { void handleSend(prompt.desc) }
const handleDebateContinue = (message: string) => { void handleValidatedTeamSend(message) }

const isGeneratingOrDebating = computed(() => {
  if (isTeamMode.value) return debateStore.isInProgress || debateStore.isLoading
  if (isCompareMode.value) return isComparingGeneration.value
  return chatStore.isGenerating
})
</script>

<template>
  <AppLayout>
    <view class="chat-container" :style="chatContainerStyle">
      <template v-if="isSingleMode">
        <view v-if="effectiveAgentName && isLanding" class="agent-banner">
          <view class="agent-banner-icon">{{ agentBannerInitial }}</view>
          <view class="agent-banner-info">
            <text class="agent-banner-label">{{ agentBannerLabel }}</text>
            <text class="agent-banner-name">{{ effectiveAgentName }}</text>
          </view>
          <view class="agent-banner-close" @click="handleClearActiveAgent" @tap="handleClearActiveAgent">
            <text>x</text>
          </view>
        </view>
        <ChatLandingPrompts
          v-if="isLanding"
          :prompts="SUGGESTED_PROMPTS"
          @select="handlePromptClick"
        />
        <ChatMessageList
          v-else
          :messages="chatStore.messages"
          :is-generating="chatStore.isGenerating"
          :content-bottom-padding-px="contentBottomPaddingPx"
          :scroll-into-view="scrollIntoView"
          :scroll-top="scrollTop"
          :viewport-height-px="chatViewportHeightPx"
          :model-display-name="modelDisplayName"
          :model-avatar-path="modelAvatarPath"
          @scroll="onMessageScroll"
          @scrolltolower="onScrollToLower"
          @user-scroll-intent="handleMessageUserScrollIntent"
          @edit="editContent"
          @copy="copyContent"
          @switch-version="switchMessageVersion"
          @error-details="showMessageErrorDetails"
        />
      </template>

      <template v-else-if="isCompareMode">
        <ChatLandingPrompts
          v-if="isLanding"
          :prompts="SUGGESTED_PROMPTS"
          @select="handlePromptClick"
        />
        <ChatMessageList
          v-else-if="showCompareMessageFallback"
          :messages="chatStore.messages"
          :is-generating="isComparingGeneration"
          :content-bottom-padding-px="contentBottomPaddingPx"
          :scroll-into-view="scrollIntoView"
          :scroll-top="scrollTop"
          :viewport-height-px="chatViewportHeightPx"
          :model-display-name="modelDisplayName"
          :model-avatar-path="modelAvatarPath"
          @scroll="onMessageScroll"
          @scrolltolower="onScrollToLower"
          @user-scroll-intent="handleMessageUserScrollIntent"
          @edit="editContent"
          @copy="copyContent"
          @switch-version="switchMessageVersion"
          @error-details="showMessageErrorDetails"
        />
        <scroll-view
          v-else
          scroll-y
          enable-flex
          class="compare-scroll"
          :style="chatScrollViewportStyle"
          :scroll-top="scrollTop"
          :lower-threshold="90"
          scroll-with-animation
          @scroll="onMessageScroll"
          @scrolltolower="onScrollToLower"
          @touchmove="handleMessageUserScrollIntent"
        >
          <view class="compare-rounds" :style="{ paddingBottom: contentBottomPaddingPx + 'px' }">
            <view v-for="round in compareRounds" :key="round.id" class="compare-round">
              <view class="compare-user-row">
                <view class="compare-user-bubble">
                  <text class="compare-user-text">{{ round.userContent }}</text>
                </view>
              </view>
              <view class="compare-cards-row">
                <view v-for="(resp, idx) in round.responses" :key="idx" class="compare-card">
                  <view class="compare-card-header">
                    <text class="compare-card-model">{{ resp.modelName }}</text>
                    <view class="compare-card-actions">
                      <view
                        v-if="resp.status === 'success'"
                        class="compare-card-action"
                        @click="handleCompareCopy(resp.content, $event)"
                        @tap="handleCompareCopy(resp.content, $event)"
                      >
                        <!-- #ifdef MP-WEIXIN -->
                        <MpShapeIcon name="copy" :size="14" color="var(--app-text-secondary)" :stroke-width="2" />
                        <!-- #endif -->
                        <!-- #ifndef MP-WEIXIN -->
                        <svg
                          width="14"
                          height="14"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="var(--app-text-secondary)"
                          stroke-width="2"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                        >
                          <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                        </svg>
                        <!-- #endif -->
                      </view>
                    </view>
                  </view>
                  <view class="compare-card-body">
                    <text v-if="resp.status === 'sending' && !resp.content" class="compare-card-loading">...</text>
                    <text v-else-if="resp.status === 'error' && !resp.content" class="compare-card-error">请求失败</text>
                    <text v-else class="compare-card-content">{{ resp.content }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>
      </template>

      <template v-else>
        <ChatLandingPrompts
          v-if="isLanding"
          :prompts="SUGGESTED_PROMPTS"
          @select="handlePromptClick"
        />
        <DebatePanel
          v-else
          :desktop-rail-open="desktopTeamRailOpen"
          @continue="handleDebateContinue"
          @update:desktop-rail-open="desktopTeamRailOpen = $event"
        />
      </template>

      <ChatInputBar
        ref="chatInputBarRef"
        v-model="inputContent"
        :is-generating="isGeneratingOrDebating"
        :keyboard-height-px="inputBarBottomOffsetPx"
        :safe-bottom-px="inputBarSafeBottomPx"
        :web-read-status="webReadStatus"
        :can-send="canSendCurrentInput"
        :show-agent-selector="true"
        :agent-summary="agentControlSummary"
        :agent-options="agentSelectorOptions"
        :selected-agent-id="activeAgentId"
        :agent-loading="userPreferencesStore.loading || userPreferencesStore.savedAssetsLoading.AGENT"
        :can-clear-agent="Boolean(activeAgentId)"
        :agent-locked="isAgentSelectionLocked"
        agent-locked-message="当前会话的智能体已锁定，如需切换请新建会话"
        @height-change="inputBarHeightPx = $event"
        @send="handleSend()"
        @stop="stopGeneration"
        @transcription="onTranscription"
        @audio-error="showError"
        @web-read-request="handleWebReadRequest"
        @web-read-clear="handleWebReadClear"
        @upload-files="attachments.uploadWebFiles($event)"
        @pick-attachment="attachments.pickAndUploadAttachment()"
        @pick-image="attachments.pickAndUploadImage()"
        @pick-document="attachments.pickAndUploadDocument()"
        @select-agent="handleAgentSelect"
        @clear-agent="handleClearActiveAgent"
        @open-agent-market="openAgentMarket"
      >
        <view v-if="attachments.hasAttachments.value" class="output-format-row">
          <view
            v-for="option in outputFormatOptions"
            :key="option.id"
            class="output-format-option"
            :class="{ 'output-format-option-active': selectedOutputFormat === option.id }"
            @click="selectOutputFormat(option.id)"
            @tap="selectOutputFormat(option.id)"
          >
            <text class="output-format-option-text">{{ option.label }}</text>
          </view>
        </view>
        <AttachmentList
          v-if="attachments.chipItems.value.length > 0"
          :items="attachments.chipItems.value"
          :show-remove="true"
          @remove="attachments.removeAttachment($event)"
        />
        <view v-if="attachments.uploading.value" class="attachment-uploading-state">
          <text class="attachment-uploading-text">上传中...</text>
        </view>
        <view
          v-if="showScrollToLatest"
          class="scroll-latest-btn"
          @click="jumpToLatest"
          @tap="jumpToLatest"
        >
          <text class="scroll-latest-text">{{ newMessageCount ? newMessageCount + ' 条新消息' : '回到底部' }}</text>
        </view>
      </ChatInputBar>
    </view>

    <ChatConfigSheet
      v-model:visible="showMobileConfigSheet"
      v-model:chat-mode="chatMode"
      v-model:model-value="modelStore.selectedModelId"
      v-model:multi-model-ids="multiModelIds"
      v-model:captain-mode="captainMode"
      :models="buildAppLayoutModelOptions(modelStore.models)"
    />
  </AppLayout>
</template>

<style scoped>
.chat-container {
  --chat-content-max-width: 768px;
  --chat-content-padding-x: var(--app-space-horizontal, 20px);
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  width: 100%;
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 1024px) {
  .chat-container {
    --chat-content-padding-x: var(--app-space-horizontal, 16px);
  }
}

@media (max-width: 640px) {
  .chat-container {
    --chat-content-padding-x: var(--app-space-horizontal, 12px);
  }
}

.chat-runtime-bars {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-vertical, 8px);
  padding-bottom: var(--app-space-vertical, 8px);
}

.chat-runtime-hint {
  padding: 0 var(--app-space-horizontal, 16px);
  font-size: 12px;
  color: var(--app-text-secondary);
}

/* --- Compare mode: Arena.ai round-based layout --- */
.compare-scroll {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
}

.compare-rounds {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px 16px 150px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.compare-round {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* User message: right aligned bubble */
.compare-user-row {
  display: flex;
  justify-content: flex-end;
}

.compare-user-bubble {
  max-width: 70%;
  padding: 10px 16px;
  background: var(--app-surface-muted);
  border-radius: 18px 18px 4px 18px;
}

.compare-user-text {
  font-size: 14px;
  color: var(--app-text-primary);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Response cards: side-by-side */
.compare-cards-row {
  display: flex;
  gap: 12px;
}

.compare-card {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface);
  border-radius: 12px;
  overflow: hidden;
}

.compare-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--app-border-color-soft);
}

.compare-card-model {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.compare-card-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.compare-card-action {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 6px;
  transition: background 120ms;
}

.compare-card-action:hover {
  background: var(--app-fill-hover);
}

.compare-card-body {
  padding: 12px 14px;
}

.compare-card-content {
  font-size: 14px;
  color: var(--app-text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.compare-card-loading {
  font-size: 14px;
  color: var(--app-text-secondary);
  animation: pulse-compare 1.2s ease-in-out infinite;
}

.compare-card-error {
  font-size: 13px;
  color: var(--app-danger);
}

@keyframes pulse-compare {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
}

/* Agent banner */
.agent-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 16px 0;
  padding: 12px 16px;
  background: var(--app-surface);
  border: 1px solid var(--app-border-color);
  border-radius: 12px;
}

.agent-banner-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--app-surface-muted);
  color: var(--app-text-primary);
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.agent-banner-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.agent-banner-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--app-text-secondary);
  font-weight: 600;
}

.agent-banner-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--app-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-banner-close {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: var(--app-text-secondary);
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  transition: background 120ms;
}

.agent-banner-close:hover {
  background: var(--app-fill-hover);
}

.attachment-uploading-state {
  display: inline-flex;
  align-items: center;
  margin-top: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--app-surface-muted);
  border: 1px solid var(--app-border-color);
}

.attachment-uploading-text {
  font-size: 12px;
  line-height: 1;
  color: var(--app-text-secondary);
  font-weight: 500;
}

.output-format-row {
  display: flex;
  align-items: center;
  gap: 6px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.output-format-row::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.output-format-option {
  height: 28px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.output-format-option-active {
  border-color: rgba(249, 115, 22, 0.46);
  background: rgba(249, 115, 22, 0.12);
}

.output-format-option-text {
  font-size: 12px;
  line-height: 1;
  color: var(--app-text-secondary);
  white-space: nowrap;
}

.output-format-option-active .output-format-option-text {
  color: var(--app-text-primary);
  font-weight: 600;
}

.scroll-latest-btn {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 6px;
  left: 50%;
  bottom: calc(100% + 14px);
  transform: translateX(-50%);
  border-radius: 999px;
  border: 1px solid var(--app-border-color);
  background: var(--app-surface-raised);
  box-shadow: var(--app-shadow-elevated);
  padding: 8px 12px;
  z-index: 12;
  cursor: pointer;
  white-space: nowrap;
}

.scroll-latest-text {
  font-size: 12px;
  line-height: 1;
  color: var(--app-text-primary);
  font-weight: 600;
}

@media (max-width: 720px) {
}
</style>
