import { http } from './http'
import { PLATFORM_USER_ROUTE_CONTRACT } from './platformUserRouteContract'

export interface FollowUpSuggestionRequest {
  context: string
  model?: string
}

export interface FollowUpSuggestionResponse {
  suggestions: string[]
}

export const suggestFollowUps = (data: FollowUpSuggestionRequest) =>
  http.post<FollowUpSuggestionResponse>(PLATFORM_USER_ROUTE_CONTRACT.followUpSuggestions, data, { auth: true, silent: true })
