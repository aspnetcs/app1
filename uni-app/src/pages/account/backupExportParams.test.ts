import { describe, expect, it } from 'vitest'

import { buildBackupExportQuery } from './backupExportParams'

describe('backupExportParams', () => {
  it('buildBackupExportQuery trims modules and drops empty items', () => {
    const query = buildBackupExportQuery({
      modulesText: ' conversations, ,messages ,  messageBlocks ',
      conversationLimitText: '',
      messageLimitText: '',
    })

    expect(query.modules).toBe('conversations,messages,messageBlocks')
    expect(query.conversationLimit).toBeUndefined()
    expect(query.messageLimit).toBeUndefined()
  })

  it('buildBackupExportQuery clamps optional limits', () => {
    const query = buildBackupExportQuery({
      modulesText: '',
      conversationLimitText: '2000',
      messageLimitText: '-5',
    })

    expect(query.conversationLimit).toBe(200)
    expect(query.messageLimit).toBe(1)
  })
})
