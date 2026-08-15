import { http } from './http'
import { PLATFORM_ONBOARDING_ROUTE_CONTRACT } from './platformOnboardingRouteContract'

export interface OnboardingConfig {
  enabled: boolean
  allowSkip: boolean
  welcomeTitle: string
  welcomeMessage: string
  steps: string[]
}

export interface OnboardingState {
  userId: string
  status: string
  currentStep?: string
  completedSteps: string[]
  resetCount: number
  lastCompletedAt?: string | null
  skippedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
  config: OnboardingConfig
  shouldShow: boolean
}

export interface OnboardingStateUpdatePayload {
  status: string
  currentStep?: string
  completedSteps?: string[]
}

export const getOnboardingState = () =>
  http.get<OnboardingState>(PLATFORM_ONBOARDING_ROUTE_CONTRACT.state, undefined, {
    auth: true,
    silent: true,
  })

export const updateOnboardingState = (payload: OnboardingStateUpdatePayload) =>
  http.put<OnboardingState>(PLATFORM_ONBOARDING_ROUTE_CONTRACT.state, payload, {
    auth: true,
    silent: true,
  })