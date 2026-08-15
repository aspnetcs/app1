/**
 * Compare rounds utility functions.
 * Extracted to main package to avoid cross-subpackage import issues in WeChat MP.
 * Original source: chat/pages/index/compareRoundsState.ts
 */
import type { Message } from '@/stores/chat'

export interface CompareResponse {
  modelId: string
  modelName: string
  content: string
  status: 'sending' | 'success' | 'error'
}

export interface CompareRound {
  id: number
  userContent: string
  responses: CompareResponse[]
}

export function buildCompareRoundsFromMessages(messages: Message[]): CompareRound[] {
  const rounds: CompareRound[] = []
  let currentRound: CompareRound | null = null
  let roundId = 1

  for (const message of messages) {
    if (message.role === 'user') {
      currentRound = {
        id: roundId++,
        userContent: message.content,
        responses: [],
      }
      rounds.push(currentRound)
      continue
    }

    if (message.role !== 'assistant' || !currentRound) {
      continue
    }

    const modelId = message.model?.trim()
    if (!modelId) {
      continue
    }

    currentRound.responses.push({
      modelId,
      modelName: modelId,
      content: message.content,
      status: message.status === 'error' ? 'error' : 'success',
    })
  }

  return rounds.some((round) => round.responses.length > 1) ? rounds : []
}
