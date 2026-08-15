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

type ChatRequestMessage = {
  role: 'user' | 'assistant'
  content: string
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

export function buildComparePayloadForModel(
  rounds: CompareRound[],
  modelId: string,
  currentRoundId: number,
  nextUserContent: string,
): ChatRequestMessage[] {
  const payload: ChatRequestMessage[] = []

  for (const round of rounds) {
    if (round.id === currentRoundId) {
      break
    }

    payload.push({ role: 'user', content: round.userContent })
    const previousResponse = round.responses.find((response) => response.modelId === modelId)
    if (previousResponse?.content.trim()) {
      payload.push({ role: 'assistant', content: previousResponse.content })
    }
  }

  payload.push({ role: 'user', content: nextUserContent })
  return payload
}

export function buildCompareRoundMessages(
  userMessage: Message,
  round: CompareRound,
): Message[] {
  return [
    userMessage,
    ...round.responses.map((response, index) => ({
      id: `${round.id}-${response.modelId}`,
      role: 'assistant' as const,
      content: response.content,
      createdAt: userMessage.createdAt + index + 1,
      model: response.modelId,
      status: (response.status === 'error' ? 'error' : 'success') as Message['status'],
    })),
  ]
}
