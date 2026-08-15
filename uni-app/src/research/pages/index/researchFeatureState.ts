import { extractErrorMessage } from '@/utils/errorMessage'

export type ResearchFeatureStatus = 'enabled' | 'disabled' | 'unknown'

export function inferResearchFeatureStatusFromError(error: unknown): ResearchFeatureStatus {
  const message = extractErrorMessage(error, '')
  return /research assistant is (disabled|not enabled)|literature search is not enabled|experiment execution is not enabled|paper generation is not enabled/i.test(message)
    ? 'disabled'
    : 'unknown'
}
