export type BackupExportFormState = {
  modulesText: string
  conversationLimitText: string
  messageLimitText: string
}

export type BackupExportQuery = {
  modules?: string
  conversationLimit?: number
  messageLimit?: number
}

const MAX_CONVERSATION_LIMIT = 200
const MAX_MESSAGE_LIMIT = 2000

function parseOptionalInt(text: string, min: number, max: number): number | undefined {
  const trimmed = (text || '').trim()
  if (!trimmed) return undefined
  const value = Number.parseInt(trimmed, 10)
  if (!Number.isFinite(value)) return undefined
  const normalized = Math.min(Math.max(value, min), max)
  return normalized
}

export function buildBackupExportQuery(form: BackupExportFormState): BackupExportQuery {
  const modules = (form.modulesText || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .join(',')

  return {
    modules: modules || undefined,
    conversationLimit: parseOptionalInt(form.conversationLimitText, 1, MAX_CONVERSATION_LIMIT),
    messageLimit: parseOptionalInt(form.messageLimitText, 1, MAX_MESSAGE_LIMIT),
  }
}
