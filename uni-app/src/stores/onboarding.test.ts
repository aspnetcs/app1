import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useOnboardingStore } from './onboarding'
import { getOnboardingState, updateOnboardingState } from '@/api/onboarding'

vi.mock('@/api/onboarding', () => ({
  getOnboardingState: vi.fn(),
  updateOnboardingState: vi.fn(),
}))

describe('onboarding store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(getOnboardingState).mockResolvedValue({
      data: {
        userId: 'user-1',
        status: 'in_progress',
        currentStep: 'history',
        completedSteps: ['welcome'],
        resetCount: 0,
        config: {
          enabled: true,
          allowSkip: true,
          welcomeTitle: '欢迎使用 AI App',
          welcomeMessage: '通过简短引导了解历史、助手市场和常用入口。',
          steps: ['welcome', 'history', 'market', 'settings'],
        },
        shouldShow: true,
      },
    } as never)
    vi.mocked(updateOnboardingState).mockResolvedValue({
      data: {
        userId: 'user-1',
        status: 'completed',
        currentStep: 'settings',
        completedSteps: ['welcome', 'history', 'market', 'settings'],
        resetCount: 0,
        config: {
          enabled: true,
          allowSkip: true,
          welcomeTitle: '欢迎使用 AI App',
          welcomeMessage: '通过简短引导了解历史、助手市场和常用入口。',
          steps: ['welcome', 'history', 'market', 'settings'],
        },
        shouldShow: false,
      },
    } as never)
  })

  it('resolves onboarding page while state requires onboarding', async () => {
    const store = useOnboardingStore()
    await expect(store.resolveEntryUrl()).resolves.toBe('/pages/onboarding/onboarding')
    expect(store.needsOnboarding).toBe(true)
  })

  it('marks onboarding complete', async () => {
    const store = useOnboardingStore()
    await store.fetchState(true)
    await store.completeOnboarding()

    expect(updateOnboardingState).toHaveBeenCalledWith({
      status: 'completed',
      currentStep: 'settings',
      completedSteps: ['welcome', 'history', 'market', 'settings'],
    })
    expect(store.state?.status).toBe('completed')
  })
})