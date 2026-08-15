import { http } from './http'
import { PLATFORM_TEXT_TRANSFORM_ROUTE_CONTRACT } from './platformTextTransformRouteContract'

export type PromptOptimizeDirection = 'detailed' | 'concise' | 'creative' | 'academic'

export interface PromptOptimizeRequest {
  content: string
  direction?: PromptOptimizeDirection
  model?: string
}

export interface PromptOptimizeResponse {
  optimizedPrompt: string
  direction: PromptOptimizeDirection
}

export const optimizePromptApi = (data: PromptOptimizeRequest) =>
  http.post<PromptOptimizeResponse>(PLATFORM_TEXT_TRANSFORM_ROUTE_CONTRACT.promptOptimize, data, { auth: true, silent: true })
