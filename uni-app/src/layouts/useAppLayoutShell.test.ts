import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  chatModeState: {
    chatMode: { value: 'single' as 'single' | 'compare' | 'team' },
    multiModelIds: { value: [] as string[] },
    captainMode: { value: 'auto' as 'auto' | 'fixed_first' },
  },
  chatStore: {
    selectedModel: '',
    history: [] as Array<Record<string, unknown>>,
    messages: [] as Array<Record<string, unknown>>,
    currentConversationId: null as string | null,
    startConversationDraft: vi.fn(),
    fetchHistory: vi.fn(),
    loadConversation: vi.fn(),
    touchConversation: vi.fn(),
    clearMessages: vi.fn(),
  },
  debateStore: {
    conversationId: null as string | null,
    selectedModelIds: [] as string[],
    captainSelectionMode: 'auto' as 'auto' | 'fixed_first',
    sharedSummary: null as string | null,
    decisionHistory: [] as Array<{ keyIssues: string[] } & Record<string, unknown>>,
    memoryVersion: 0,
    completedTurns: 0,
    captainHistory: [] as string[],
    currentTurnId: null as string | null,
    currentTurnNumber: 0,
    currentStage: 'IDLE' as string,
    currentCaptainModel: null as string | null,
    captainSource: null as string | null,
    captainExplanation: null as string | null,
    issues: [] as Array<Record<string, unknown>>,
    finalAnswer: null as string | null,
    errorMessage: null as string | null,
    memberStatuses: [] as Array<Record<string, unknown>>,
    turnSummaries: [] as Array<Record<string, unknown>>,
    isActive: false,
    isLoading: false,
    reset: vi.fn(),
    recoverSession: vi.fn(),
  },
  modelStore: {
    models: [],
    selectedModelId: '',
    loaded: true,
    loadError: '',
    fetchModels: vi.fn(),
    selectModel: vi.fn(),
  },
  appStore: {
    screenWidth: 1280,
    showSidebar: true,
    desktopSidebarExpanded: true,
  },
  authStore: {
    isLoggedIn: true,
    isGuest: false,
    userInfo: null,
  },
  navigation: {
    openChatPage: vi.fn(),
    openHistoryPage: vi.fn(),
    openResearchPage: vi.fn(),
    openMarketPage: vi.fn(),
    openAccountPage: vi.fn(),
    openFeatureMenu: vi.fn(),
    goLogin: vi.fn(),
  },
  clearActiveChatSession: vi.fn(),
  writeActiveChatSession: vi.fn(),
}))

vi.mock('vue', async () => {
  const actual = await vi.importActual<typeof import('vue')>('vue')
  return {
    ...actual,
    onMounted: (callback: () => void) => callback(),
    watch: vi.fn(),
  }
})

vi.mock('@/stores/chat', () => ({
  useChatStore: () => mocks.chatStore,
}))

vi.mock('@/stores/models', () => ({
  useModelStore: () => mocks.modelStore,
}))

vi.mock('@/stores/app', () => ({
  useAppStore: () => mocks.appStore,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mocks.authStore,
}))

vi.mock('@/stores/debate', () => ({
  useDebateStore: () => mocks.debateStore,
}))

vi.mock('@/composables/useChatMode', () => ({
  useChatMode: () => mocks.chatModeState,
}))

vi.mock('@/stores/chatSessionStorage', () => ({
  clearActiveChatSession: mocks.clearActiveChatSession,
  writeActiveChatSession: mocks.writeActiveChatSession,
}))

vi.mock('@/chat/pages/index/compareRoundsState', () => ({
  buildCompareRoundsFromMessages: vi.fn(() => []),
}))

vi.mock('./appLayoutNavigation', () => ({
  createAppLayoutNavigation: () => mocks.navigation,
}))

vi.mock('./appLayoutModelOptions', () => ({
  buildAppLayoutModelOptions: () => [],
}))

import { useAppLayoutShell } from './useAppLayoutShell'

