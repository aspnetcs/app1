import { describe, expect, it } from 'vitest'
import { parseStageOutput, parseStageOutputsFromFullExport } from './stageOutput'

describe('parseStageOutput', () => {
  it('parses serialized markdown payloads', () => {
    expect(parseStageOutput('{"format":"markdown","content":"# Title"}')).toEqual({
      format: 'markdown',
      content: '# Title',
    })
  })

  it('treats plain text as markdown content', () => {
    expect(parseStageOutput('## Outline')).toEqual({
      format: 'markdown',
      content: '## Outline',
    })
  })
})

describe('parseStageOutputsFromFullExport', () => {
  it('extracts per-stage sections from full export markdown', () => {
    const sections = parseStageOutputsFromFullExport(`
# Demo

## 全部阶段详情

### Stage 1 - topic_init

- 状态: completed

First stage body

### Stage 2 - problem_decompose

- 状态: completed

Second stage body
`)

    expect(sections.get(1)).toEqual({
      format: 'markdown',
      content: '- 状态: completed\n\nFirst stage body',
    })
    expect(sections.get(2)).toEqual({
      format: 'markdown',
      content: '- 状态: completed\n\nSecond stage body',
    })
  })
})
