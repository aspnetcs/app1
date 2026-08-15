import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getOnboardingState, updateOnboardingState, type OnboardingState } from '@/api/onboarding'

const CHAT_ENTRY_URL = '/chat/pages/index/index'
const ONBOARDING_ENTRY_URL = '/pages/onboarding/onboarding'

export const useOnboardingStore = defineStore('onboarding', () => {
  const state = ref<OnboardingState | null>(null)
  const loading = ref(false)

  const needsOnboarding = computed(() => Boolean(state.value?.shouldShow))
  const steps = computed(() => state.value?.config.steps || ['welcome', 'history', 'market', 'settings'])

  async function fetchState(force = false) {
    if (state.value && !force) {
      return state.value
    }
    loading.value = true
    try {
      const response = await getOnboardingState()
      state.value = response.data
      return state.value
    } finally {
      loading.value = false
    }
  }

  async function resolveEntryUrl() {
    try {
      const next = await fetchState(true)
      return next?.shouldShow ? ONBOARDING_ENTRY_URL : CHAT_ENTRY_URL
    } catch {
      return CHAT_ENTRY_URL
    }
  }

  async function updateState(payload: {
    status: string
    currentStep?: string
    completedSteps?: string[]
  }) {
    const response = await updateOnboardingState(payload)
    state.value = response.data
    return state.value
  }

  async function completeOnboarding() {
    const currentSteps = steps.value
    const lastStep = currentSteps[currentSteps.length - 1] || 'settings'
    return updateState({
      status: 'completed',
      currentStep: lastStep,
      completedSteps: [...currentSteps],
    })
  }

  async function skipOnboarding() {
    return updateState({
      status: 'skipped',
      currentStep: state.value?.currentStep || steps.value[0],
      completedSteps: state.value?.completedSteps || [],
    })
  }

  function clear() {
    state.value = null
    loading.value = false
  }

  return {
    state,
    loading,
    needsOnboarding,
    steps,
    fetchState,
    resolveEntryUrl,
    updateState,
    completeOnboarding,
    skipOnboarding,
    clear,
  }
})