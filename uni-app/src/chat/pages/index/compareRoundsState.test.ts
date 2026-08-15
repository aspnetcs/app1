import { describe, expect, it } from 'vitest'
import type { Message } from '@/stores/chat'
import {
  buildCompareRoundMessages,
  buildComparePayloadForModel,
  buildCompareRoundsFromMessages,
  type CompareRound,
} from './compareRoundsState'

function message(overrides: Partial<Message>): Message {
  return {
    id: overrides.id ?? 'msg-1',
    role: overrides.role ?? 'user',
    content: overrides.content ?? '',
    createdAt: overrides.createdAt ?? Date.now(),
    model: overrides.model,
    status: overrides.status,
  }
}

describe('compareRoundsState', () => {
  it('rebuilds compare rounds only when a round has multiple assistant replies', () => {
    const rounds = buildCompareRoundsFromMessages([
      message({ id: 'u1', role: 'user', content: 'Question 1' }),
      message({ id: 'a1', role: 'assistant', model: 'model-a', content: 'Answer A1' }),
      message({ id: 'a2', role: 'assistant', model: 'model-b', content: 'Answer B1' }),
      message({ id: 'u2', role: 'user', content: 'Question 2' }),
      message({ id: 'a3', role: 'assistant', model: 'model-a', content: 'Answer A2' }),
      message({ id: 'a4', role: 'assistant', model: 'model-b', content: 'Answer B2' }),
    ])

    expect(rounds).toEqual<CompareRound[]>([
      {
        id: 1,
        userContent: 'Question 1',
        responses: [
          { modelId: 'model-a', modelName: 'model-a', content: 'Answer A1', status: 'success' },
          { modelId: 'model-b', modelName: 'model-b', content: 'Answer B1', status: 'success' },
        ],
      },
      {
        id: 2,
        userContent: 'Question 2',
        responses: [
          { modelId: 'model-a', modelName: 'model-a', content: 'Answer A2', status: 'success' },
          { modelId: 'model-b', modelName: 'model-b', content: 'Answer B2', status: 'success' },
        ],
      },
    ])
  })

  it('keeps each model on its own prior answer thread', () => {
    const rounds: CompareRound[] = [
      {
        id: 1,
        userContent: 'Question 1',
        responses: [
          { modelId: 'model-a', modelName: 'model-a', content: 'Answer A1', status: 'success' },
          { modelId: 'model-b', modelName: 'model-b', content: 'Answer B1', status: 'success' },
        ],
      },
      {
        id: 2,
        userContent: 'Question 2',
        responses: [],
      },
    ]

    expect(buildComparePayloadForModel(rounds, 'model-b', 2, 'Question 2')).toEqual([
      { role: 'user', content: 'Question 1' },
      { role: 'assistant', content: 'Answer B1' },
      { role: 'user', content: 'Question 2' },
    ])
  })

  it('builds local compare conversation messages in stable model order', () => {
    const userMessage = message({
      id: 'u1',
      role: 'user',
      content: 'Question 1',
      createdAt: 100,
      model: 'model-a',
      status: 'success',
    })

    const round: CompareRound = {
      id: 1,
      userContent: 'Question 1',
      responses: [
        { modelId: 'model-a', modelName: 'model-a', content: 'Answer A1', status: 'success' },
        { modelId: 'model-b', modelName: 'model-b', content: '', status: 'error' },
      ],
    }

    expect(buildCompareRoundMessages(userMessage, round)).toEqual([
      userMessage,
      message({
        id: '1-model-a',
        role: 'assistant',
        content: 'Answer A1',
        createdAt: 101,
        model: 'model-a',
        status: 'success',
      }),
      message({
        id: '1-model-b',
        role: 'assistant',
        content: '',
        createdAt: 102,
        model: 'model-b',
        status: 'error',
      }),
    ])
  })
})
