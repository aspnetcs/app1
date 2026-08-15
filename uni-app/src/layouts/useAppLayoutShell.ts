import { computed, onMounted, ref, watch } from 'vue'
import { useChatMode } from '@/composables/useChatMode'
import { buildCompareRoundsFromMessages } from '@/utils/compareRounds'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'
import { useChatStore } from '@/stores/chat'
import { useDebateStore } from '@/stores/debate'
import {
  clearActiveChatSession,
  writeActiveChatSession,
} from '@/stores/chatSessionStorage'
import { useModelStore } from '@/stores/models'
import { createAppLayoutNavigation } from './appLayoutNavigation'
import { buildAppLayoutModelOptions } from './appLayoutModelOptions'

const RAIL_WIDTH_EXPANDED = 280
const RAIL_WIDTH_COLLAPSED = 68
const MOBILE_BREAKPOINT = 600

export function useAppLayoutShell() {
  const appStore = useAppStore()
  const chatStore = useChatStore()
  const authStore = useAuthStore()
  const modelStore = useModelStore()
  const debateStore = useDebateStore()
  const { chatMode, multiModelIds, captainMode } = useChatMode()

  const mobileDrawerOpen = ref(false)
  const sidebarTransitionEnabled = ref(true)
  let sidebarTransitionTimer: ReturnType<typeof setTimeout> | null = null

  const realModels = computed(() => buildAppLayoutModelOptions(modelStore.models))
  const selectedModelId = computed({
    get: () => {
      if (modelStore.loaded) {
        return modelStore.selectedModelId || ''
      }
      return modelStore.selectedModelId || chatStore.selectedModel || ''
    },
    set: (value: string) => {
      if (!value) {
        chatStore.selectedModel = ''
        return
      }
      modelStore.selectModel(value)
      chatStore.selectedModel = value
    },
  })

  const isMobileLayout = computed(() => appStore.screenWidth < MOBILE_BREAKPOINT)
  const isRailExpanded = computed(() => !isMobileLayout.value && appStore.showSidebar)
  const isRailCollapsed = computed(() => !isMobileLayout.value && !appStore.showSidebar)
  const isOverlayOpen = computed(() => isMobileLayout.value && mobileDrawerOpen.value)
  const sidebarWidth = computed(() => {
    if (isMobileLayout.value) return RAIL_WIDTH_COLLAPSED
    return isRailExpanded.value ? RAIL_WIDTH_EXPANDED : RAIL_WIDTH_COLLAPSED
  })
  const sidebarStyle = computed(() => {
    if (isMobileLayout.value) return undefined
    return { width: `${sidebarWidth.value}px` }
  })
  const appShellStyle = computed(() => ({
    '--sidebar-width': `${sidebarWidth.value}px`,
    '--app-safe-top': `${appStore.safeAreaTop}px`,
    '--app-safe-bottom': `${appStore.safeAreaBottom}px`,
  }))

  const showLoginButton = computed(() => !authStore.isLoggedIn || authStore.isGuest)
  const avatarText = computed(() => {
    const name = authStore.userInfo?.nickName?.trim()
    if (name) return name.slice(0, 1).toUpperCase()
    const email = authStore.userInfo?.email?.trim()
    if (email) return email.slice(0, 1).toUpperCase()
    const phone = authStore.userInfo?.phone?.trim()
    if (phone) return phone.slice(-1).toUpperCase()
    return 'U'
  })

  const temporarilyDisableSidebarTransition = () => {
    sidebarTransitionEnabled.value = false
    if (sidebarTransitionTimer) {
      clearTimeout(sidebarTransitionTimer)
    }
    sidebarTransitionTimer = setTimeout(() => {
      sidebarTransitionEnabled.value = true
      sidebarTransitionTimer = null
    }, 260)
  }

  const closeSidebar = () => {
    if (isMobileLayout.value) {
      mobileDrawerOpen.value = false
    }
  }

  const inferCompareModelIds = () => {
    const modelIds = new Set<string>()
    for (const round of buildCompareRoundsFromMessages(chatStore.messages)) {
      for (const response of round.responses) {
        modelIds.add(response.modelId)
      }
    }
    return [...modelIds]
  }

  const snapshotDebateState = () => ({
    conversationId: debateStore.conversationId,
    selectedModelIds: [...debateStore.selectedModelIds],
    captainSelectionMode: debateStore.captainSelectionMode,
    sharedSummary: debateStore.sharedSummary,
    decisionHistory: debateStore.decisionHistory.map((record) => ({
      ...record,
      keyIssues: [...record.keyIssues],
    })),
    memoryVersion: debateStore.memoryVersion,
    completedTurns: debateStore.completedTurns,
    captainHistory: [...debateStore.captainHistory],
    currentTurnId: debateStore.currentTurnId,
    currentTurnNumber: debateStore.currentTurnNumber,
    currentStage: debateStore.currentStage,
    currentCaptainModel: debateStore.currentCaptainModel,
    captainSource: debateStore.captainSource,
    captainExplanation: debateStore.captainExplanation,
    issues: debateStore.issues.map((issue) => ({ ...issue })),
    finalAnswer: debateStore.finalAnswer,
    errorMessage: debateStore.errorMessage,
    memberStatuses: debateStore.memberStatuses.map((member) => ({ ...member })),
    turnSummaries: debateStore.turnSummaries.map((turn) => ({
      ...turn,
      issues: turn.issues.map((issue) => ({ ...issue })),
    })),
    isActive: debateStore.isActive,
    isLoading: debateStore.isLoading,
  })

  const restoreDebateState = (snapshot: ReturnType<typeof snapshotDebateState>) => {
    debateStore.conversationId = snapshot.conversationId
    debateStore.selectedModelIds = [...snapshot.selectedModelIds]
    debateStore.captainSelectionMode = snapshot.captainSelectionMode
    debateStore.sharedSummary = snapshot.sharedSummary
    debateStore.decisionHistory = snapshot.decisionHistory.map((record) => ({
      ...record,
      keyIssues: [...record.keyIssues],
    }))
    debateStore.memoryVersion = snapshot.memoryVersion
    debateStore.completedTurns = snapshot.completedTurns
    debateStore.captainHistory = [...snapshot.captainHistory]
    debateStore.currentTurnId = snapshot.currentTurnId
    debateStore.currentTurnNumber = snapshot.currentTurnNumber
    debateStore.currentStage = snapshot.currentStage
    debateStore.currentCaptainModel = snapshot.currentCaptainModel
    debateStore.captainSource = snapshot.captainSource
    debateStore.captainExplanation = snapshot.captainExplanation
    debateStore.issues = snapshot.issues.map((issue) => ({ ...issue }))
    debateStore.finalAnswer = snapshot.finalAnswer
    debateStore.errorMessage = snapshot.errorMessage
    debateStore.memberStatuses = snapshot.memberStatuses.map((member) => ({ ...member }))
    debateStore.turnSummaries = snapshot.turnSummaries.map((turn) => ({
      ...turn,
      issues: turn.issues.map((issue) => ({ ...issue })),
    }))
    debateStore.isActive = snapshot.isActive
    debateStore.isLoading = snapshot.isLoading
  }

  const setSingleConversationSession = (conversationId: string, title?: string) => {
    chatMode.value = 'single'
    multiModelIds.value = []
    captainMode.value = 'auto'
    chatStore.touchConversation(conversationId, title, {
      bumpUpdatedAt: false,
      mode: 'chat',
      compareModelIds: null,
      captainMode: null,
    })
    writeActiveChatSession({
      conversationId,
      mode: 'single',
      title,
    })
  }

  const setCompareConversationSession = (
    conversationId: string,
    modelIds: string[],
    title?: string,
  ) => {
    chatMode.value = 'compare'
    multiModelIds.value = [...modelIds]
    writeActiveChatSession({
      conversationId,
      mode: 'compare',
      multiModelIds: [...modelIds],
      title,
    })
  }

  const setTeamConversationSession = (
    conversationId: string,
    modelIds: string[],
    nextCaptainMode: 'auto' | 'fixed_first',
    title?: string,
  ) => {
    chatMode.value = 'team'
    multiModelIds.value = [...modelIds]
    captainMode.value = nextCaptainMode
    writeActiveChatSession({
      conversationId,
      mode: 'team',
      multiModelIds: [...modelIds],
      captainMode: nextCaptainMode,
      title,
    })
  }

  const {
    openChatPage,
    openHistoryPage,
    openTranslationPage,
    openResearchPage,
    openMarketPage,
    openAccountPage,
    openFeatureMenu,
    goLogin,
  } = createAppLayoutNavigation({
    isMobileLayout,
    closeSidebar,
  })

  const toggleSidebar = () => {
    if (isMobileLayout.value) {
      mobileDrawerOpen.value = !mobileDrawerOpen.value
      return
    }

    const nextExpanded = !appStore.showSidebar
    appStore.showSidebar = nextExpanded
    appStore.desktopSidebarExpanded = nextExpanded
  }

  const resetConversationDraft = (scope: 'persistent' | 'temporary' = 'persistent') => {
    debateStore.reset()
    clearActiveChatSession()
    chatStore.startConversationDraft(scope)
  }

  const newChat = () => {
    resetConversationDraft('persistent')
    openChatPage()
  }

  const startIncognitoChat = () => {
    resetConversationDraft('temporary')
    openChatPage()
  }

  const openConversationFromSidebar = async (conversationId: string) => {
    const conversation = chatStore.history.find((item) => item.id === conversationId)
    const compareModelIds = conversation?.compareModelIds?.length
      ? [...conversation.compareModelIds]
      : []

    if (conversation?.mode === 'team') {
      const previousDebateState = snapshotDebateState()
      try {
        await debateStore.recoverSession(conversationId)
      } catch {
        restoreDebateState(previousDebateState)
        uni.showToast({ title: '会话加载失败', icon: 'none' })
        return
      }
      chatStore.clearMessages()
      chatStore.currentConversationId = conversationId
      setTeamConversationSession(
        conversationId,
        compareModelIds,
        conversation.captainMode || 'auto',
        conversation.title,
      )
      chatStore.touchConversation(conversationId, conversation.title, {
        bumpUpdatedAt: false,
        mode: 'team',
        compareModelIds,
        captainMode: conversation.captainMode,
      })
      openChatPage()
      return
    }

    debateStore.reset()
    const ok = await chatStore.loadConversation(conversationId)
    if (!ok) {
      uni.showToast({ title: '会话加载失败', icon: 'none' })
      return
    }

    const restoredCompareModelIds = compareModelIds.length
      ? compareModelIds
      : inferCompareModelIds()
    if (restoredCompareModelIds.length > 1) {
      chatStore.touchConversation(conversationId, conversation?.title, {
        bumpUpdatedAt: false,
        mode: 'compare',
        compareModelIds: restoredCompareModelIds,
      })
      setCompareConversationSession(
        conversationId,
        restoredCompareModelIds,
        conversation?.title,
      )
    } else {
      setSingleConversationSession(conversationId, conversation?.title)
    }

    openChatPage()
  }

  onMounted(() => {
    chatStore.fetchHistory()
    modelStore.fetchModels()

    if (isMobileLayout.value) {
      appStore.showSidebar = false
      mobileDrawerOpen.value = false
    } else {
      appStore.showSidebar = appStore.desktopSidebarExpanded
    }
  })

  watch(
    () => appStore.screenWidth,
    (width, previousWidth) => {
      const mobileNow = width < MOBILE_BREAKPOINT
      const mobileBefore =
        typeof previousWidth === 'number' ? previousWidth < MOBILE_BREAKPOINT : mobileNow

      if (mobileNow === mobileBefore) return
      temporarilyDisableSidebarTransition()

      if (mobileNow) {
        appStore.desktopSidebarExpanded = appStore.showSidebar
        appStore.showSidebar = false
        mobileDrawerOpen.value = false
        return
      }

      appStore.showSidebar = appStore.desktopSidebarExpanded
      mobileDrawerOpen.value = false
    },
  )

  watch(
    () => [modelStore.selectedModelId, modelStore.loaded, modelStore.loadError] as const,
    ([nextModelId, modelLoaded, loadError]) => {
      if (nextModelId) {
        if (chatStore.selectedModel !== nextModelId) {
          chatStore.selectedModel = nextModelId
        }
        return
      }
      if (modelLoaded && !loadError && chatStore.selectedModel) {
        chatStore.selectedModel = ''
      }
    },
    { immediate: true },
  )

  watch(
    () => chatMode.value,
    (next, previous) => {
      if (previous === 'team' && next !== 'team') {
        debateStore.reset()
        if (!chatStore.currentConversationId) {
          clearActiveChatSession()
        }
      }
    },
  )

  return {
    chatStore,
    realModels,
    selectedModelId,
    isMobileLayout,
    isRailExpanded,
    isRailCollapsed,
    isOverlayOpen,
    sidebarTransitionEnabled,
    sidebarStyle,
    appShellStyle,
    showLoginButton,
    avatarText,
    openFeatureMenu,
    goLogin,
    newChat,
    startIncognitoChat,
    openConversationFromSidebar,
    openChatPage,
    openTranslationPage,
    openResearchPage,
    openMarketPage,
    openAccountPage,
    openHistoryPage,
    toggleSidebar,
    closeSidebar,
  }
}
