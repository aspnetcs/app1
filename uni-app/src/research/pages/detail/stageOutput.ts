export type ParsedStageOutput = {
  format: 'markdown' | 'text'
  content: string
}

export const parseStageOutput = (raw: string | null | undefined): ParsedStageOutput | null => {
  const normalized = raw?.trim()
  if (!normalized) return null

  try {
    const parsed = JSON.parse(normalized) as { format?: unknown; content?: unknown }
    const content = typeof parsed.content === 'string' ? parsed.content.trim() : ''
    if (!content) return null
    return {
      format: parsed.format === 'text' ? 'text' : 'markdown',
      content,
    }
  } catch {
    return {
      format: 'markdown',
      content: normalized,
    }
  }
}

export const parseStageOutputsFromFullExport = (markdown: string | null | undefined) => {
  const sections = new Map<number, ParsedStageOutput>()
  const normalized = markdown?.trim()
  if (!normalized) return sections

  const headings = [...normalized.matchAll(/^### Stage (\d+)\s+-\s+.+$/gm)]
  for (const [index, match] of headings.entries()) {
    const stageNumber = Number.parseInt(match[1] ?? '', 10)
    const start = (match.index ?? 0) + match[0].length
    const end = headings[index + 1]?.index ?? normalized.length
    const body = normalized.slice(start, end).trim()
    if (!Number.isFinite(stageNumber) || !body) continue
    sections.set(stageNumber, {
      format: 'markdown',
      content: body,
    })
  }

  return sections
}