describe('useAppLayoutShell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    ;(globalThis as typeof globalThis & { uni?: { showToast: ReturnType<typeof vi.fn> } }).uni = {
      showToast: vi.fn(),
    }
    mocks.chatModeState.chatMode.value = 'single'
    mocks.chatModeState.multiModelIds.value = []
    mocks.chatModeState.captainMode.value = 'auto'
    mocks.chatStore.selectedModel = ''
    mocks.chatStore.history = []
    mocks.chatStore.messages = []
    mocks.chatStore.currentConversationId = null
    mocks.chatStore.loadConversation.mockResolvedValue(true)
    mocks.chatStore.clearMessages.mockReset()
    mocks.debateStore.conversationId = null
    mocks.debateStore.selectedModelIds = []
    mocks.debateStore.captainSelectionMode = 'auto'
    mocks.debateStore.sharedSummary = null
    mocks.debateStore.decisionHistory = []
    mocks.debateStore.memoryVersion = 0
    mocks.debateStore.completedTurns = 0
    mocks.debateStore.captainHistory = []
    mocks.debateStore.currentTurnId = null
    mocks.debateStore.currentTurnNumber = 0
    mocks.debateStore.currentStage = 'IDLE'
    mocks.debateStore.currentCaptainModel = null
    mocks.debateStore.captainSource = null
    mocks.debateStore.captainExplanation = null
    mocks.debateStore.issues = []
    mocks.debateStore.finalAnswer = null
    mocks.debateStore.errorMessage = null
    mocks.debateStore.memberStatuses = []
    mocks.debateStore.turnSummaries = []
    mocks.debateStore.isActive = false
    mocks.debateStore.isLoading = false
    mocks.appStore.screenWidth = 1280
    mocks.appStore.showSidebar = true
    mocks.appStore.desktopSidebarExpanded = true
    mocks.modelStore.selectedModelId = ''
    mocks.modelStore.loaded = true
    mocks.modelStore.loadError = ''
  })

  it('routes incognito entry into a temporary chat draft before opening chat page', () => {
    const shell = useAppLayoutShell()

    shell.startIncognitoChat()

    expect(mocks.debateStore.reset).toHaveBeenCalledTimes(1)
    expect(mocks.clearActiveChatSession).toHaveBeenCalledTimes(1)
    expect(mocks.chatStore.startConversationDraft).toHaveBeenCalledWith('temporary')
    expect(mocks.navigation.openChatPage).toHaveBeenCalledTimes(1)
  })

  it('restores persistent draft mode for a normal new chat', () => {
    const shell = useAppLayoutShell()

    shell.newChat()

    expect(mocks.debateStore.reset).toHaveBeenCalledTimes(1)
    expect(mocks.clearActiveChatSession).toHaveBeenCalledTimes(1)
    expect(mocks.chatStore.startConversationDraft).toHaveBeenCalledWith('persistent')
    expect(mocks.navigation.openChatPage).toHaveBeenCalledTimes(1)
  })

  it('restores compare mode when reopening a compare conversation from sidebar', async () => {
    mocks.chatStore.history = [
      {
        id: 'compare-1',
        title: 'Compare',
        mode: 'compare',
        compareModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      },
    ]

    const shell = useAppLayoutShell()
    await shell.openConversationFromSidebar('compare-1')

    expect(mocks.chatStore.loadConversation).toHaveBeenCalledWith('compare-1')
    expect(mocks.chatModeState.chatMode.value).toBe('compare')
    expect(mocks.chatModeState.multiModelIds.value).toEqual(['gpt-4o', 'claude-3-7-sonnet'])
    expect(mocks.writeActiveChatSession).toHaveBeenCalledWith({
      conversationId: 'compare-1',
      mode: 'compare',
      multiModelIds: ['gpt-4o', 'claude-3-7-sonnet'],
      title: 'Compare',
    })
    expect(mocks.navigation.openChatPage).toHaveBeenCalledTimes(1)
  })

  it('recovers a team session instead of loading it as a normal conversation', async () => {
    mocks.chatStore.history = [
      {
        id: 'team-1',
        title: 'Team',
        mode: 'team',
        compareModelIds: ['model-a', 'model-b'],
        captainMode: 'fixed_first',
      },
    ]
    mocks.debateStore.recoverSession.mockResolvedValue(undefined)

    const shell = useAppLayoutShell()
    await shell.openConversationFromSidebar('team-1')

    expect(mocks.chatStore.loadConversation).not.toHaveBeenCalled()
    expect(mocks.debateStore.reset).not.toHaveBeenCalled()
    expect(mocks.chatStore.clearMessages).toHaveBeenCalledTimes(1)
    expect(mocks.debateStore.recoverSession).toHaveBeenCalledWith('team-1')
    expect(mocks.chatStore.currentConversationId).toBe('team-1')
    expect(mocks.chatModeState.chatMode.value).toBe('team')
    expect(mocks.chatModeState.multiModelIds.value).toEqual(['model-a', 'model-b'])
    expect(mocks.chatModeState.captainMode.value).toBe('fixed_first')
    expect(mocks.writeActiveChatSession).toHaveBeenCalledWith({
      conversationId: 'team-1',
      mode: 'team',
      multiModelIds: ['model-a', 'model-b'],
      captainMode: 'fixed_first',
      title: 'Team',
    })
    expect(mocks.navigation.openChatPage).toHaveBeenCalledTimes(1)
  })

  it('restores the previous debate state when team recovery fails', async () => {
    mocks.chatModeState.chatMode.value = 'single'
    mocks.chatStore.currentConversationId = 'single-1'
    mocks.chatStore.messages = [{ id: 'msg-1' }]
    mocks.chatStore.history = [
      {
        id: 'team-1',
        title: 'Team',
        mode: 'team',
        compareModelIds: ['model-a', 'model-b'],
      },
    ]
    mocks.debateStore.conversationId = 'existing-team'
    mocks.debateStore.selectedModelIds = ['existing-model']
    mocks.debateStore.currentStage = 'COMPLETED'
    mocks.debateStore.isActive = true
    mocks.debateStore.recoverSession.mockRejectedValue(new Error('recover failed'))

    const shell = useAppLayoutShell()
    await shell.openConversationFromSidebar('team-1')

    expect(mocks.chatStore.clearMessages).not.toHaveBeenCalled()
    expect(mocks.chatStore.currentConversationId).toBe('single-1')
    expect(mocks.chatModeState.chatMode.value).toBe('single')
    expect(mocks.debateStore.conversationId).toBe('existing-team')
    expect(mocks.debateStore.selectedModelIds).toEqual(['existing-model'])
    expect(mocks.debateStore.currentStage).toBe('COMPLETED')
    expect(mocks.debateStore.isActive).toBe(true)
    expect(mocks.navigation.openChatPage).not.toHaveBeenCalled()
    expect((globalThis as typeof globalThis & { uni: { showToast: ReturnType<typeof vi.fn> } }).uni.showToast).toHaveBeenCalled()
  })

  it('clears stale compare metadata when reopening a normal single conversation', async () => {
    mocks.chatModeState.chatMode.value = 'compare'
    mocks.chatModeState.multiModelIds.value = ['gpt-4o', 'claude-3-7-sonnet']
    mocks.chatModeState.captainMode.value = 'fixed_first'
    mocks.chatStore.history = [
      {
        id: 'single-1',
        title: 'Single',
      },
    ]

    const shell = useAppLayoutShell()
    await shell.openConversationFromSidebar('single-1')

    expect(mocks.chatModeState.chatMode.value).toBe('single')
    expect(mocks.chatModeState.multiModelIds.value).toEqual([])
    expect(mocks.chatModeState.captainMode.value).toBe('auto')
    expect(mocks.chatStore.touchConversation).toHaveBeenCalledWith('single-1', 'Single', {
      bumpUpdatedAt: false,
      mode: 'chat',
      compareModelIds: null,
      captainMode: null,
    })
    expect(mocks.writeActiveChatSession).toHaveBeenCalledWith({
      conversationId: 'single-1',
      mode: 'single',
      title: 'Single',
    })
  })

  it('preserves a collapsed desktop sidebar across page remounts', () => {
    const firstShell = useAppLayoutShell()

    firstShell.toggleSidebar()

    expect(mocks.appStore.showSidebar).toBe(false)
    expect(mocks.appStore.desktopSidebarExpanded).toBe(false)

    useAppLayoutShell()

    expect(mocks.appStore.showSidebar).toBe(false)
    expect(mocks.appStore.desktopSidebarExpanded).toBe(false)
  })
})
