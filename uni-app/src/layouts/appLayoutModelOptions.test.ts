import { describe, expect, it } from 'vitest'
import { buildAppLayoutModelOptions } from './appLayoutModelOptions'

describe('buildAppLayoutModelOptions', () => {
  it('keeps model capability metadata for user-facing selectors', () => {
    const [option] = buildAppLayoutModelOptions([
      {
        id: 'gpt-5.4',
        name: 'GPT 5.4',
        avatar: 'openai',
        supportsImageParsing: true,
        supportsImageParsingSource: 'inferred',
      },
    ])

    expect(option.supportsImageParsing).toBe(true)
    expect(option.supportsImageParsingSource).toBe('inferred')
  })
})
